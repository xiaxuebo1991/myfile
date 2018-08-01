/******************************************************************************
 * @file UimRemoteClientService.java
 * <p>
 * ---------------------------------------------------------------------------
 * Copyright (c) 2014,2015 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 * ---------------------------------------------------------------------------
 ******************************************************************************/

package com.ucloudlink.framework.remoteuim;

import android.app.Service;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import com.ucloudlink.framework.util.ByteUtils;
import com.ucloudlink.refact.channel.enabler.datas.RemoteUimEvent;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import okio.ByteString;

import static com.ucloudlink.framework.remoteuim.UimRemoteEventReq.ErrorCause;
import static com.ucloudlink.framework.remoteuim.UimRemoteEventReq.Event;
import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;

public class UimRemoteClientService extends Service {
    private final String TAG = "UimRemoteClientService";

    public static final int EVENT_REQ  = 1;
    public static final int EVENT_RESP = 2;

    public static class UimRemoteError {

        public static final int UIM_REMOTE_SUCCESS = 0;

        public static final int UIM_REMOTE_ERROR = 1;
    }

    private int mToken = 0;

    private int                     simSlots = 2;
    private UimRemoteClientSocket[] mSocket  = new UimRemoteClientSocket[simSlots];
    private HandlerThread[]         mThreads = new HandlerThread[2];

    IUimRemoteClientServiceCallback mCb = null;

    private static class Application {
        public String  name;
        public String  key;
        public boolean parsingFail;
    }

    private Handler mRespHdlr;

    class ApduHandler extends Handler {

        public ApduHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            logv("handleMessage()");
            if (mCb == null) {
                loge("handleMessage() - null mCb");
                return;
            }
            try {
                byte[] bytes = (byte[]) msg.obj;
                //                byte[] bytes=new byte[]{8,3,16,2,42,41,10,39,0,-120,0,-127,34,16,-24,-28,-33,94,50,58,77,-42,119,-21,-18,-128,-67,38,-68,100,16,-5,-20,-115,104,-88,-62,114,76,74,88,-109,-121,31,-52,-15,-40};//测试
                logv("getMsg from UIM bytes: " + ByteUtils.bytesToHex(bytes));
                int slotId = msg.arg1;
                MessageTag tag = MessageTag.ADAPTER.decode(bytes);
                logv("handleMessage() - token: " + tag.token + ", type: " + tag.type + ", Id: " + tag.id + ", error: " + tag.error + ", slot id: " + slotId);
                if (tag.type == MessageType.UIM_REMOTE_MSG_RESPONSE) {
                    switch (tag.id) {
                        case UIM_REMOTE_EVENT:
                            UimRemoteEventResp event_rsp;
                            event_rsp = UimRemoteEventResp.ADAPTER.decode(tag.payload.toByteArray());
                            mCb.uimRemoteEventResponse(slotId, event_rsp.response.getValue());
                            break;
                        case UIM_REMOTE_APDU:
                            UimRemoteApduResp adpu_rsp;
                            adpu_rsp = UimRemoteApduResp.ADAPTER.decode(tag.payload.toByteArray());
                            mCb.uimRemoteApduResponse(slotId, adpu_rsp.status.getValue());
                            break;
                        default:
                            loge("unexpected msg id");
                    }
                } else if (tag.type == MessageType.UIM_REMOTE_MSG_INDICATION) {
                    switch (tag.id) {
                        case UIM_REMOTE_APDU:
                            UimRemoteApduInd adpu_ind;
                            adpu_ind = UimRemoteApduInd.ADAPTER.decode(tag.payload.toByteArray());
                            byte[] apduCmd = adpu_ind.apduCommand.toByteArray();
                            logv("handleMessage: apduCmd:" + apduCmd);
                            mCb.uimRemoteApduIndication(slotId, apduCmd);
                            break;
                        case UIM_REMOTE_CONNECT:
                            mCb.uimRemoteConnectIndication(slotId);
                            break;
                        case UIM_REMOTE_DISCONNECT:
                            mCb.uimRemoteDisconnectIndication(slotId);
                            break;
                        case UIM_REMOTE_POWER_UP:
                            mCb.uimRemotePowerUpIndication(slotId);
                            break;
                        case UIM_REMOTE_POWER_DOWN:
                            mCb.uimRemotePowerDownIndication(slotId);
                            break;
                        case UIM_REMOTE_RESET:
                            mCb.uimRemoteResetIndication(slotId);
                            break;
                        default:
                            loge("unexpected msg id");
                    }
                } else {
                    loge("unexpected msg type");
                }
            } catch (Exception e) {
                loge("error occured when parsing the resp/ind");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logi("onCreate()");
        logi("simCount: " + simSlots);
        if (mRespHdlr == null) {
            HandlerThread apduThread = new HandlerThread("apduHandler");
            apduThread.start();
            mRespHdlr = new ApduHandler(apduThread.getLooper());
        }
        //        for (int i = 0; i < simSlots; i++) {
        //            createSocket(i);
        //        }
        //initing whitelist
        //getWhiteList();
    }

    private void createSocket(int i) {
        if (mSocket[i] == null) {
            mSocket[i] = new UimRemoteClientSocket(mRespHdlr, i, getApduThreadLooper(i));
            new Thread(mSocket[i]).start();
        }
    }

    private Looper getApduThreadLooper(int i) {
        if (i >= 2) {
            throw new IllegalStateException("getApduThreadLooper i should less than 2");
        }
        HandlerThread thread = mThreads[i];
        if (thread == null ||!thread.isAlive()) {
            thread = new HandlerThread("apduHandler" + i);
            thread.start();
            mThreads[i] = thread;
        }
        return thread.getLooper();
    }

    @Override
    public IBinder onBind(Intent intent) {
        logi("onBind()");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        logi("onDestroy()");
        for (int i = 0; i < simSlots; i++) {
            mSocket[i].toDestroy();
//            mSocket[i].getLooper().quitSafely();
            mSocket[i] = null;
        }
        stopSelf();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        logi("onLowMemory()");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        logi("onTrimMemory level: " + level);
    }

    private final IUimRemoteClientService.Stub mBinder = new IUimRemoteClientService.Stub() {
        public int registerCallback(IUimRemoteClientServiceCallback cb) {
            if (!verifyAuthenticity(mBinder.getCallingUid())) {
                logd("Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            UimRemoteClientService.this.mCb = cb;
            if (cb == null) {
                logd("registerCallback() - null cb");
            }
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int deregisterCallback(IUimRemoteClientServiceCallback cb) {
            if (!verifyAuthenticity(mBinder.getCallingUid())) {
                loge("Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            UimRemoteClientService.this.mCb = null;
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }

        public int uimRemoteEvent(int slot, int event, byte[] atr, int errCode, int tranSport, int usage, int apdu_timeout, int disable_all_polling, int poll_timer) {
            synchronized (this) {
                if (event == RemoteUimEvent.UIM_REMOTE_CONNECT_SOCKET) {
                    createSocket(slot);
                    return UimRemoteError.UIM_REMOTE_SUCCESS;
                } else if (event == RemoteUimEvent.UIM_REMOTE_DISCONNECT_SOCKET) {
                    disconnectSocket(slot);
                    return UimRemoteError.UIM_REMOTE_SUCCESS;
                }
                logv("uimRemoteEvent atr len:" + atr.length);
                if (!verifyAuthenticity(mBinder.getCallingUid())) {
                    loge("Cannot perform! returning failure");
                    return UimRemoteError.UIM_REMOTE_ERROR;
                }
                if (slot >= simSlots) {
                    loge("Sim Slot not supported!" + slot);
                    return UimRemoteError.UIM_REMOTE_ERROR;
                }
                if (mSocket[slot] == null) {
                    loge("socket is not connected");
                    return UimRemoteError.UIM_REMOTE_ERROR;
                }
                logv("uimRemoteEvent() - slot: " + slot + "; event: " + event);
                // get tag;
                //            UimRemoteClient.MessageTag tag = new UimRemoteClient.MessageTag();
                //            tag.setToken(mToken++);
                //            tag.setType(UimRemoteClient.UIM_REMOTE_MSG_REQUEST);
                //            tag.setId(UimRemoteClient.UIM_REMOTE_EVENT);
                //            tag.setError(UimRemoteClient.UIM_REMOTE_ERR_SUCCESS);
                MessageTag.Builder tag = new MessageTag.Builder();
                tag.token(mToken++).type(MessageType.UIM_REMOTE_MSG_REQUEST).id(MessageId.UIM_REMOTE_EVENT).error(Error.UIM_REMOTE_ERR_SUCCESS);
                // get request
                UimRemoteEventReq.Builder req = new UimRemoteEventReq.Builder();
                req.event(Event.fromValue(event));
                req.atr(ByteString.of(atr));
                req.error_code(ErrorCause.fromValue(errCode));
                req.transport(UimRemoteEventReq.Transport.fromValue(tranSport));
                req.usage(UimRemoteEventReq.Usage.fromValue(usage));
                req.apdu_timeout(apdu_timeout);
                //req.setDisableAllPolling(disable_all_polling);
                req.disable_all_polling(disable_all_polling);
                req.poll_timer(poll_timer);
                tag.payload(ByteString.of(UimRemoteClientMsgPacking.packMsg(MessageId.UIM_REMOTE_EVENT, MessageType.UIM_REMOTE_MSG_REQUEST, req.build())));
                byte[] bytes = UimRemoteClientMsgPacking.packTag(tag.build());
                logv("!!uimRemoteEvent: bytes" + ByteUtils.bytesToHex(bytes));
                Message msg = mSocket[slot].obtainMessage(EVENT_REQ, bytes);// wldebug
                msg.sendToTarget();
                return UimRemoteError.UIM_REMOTE_SUCCESS;
            }
        }

        public int uimRemoteApdu(int slot, int apduStatus, byte[] apduResp) {
            if (!verifyAuthenticity(mBinder.getCallingUid())) {
                loge("Cannot perform! returning failure");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (slot >= simSlots) {
                loge("Sim Slot not supported!" + slot);
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            if (mSocket[slot] == null) {
                loge("socket is not connected");
                return UimRemoteError.UIM_REMOTE_ERROR;
            }
            logv("uimRemoteApdu() - slot: " + slot + "; adpuStatus: " + apduStatus);
            logv("uimRemoteApdu: apduResp:" + bytesToHex(apduResp));
            // get tag;
            MessageTag.Builder tag = new MessageTag.Builder();
            tag.token(mToken++);
            tag.type(MessageType.UIM_REMOTE_MSG_REQUEST);
            tag.id(MessageId.UIM_REMOTE_APDU);
            tag.error(Error.UIM_REMOTE_ERR_SUCCESS);
            // get request
            //UimRemoteClient.UimRemoteApduReq req = new UimRemoteClient.UimRemoteApduReq();
            UimRemoteApduReq.Builder req = new UimRemoteApduReq.Builder();
            //            UimRemoteApduReq.Builder req = new UimRemoteApduReq(UimRemoteApduReq.ApduStatus.fromValue(apduStatus),ByteString.of(apduResp)).newBuilder();
            //req.setStatus(apduStatus);
            req.status(UimRemoteApduReq.ApduStatus.fromValue(apduStatus));
            req.apduResponse(ByteString.of(apduResp));
            tag.payload(ByteString.of(UimRemoteClientMsgPacking.packMsg(MessageId.UIM_REMOTE_APDU, MessageType.UIM_REMOTE_MSG_REQUEST, req.build())));// wlmodify
            byte[] bytes = UimRemoteClientMsgPacking.packTag(tag.build());
            logv("!!uimRemoteApdu: bytes hex" + ByteUtils.bytesToHex(bytes));
            Message msg = mSocket[slot].obtainMessage(EVENT_REQ, bytes);
            msg.sendToTarget();
            return UimRemoteError.UIM_REMOTE_SUCCESS;
        }
    };

    private void disconnectSocket(final int slot) {
        if (mSocket[slot] != null) {
            mSocket[slot].postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSocket[slot].toDestroy();
//                    mSocket[slot].getLooper().quitSafely();
                    mSocket[slot] = null;
                }
            }, 200);
        }
    }

    private Application readApplication(XmlResourceParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Application");
        Application app = new Application();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG) {
            if (eventType != XmlPullParser.START_TAG) {
                app.parsingFail = true;
                loge("parse fail");
                break;
            }
            String tagName = parser.getName();
            if (tagName.equals("PackageName")) {
                eventType = parser.next();
                if (eventType == XmlPullParser.TEXT) {
                    app.name = parser.getText();
                    eventType = parser.next();
                }
                if ((eventType != XmlPullParser.END_TAG) || !(parser.getName().equals("PackageName"))) {
                    //Invalid tag or invalid xml format
                    app.parsingFail = true;
                    loge("parse fail");
                    break;
                }
            } else if (tagName.equals("SignatureHash")) {
                eventType = parser.next();
                if (eventType == XmlPullParser.TEXT) {
                    app.key = parser.getText();
                    eventType = parser.next();
                }
                if ((eventType != XmlPullParser.END_TAG) || !(parser.getName().equals("SignatureHash"))) {
                    //Invalid tag or invalid xml format
                    app.parsingFail = true;
                    loge("parse fail");
                    break;
                }
            } else {
                app.parsingFail = true;
                loge("parse fail" + tagName);
                break;
            }
            eventType = parser.next();
        }
        if ((eventType != XmlPullParser.END_TAG) || !(parser.getName().equals("Application"))) {
            //End Tag that ended the loop is not Application
            app.parsingFail = true;
        }
        return app;
    }

    private static String bytesToHex(byte[] inputBytes) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : inputBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void logv(String msg) {

    }

    private boolean verifyAuthenticity(int uid) {
        return true;
        //        boolean ret = false;
        //
        //        if(UimRemoteClientWhiteList == null) {
        //            return ret;
        //        }
        //        String[] packageNames = context.getPackageManager().getPackagesForUid(uid);
        //        for(String packageName : packageNames){
        //            if(UimRemoteClientWhiteList.containsKey(packageName)){
        //                String hash = (String)UimRemoteClientWhiteList.get(packageName);
        //                String compareHash = new String();
        //                try {
        //                    Signature[] sigs = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures;
        //                    for(Signature sig: sigs) {
        //
        //                        //get the raw certificate into input stream
        //                        final byte[] rawCert = sig.toByteArray();
        //                        InputStream certStream = new ByteArrayInputStream(rawCert);
        //
        //                        //Read the X.509 Certificate into certBytes
        //                        final CertificateFactory certFactory = CertificateFactory.getInstance("X509");
        //                        final X509Certificate x509Cert = (X509Certificate)certFactory.generateCertificate(certStream);
        //                        byte[] certBytes = x509Cert.getEncoded();
        //
        //                        //get the fixed SHA-1 cert
        //                        MessageDigest md = MessageDigest.getInstance("SHA-1");
        //	                    md.update(certBytes);
        //	                    byte[] certThumbprint = md.digest();
        //
        //                        //cert in hex format
        //                        compareHash = bytesToHex(certThumbprint);
        //
        //                        if(hash.equals(compareHash)) {
        //                            ret = true;
        //                            break;
        //                        }
        //                    }
        //                }
        //                catch(Exception e) {
        //                    loge( "Exception reading client data!" + e);
        //                }
        //            }
        //        }
        //        return ret;
    }
}

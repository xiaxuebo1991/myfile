/******************************************************************************
 * @file UcloudServiceClientService.java
 * <p>
 * ---------------------------------------------------------------------------
 * Copyright (c) 2014,2015 ucloudlink Technologies, Inc.  All Rights Reserved.
 * ucloudlink Technologies Proprietary and Confidential.
 * ---------------------------------------------------------------------------
 ******************************************************************************/

package com.ucloudlink.framework.ucloudsocket;

import android.app.Service;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

//import com.ucloudlink.framework.ucloudsocket.*;
import com.ucloudlink.framework.remoteuim.UimRemoteEventResp;
import com.ucloudlink.framework.util.ByteUtils;
import com.ucloudlink.refact.card.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import okio.ByteString;

import com.ucloudlink.refact.utils.JLog;
//import static com.ucloudlink.framework.ucloudsocket.UcloudServiceEventReq.ErrorCause;
//import static com.ucloudlink.framework.ucloudsocket.UcloudServiceEventReq.Event;
import com.ucloudlink.refact.network.*;
/*实现UcloudServiceClientService*/
public class UcloudServiceClientService extends Service {
    private final String TAG = "UcloudServiceClientService";
    /*定义handler参数*/
    public static final int EVENT_REQ  = 1;
    public static final int EVENT_RESP = 2;
    /*定义错误值*/
    public static class UcloudServiceError {

        public static final int UCLOUD_SUCCESS = 0;
        public static final int UCLOUD_ERROR = 1;
    }
    /*定义socket token*/
    private int mToken = 0;
    /*定义slot id和socket，以及回调函数*/
    private int                     simSlots = 2;
    private UcloudServiceClientSocket[] mUcloudSocket  = new UcloudServiceClientSocket[simSlots];
    IUcloudServiceClientServiceCallback mCb = null;

    private static class Application {
        public String  name;
        public String  key;
        public boolean parsingFail;
    }
    /*定义接收消息handler*/
    private Handler mRespHdlr;
    /*接收消息handler实现*/
    class RecvHandler extends Handler {

        public RecvHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            JLog.logv("handleMessage()");
            if (mCb == null) {
                JLog.loge("handleMessage() - null mCb");
                return;
            }
            try {
                byte[] bytes = (byte[]) msg.obj;
                //                byte[] bytes=new byte[]{8,3,16,2,42,41,10,39,0,-120,0,-127,34,16,-24,-28,-33,94,50,58,77,-42,119,-21,-18,-128,-67,38,-68,100,16,-5,-20,-115,104,-88,-62,114,76,74,88,-109,-121,31,-52,-15,-40};//测试
                JLog.logv("ucloudlink getMsg from ucloud bytes: " + ByteUtils.bytesToHex(bytes));
                int slotId = msg.arg1;
                MessageTag tag = MessageTag.ADAPTER.decode(bytes);
                JLog.logv("handleMessage() - token: " + tag.token +
                        ", type: " + tag.type +
                        ", Id: " + tag.id +
                        ", error: " + tag.error +
                        ", slot id: " + slotId);
                if (tag.type == MessageType.UCLOUDLINK_MSG_RESPONSE) {
                    switch (tag.id) {
                        case UCLOUDLINK_EVENT:
                            UcloudServiceEventResp event_rsp;
                            event_rsp = UcloudServiceEventResp.ADAPTER.decode(tag.payload.toByteArray());
                            mCb.ucloudServiceEventResponse(slotId, event_rsp.response.getValue());
                            break;
                        default:
                            JLog.loge("unexpected msg id");
                    }
                } else if (tag.type == MessageType.UCLOUDLINK_MSG_INDICATION) {
                    switch (tag.id) {
                        case UCLOUDLINK_EVENT:
                            UcloudServiceEventResp event_rsp;
                            event_rsp = UcloudServiceEventResp.ADAPTER.decode(tag.payload.toByteArray());
                            mCb.ucloudServiceEventResponse(slotId, event_rsp.response.getValue());
                            break;
                        default:
                            JLog.loge("unexpected msg id");
                    }
                } else {
                    JLog.loge("unexpected msg type");
                }
            } catch (Exception e) {
                JLog.loge("error occured when parsing the resp/ind");
                e.printStackTrace();
            }
        }
    }
    /*启动服务*/
    @Override
    public void onCreate() {
        super.onCreate();
        JLog.logi("ucloudlink onCreate()");

        JLog.logi("simCount: " + simSlots);
        if (mRespHdlr == null) {
            HandlerThread recvThread = new HandlerThread("recvThread");
            recvThread.start();
            mRespHdlr = new RecvHandler(recvThread.getLooper());
        }
    }
    /*创建socket*/
    private void createSocket(int i) {
        if (mUcloudSocket[i] == null) {
            HandlerThread sendThread = new HandlerThread("recvThread" + i);
            sendThread.start();
            mUcloudSocket[i] = new UcloudServiceClientSocket(mRespHdlr, i, sendThread.getLooper());
            new Thread(mUcloudSocket[i]).start();
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        JLog.logi("ucloudlink onBind()");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        JLog.logi("onDestroy()");
        for (int i = 0; i < simSlots; i++) {
            mUcloudSocket[i].toDestroy();
        }
        stopSelf();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        JLog.logi("onLowMemory()");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        JLog.logi("onTrimMemory level: " + level);
    }
    /*服务接口实现*/
    private final IUcloudServiceClientService.Stub mBinder = new IUcloudServiceClientService.Stub() {
        public int registerCallback(IUcloudServiceClientServiceCallback cb) {
            if (!verifyAuthenticity(mBinder.getCallingUid())) {
                JLog.logd("Cannot perform! returning failure");
                return UcloudServiceError.UCLOUD_ERROR;
            }
            UcloudServiceClientService.this.mCb = cb;
            if (cb == null) {
                JLog.logd("registerCallback() - null cb");
            }
            return UcloudServiceError.UCLOUD_SUCCESS;
        }

        public int deregisterCallback(IUcloudServiceClientServiceCallback cb) {
            if (!verifyAuthenticity(mBinder.getCallingUid())) {
                JLog.loge("Cannot perform! returning failure");
                return UcloudServiceError.UCLOUD_ERROR;
            }
            UcloudServiceClientService.this.mCb = null;
            return UcloudServiceError.UCLOUD_SUCCESS;
        }

        public int ucloudServiceEventProc(int slot, int event, byte[] eventByte, int subEvent,String subEventString,int errCode,int eventTimeout) {
            JLog.logi("ucloudlink UcloudServiceEventProc");
            synchronized (this) {
                if (event == UcloudServiceEvent.UCLOUDLINK_CONNECT_SOCKET) {
                    JLog.logi("ucloudlink UCLOUD_CONNECT_SOCKET");
                    createSocket(slot);
                    return UcloudServiceError.UCLOUD_SUCCESS;
                } else if (event ==  UcloudServiceEvent.UCLOUDLINK_DISCONNECT_SOCKET) {
                    JLog.logi("ucloudlink UCLOUD_DISCONNECT_SOCKET");
                    disconnectSocket(slot);
                    return UcloudServiceError.UCLOUD_SUCCESS;
                }
                JLog.logv("ucloudlink UcloudServiceEvent eventByte len:" + eventByte.length);
                if (!verifyAuthenticity(mBinder.getCallingUid())) {
                    JLog.loge("ucloudlink Cannot perform! returning failure");
                    return UcloudServiceError.UCLOUD_ERROR;
                }
                if (slot >= simSlots) {
                    JLog.loge("ucloudlink Sim Slot not supported!" + slot);
                    return UcloudServiceError.UCLOUD_ERROR;
                }
                JLog.logv("ucloudlink UcloudServiceEvent() - slot: " + slot + "; event: " + event);
                if (mUcloudSocket[slot] == null) {
                    JLog.loge("ucloudlink socket is not connected");
                    return UcloudServiceError.UCLOUD_ERROR;
                }
//                byte[] bytes = {1,1};
                JLog.logv("ucloudlink UcloudServiceEvent() - slot: " + slot + "; event: " + event);
                MessageTag.Builder tag = new MessageTag.Builder();
               tag.token(mToken++).type(MessageType.UCLOUDLINK_MSG_REQUEST).id(MessageId.UCLOUDLINK_EVENT).error(Error.UCLOUDLINK_ERR_SUCCESS);
                // get request
                JLog.logv("ucloudlink  wangkun get request:  " + slot + "; event: " + event);
                UcloudServiceEventReq.Builder req = new UcloudServiceEventReq.Builder();
                req.event(UcloudServiceEventReq.Event.fromValue(event));
                JLog.logv("ucloudlink  get request:  " + slot + "; event: " + event);
                req.eventByte(ByteString.of(eventByte));
                JLog.logv("ucloudlink  get request:  " + slot + "; event: " + event);
                req.subEvent(subEvent);
                JLog.logv("ucloudlink  subEvent:  " + slot + "; event: " + event);
                req.subEventString(subEventString);
                JLog.logv("ucloudlink  subEventString:  " + slot + "; event: " + event);
                req.error_code(UcloudServiceEventReq.ErrorCause.fromValue(errCode));
                req.eventTimeout(eventTimeout);
                JLog.logv("ucloudlink  error_code:  " + slot + "; event: " + event);
                tag.payload(ByteString.of(UcloudServiceClientMsgPacking.packMsg(MessageId.UCLOUDLINK_EVENT, MessageType.UCLOUDLINK_MSG_REQUEST, req.build())));
                JLog.logv("ucloudlink  payload:  " + slot + "; event: " + event);
                byte[] bytes = UcloudServiceClientMsgPacking.packTag(tag.build());
                JLog.logv("ucloudlink !!UcloudServiceEvent: bytes" + ByteUtils.bytesToHex(bytes));
                Message msg = mUcloudSocket[slot].obtainMessage(EVENT_REQ, bytes);// wldebug
                JLog.logv("ucloudlink  msg:  " + slot + "; event: " + event);
                msg.sendToTarget();
                JLog.logv("ucloudlink  sendToTarget:  " + slot + "; event: " + event);
                return UcloudServiceError.UCLOUD_SUCCESS;
            }

        }
        public boolean getSocketDisConnectStatus() {
            JLog.logv("ucloudlink service to socket ucloudlink  getSocketDisConnectStatus");
            if(mUcloudSocket[0] == null){
                JLog.logv("ucloudlink mUcloudSocket[0] == null");
                return true;
            }else if(mUcloudSocket[0].getSocketDisConnectStatus() == true){
                JLog.logv("ucloudlink mUcloudSocket[0].getSocketDisConnectStatus() == true");
                return true;
            }else{
                return false;
            }

        }
    };

    private void disconnectSocket(final int slot) {
        JLog.logv("ucloudlinkserviceclientservice  disconnectSocket");
        if (mUcloudSocket[slot] != null) {
            mUcloudSocket[slot].postDelayed(new Runnable() {
                @Override
                public void run() {
                    mUcloudSocket[slot].toDestroy();
                    mUcloudSocket[slot] = null;
                }
            },200);
           
        }
    }

    private Application readApplication(XmlResourceParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Application");
        Application app = new Application();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG) {
            if (eventType != XmlPullParser.START_TAG) {
                app.parsingFail = true;
                JLog.loge("parse fail");
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
                    JLog.loge("parse fail");
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
                    JLog.loge("parse fail");
                    break;
                }
            } else {
                app.parsingFail = true;
                JLog.loge("parse fail" + tagName);
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

    private boolean verifyAuthenticity(int uid) {
        return true;
    }
}

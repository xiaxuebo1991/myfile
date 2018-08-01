package com.ucloudlink.refact.business.softsim.download;

import android.annotation.CallSuper;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimRsp;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimRspOrder;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimBinRsp;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimInfoRsp;
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType;
import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.channel.transceiver.protobuf.MessagePacker;
import com.ucloudlink.refact.business.softsim.download.remote.RequesteAction;
import com.ucloudlink.refact.business.softsim.download.remote.TransceiverAdapter;
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimBinInfoSingleReq;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimBinLocalFile;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rx.Single;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

import static com.ucloudlink.refact.utils.JLog.logk;

/**
 * Created by shiqianhua on 2016/12/1.
 */

public class SoftsimRequest {
    private String TAG = "SoftsimRequest";
    State curState;
    State lastState;
    private String                      username;
    private String                      order;
    private FlowOrder                   flowOrder;
    private OrderInfo                   orderInfo;
    private ArrayList<SoftsimLocalInfo> softsimLocalInfos;
    ArrayList<SoftsimBinLocalFile> softsimBinFiles;

    private DispatchSoftsimRsp dispatchRsp;
    private GetSoftsimInfoRsp  softsimInfoRsp;
    private GetSoftsimBinRsp   softsimBinRsp;

    private TransceiverAdapter transceiverAdapter;

    private Single<OrderInfo> sendData;
    private String            sessionid;

    private enum State {
        WAIT_SOCKET, DISPATCH_SOFTSIM, GETTING_SIM_INFO, GETTING_BIN, SUCC, FAIL, CANCEL,
    }

    private static final int EVENT_START               = 1;
    private static final int EVENT_START_TIMEOUT       = 2;
    private static final int EVENT_SOCKET_OK           = 3;
    private static final int EVENT_DISPATCH_TIMEOUT    = 4;
    private static final int EVENT_DISPATCH_RECV       = 5;
    private static final int EVENT_GET_SIMINFO_REQ     = 6;
    private static final int EVENT_GET_SIMINFO_RSP     = 7;
    private static final int EVENT_GET_SIMINFO_TIMEOUT = 8;
    private static final int EVENT_BIN_DATA_TIMEOUT    = 9;
    private static final int EVENT_BIN_DATA_RECV       = 10;
    private static final int EVENT_STOP                = 11;
    private static final int EVENT_SOCKET_FAIL         = 12;

    private int       mDownloadErrcode;
    private Throwable mDownloadException;

    private Handler mhandler;

    private Subscription socketSub;
    private Subscription dispatchSub;
    private Subscription getSimInfoSub;
    private Subscription getSimBinSub;

    public SoftsimRequest(final String username, final FlowOrder flowOrder, String sessionid, TransceiverAdapter transceiverAdapter) {
        this.username = username;
        this.flowOrder = flowOrder;
        this.order = flowOrder.getOrderId();
        this.sessionid = sessionid;

        this.transceiverAdapter = transceiverAdapter;
        mhandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (!processMessageTotal(msg)) {
                    super.handleMessage(msg);
                }
            }
        };
        stateInit();
    }

    private void stateInit() {
        curState = State.WAIT_SOCKET;
        enterState(curState, curState);
    }

    private void enterState(State next, State lastState) {
        switch (next) {
            case WAIT_SOCKET:
                waitForSocket();
                break;
            case DISPATCH_SOFTSIM:
                dispatchSoftsimRequst(username, order);
                break;
            case GETTING_SIM_INFO:
                getSoftsimInfoAction();
                break;
            case GETTING_BIN:
                getBinFinAction();
                break;
            case SUCC:
                onSucc(order, orderInfo, softsimLocalInfos, softsimBinFiles);
                break;
            case FAIL:
                String msg = (mDownloadException == null || mDownloadException.getMessage() == null) ? "null" : mDownloadException.getMessage();
                onError(order, mDownloadErrcode, new Throwable(msg));
                break;
            case CANCEL:
                break;
            default:
                break;
        }
    }

    private void exitState(State cur, State next) {
        switch (next) {
            case WAIT_SOCKET:
                cancelSub(socketSub);
                break;
            case DISPATCH_SOFTSIM:
                cancelSub(dispatchSub);
                break;
            case GETTING_SIM_INFO:
                cancelSub(getSimInfoSub);
                break;
            case GETTING_BIN:
                cancelSub(getSimBinSub);
                break;
            case SUCC:

                break;
            case FAIL:

                break;
            case CANCEL:

                break;
            default:
                break;
        }
    }

    private void transToNextState(State next) {
        if (next != curState) {
            logk("transToNextState: " + curState + " -> " + next + "  order:" + order);
            exitState(curState, next);
            lastState = curState;
            curState = next;
            enterState(curState, lastState);
        }
    }

    private void processErrRsp(Throwable t) {
        String err = ErrorCode.INSTANCE.getErrString(t);
        JLog.logd(TAG, "processErrRsp: " + err + " " + t);

        ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
        JLog.logd(TAG, "processErrRsp: errinfo:" + info);
        if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
            // TODO: 2017/6/5  
        } else if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
            // TODO: 2017/6/5
        } else {

        }

        mDownloadErrcode = info.getCode();
        mDownloadException = t;
    }

    private boolean processMessageStateWaitSocket(Message msg) {
        switch (msg.what) {
            case EVENT_SOCKET_OK:
                transToNextState(State.DISPATCH_SOFTSIM);
                break;
            case EVENT_START_TIMEOUT:
                mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_SOCKET_TIMEOUT();
                mDownloadException = new Throwable("socket create timeout!");
                waitForSocket();
                break;
            case EVENT_SOCKET_FAIL:
                waitForSocket();
                break;
            case EVENT_STOP:
                mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL();
                mDownloadException = new Throwable("user cancel");
                transToNextState(State.FAIL);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean processMessageStateDispatchSoftsim(Message msg) {
        switch (msg.what) {
            case EVENT_DISPATCH_RECV:
                switch (msg.arg1) {
                    case 0:
                        dispatchRsp = (DispatchSoftsimRsp) msg.obj;
                        orderInfo = getOrderInfoFromDispatchRsp(order, dispatchRsp);
                        if (orderInfo == null) {
                            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_NO_ORDER();
                            transToNextState(State.FAIL);
                        } else {
                            transToNextState(State.GETTING_SIM_INFO);
                        }
                        break;
                    case -1:
                        Throwable t = (Throwable) msg.obj;
                        JLog.loge(TAG, "processMessageStateDispatchSoftsim: dispatch softwsim failed!" + t.getMessage());
                        if (t instanceof TimeoutException) {
                            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_DISPATCH_TIMEOUT();
                            mDownloadException = t;
                        } else {
                            processErrRsp(t);
                        }
                        transToNextState(State.FAIL);
                        break;
                }
                break;
            case EVENT_DISPATCH_TIMEOUT:
                mDownloadException = new Throwable("dispatch timeout!");
                mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_DISPATCH_TIMEOUT();
                transToNextState(State.FAIL);
                break;
            case EVENT_STOP:
                mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL();
                mDownloadException = new Throwable("user cancel!");
                transToNextState(State.FAIL);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean processMessageStateGetSoftsimInfo(Message msg) {
        switch (msg.what) {
            case EVENT_GET_SIMINFO_RSP:
                switch (msg.arg1) {
                    case 0:
                        softsimInfoRsp = (GetSoftsimInfoRsp) msg.obj;
                        //JLog.logd(TAG, "processMessageStateGetSoftsimInfo: getsoftsim info!" + softsimInfoRsp); // do no print ki opc
                        softsimLocalInfos = MessagePacker.INSTANCE.getSoftsimInfoListFromGetInfoRsp(softsimInfoRsp);
                        if (softsimLocalInfos == null || softsimLocalInfos.size() == 0) {
                            JLog.loge(TAG, "processMessageStateGetSoftsimInfo: softsim is null");
                            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_NO_SOFTSIM();
                            transToNextState(State.FAIL);
                        } else {
                            int errcode = MessagePacker.INSTANCE.softsimListValid(softsimInfoRsp);
                            if (errcode == 0) {
                                transToNextState(State.GETTING_BIN);
                            } else {
                                JLog.loge(TAG, "processMessageStateGetSoftsimInfo: softsimListValid check fail!");
                                mDownloadErrcode = errcode;
                                transToNextState(State.FAIL);
                            }
                        }
                        break;
                    case -1:
                        Throwable t = (Throwable) msg.obj;
                        JLog.loge(TAG, "processMessageStateGetSoftsimInfo: getsoftsim info failed!" + t.getMessage());
                        if (t instanceof TimeoutException) {
                            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_GET_SOFTSIM_INFO_TIMEOUT();
                            mDownloadException = t;
                        } else {
                            processErrRsp(t);
                        }
                        transToNextState(State.FAIL);
                        break;
                }
                break;
            case EVENT_STOP:
                mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL();
                mDownloadException = new Throwable("user cancel!");
                transToNextState(State.FAIL);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean processMessageStateGetBin(Message msg) {
        switch (msg.what) {
            case EVENT_BIN_DATA_RECV:
                switch (msg.arg1) {
                    case 0:
                        softsimBinRsp = (GetSoftsimBinRsp) msg.obj;
                        JLog.logd(TAG, "processMessageStateGetBin: get bin succ!" + softsimBinRsp);

                        softsimBinFiles = getSoftsimBinFilesFromBinRsp(softsimBinRsp);
                        if (softsimBinFiles == null || softsimBinFiles.size() == 0) {
                            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_BIN_FILE_NULL();
                            JLog.loge(TAG, "processMessageStateGetBin: get no bin files ");
                            transToNextState(State.FAIL);
                        } else {
                            transToNextState(State.SUCC);
                        }
                        break;
                    case -1:
                        Throwable t = (Throwable) msg.obj;
                        if (t instanceof TimeoutException) {
                            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_GET_BIN_TIMEOUT();
                            mDownloadException = t;
                        } else {
                            processErrRsp(t);
                        }
                        transToNextState(State.FAIL);
                        break;
                }
                break;
            case EVENT_STOP:
                mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL();
                mDownloadException = new Throwable("user cancel!");
                transToNextState(State.FAIL);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean processMessageStateSucc(Message msg) {
        switch (msg.what) {
            default:
                return false;
        }
        //return true;
    }

    private boolean processMessageStateFail(Message msg) {
        switch (msg.what) {
            default:
                return false;
        }
        //return true;
    }

    private boolean processMessageStateCancel(Message msg) {
        switch (msg.what) {
            default:
                return false;
        }
        //return true;
    }

    private boolean processMessageTotal(Message msg) {
        switch (curState) {
            case WAIT_SOCKET:
                return processMessageStateWaitSocket(msg);
            case DISPATCH_SOFTSIM:
                return processMessageStateDispatchSoftsim(msg);
            case GETTING_SIM_INFO:
                return processMessageStateGetSoftsimInfo(msg);
            case GETTING_BIN:
                return processMessageStateGetBin(msg);
            case SUCC:
                return processMessageStateSucc(msg);
            case FAIL:
                return processMessageStateFail(msg);
            case CANCEL:
                return processMessageStateCancel(msg);
            default:
                return false;
        }
    }

    private void cancelSub(Subscription sub) {
        if (sub != null && !sub.isUnsubscribed()) {
            sub.unsubscribe();
        }
    }

    private void waitForSocket() {
        if (transceiverAdapter.getIsSocketOk()) {
            mhandler.sendEmptyMessage(EVENT_SOCKET_OK);
            return;
        }
        cancelSub(socketSub);
        socketSub = transceiverAdapter.getSocketStatusOb().filter(new Func1<Boolean, Boolean>() {
            @Override
            public Boolean call(Boolean aBoolean) {
                return aBoolean;
            }
        }).timeout(75, TimeUnit.SECONDS).subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean aBoolean) {
                JLog.logd(TAG, "call: socket result!!!" + aBoolean);
                mhandler.sendEmptyMessage(aBoolean ? EVENT_SOCKET_OK : EVENT_SOCKET_FAIL);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                JLog.logd(TAG, "call: timeout!!");
                mhandler.sendEmptyMessage(EVENT_START_TIMEOUT);
            }
        });
    }

    private void dispatchSoftsimRequst(String username, String order) {
        cancelSub(dispatchSub);
        dispatchSub = RequesteAction.requestDispatchSoftsim(username, order, sessionid, transceiverAdapter, 35).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                Message.obtain(mhandler, EVENT_DISPATCH_RECV, 0, 0, o).sendToTarget();
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (throwable instanceof TimeoutException) {
                    throwable = new TimeoutException("TIMEOUT: dispatch softsim timeout!");
                }
                Message.obtain(mhandler, EVENT_DISPATCH_RECV, -1, 0, throwable).sendToTarget();
            }
        });

    }

    private void getSoftsimInfoRequest(ArrayList<String> imsis) {
        cancelSub(getSimInfoSub);
        getSimInfoSub = RequesteAction.requestGetSoftsimInfo(imsis, sessionid, transceiverAdapter, 35).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                Message.obtain(mhandler, EVENT_GET_SIMINFO_RSP, 0, 0, o).sendToTarget();
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (throwable instanceof TimeoutException) {
                    throwable = new TimeoutException("TIMEOUT: request softsim info timeout!");
                }
                Message.obtain(mhandler, EVENT_GET_SIMINFO_RSP, -1, 0, throwable).sendToTarget();
            }
        });
    }

    private void getSoftsimInfoAction() {
        ArrayList<String> sims = MessagePacker.INSTANCE.getSoftsimListByDispatchRsp(dispatchRsp.orders);
        // TODO: 2017/5/10 和css讨论是否要加软卡版本判断
        JLog.logd(TAG, "getSoftsimInfoAction: start to get imsi info!" + sims);
        if (sims != null && sims.size() != 0) {
            getSoftsimInfoRequest(sims);
        } else {
            JLog.loge(TAG, "getSoftsimInfoAction: Cannot find softsim imsi!");
            mDownloadErrcode = ErrorCode.INSTANCE.getSOFTSIM_DL_NO_SOFTSIM();
            transToNextState(State.FAIL);
        }
    }

    private void getBinFileRequst(ArrayList<SoftsimBinInfoSingleReq> bins) {
        cancelSub(getSimBinSub);
        getSimBinSub = RequesteAction.reqeustSoftsimBinFile(bins, sessionid, transceiverAdapter, 60).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                Message.obtain(mhandler, EVENT_BIN_DATA_RECV, 0, 0, o).sendToTarget();
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (throwable instanceof TimeoutException) {
                    throwable = new Throwable("TIMEOUT: get bin file request failed!");
                }
                Message.obtain(mhandler, EVENT_BIN_DATA_RECV, -1, 0, throwable).sendToTarget();
            }
        });
    }

    private ArrayList<SoftsimBinInfoSingleReq> getBinrefBySoftsimRsp(GetSoftsimInfoRsp rsp) {
        ArrayList<SoftsimBinInfoSingleReq> bins = new ArrayList<>();
        boolean find = false;

        for (com.ucloudlink.framework.protocol.protobuf.SoftsimInfo info : rsp.softsims) {

            Pair[] binRefs = new Pair[]{new Pair<>(1, info.plmnBinRef), new Pair<>(2, info.feeBinRef), new Pair<>(3, info.fplmnRef)};

            for (Pair<Integer, String> binRefPair : binRefs) {
                String binRef = binRefPair.second;
                if (binRef != null && !binRef.equals("")) {
                    if (ServiceManager.accessEntry.softsimEntry.isBinRefAlreadyInDevice(binRef)) {
                        JLog.logd(TAG, "getBinrefBySoftsimRsp: " + binRef + " is still in!");
                        continue;
                    }
                    find = false;
                    for (SoftsimBinInfoSingleReq bin : bins) {
                        if (bin.getRef().equals(binRef)) {
                            find = true;
                            break;
                        }
                    }
                    if (!find) {
                        bins.add(new SoftsimBinInfoSingleReq(binRefPair.first, binRef));
                    }
                }
            }
        }

        return bins;
    }

    private void getBinFinAction() {
        ArrayList<SoftsimBinInfoSingleReq> bins = getBinrefBySoftsimRsp(softsimInfoRsp);

        if (bins != null && bins.size() != 0) {
            getBinFileRequst(bins);
        } else {
            JLog.logd(TAG, "getBinFinAction: no need to get any bin files");
            transToNextState(State.SUCC);
        }
    }

    private OrderInfo getOrderInfoFromDispatchRsp(String order, DispatchSoftsimRsp rsp) {
        JLog.logd(TAG, "getOrderInfoFromDispatchRsp: order" + order + " rsp:" + rsp);
        int count = 0;
        for (DispatchSoftsimRspOrder tmp : rsp.orders) {
            JLog.logd(TAG, "getOrderInfoFromDispatchRsp: order " + ++count + "  order:" + tmp);
            if (tmp.order.equals(order)) {
                OrderInfo orderInfo = new OrderInfo(tmp.order, MessagePacker.INSTANCE.getStringListByLongList(tmp.softsims));
                orderInfo.setOrderId(order);
                orderInfo.setSofsimList(MessagePacker.INSTANCE.getStringListByLongList(tmp.softsims));
                // sent order base info from flowOrder
                orderInfo.setCreateTime(flowOrder.getCreateTime());
                orderInfo.setActivatePeriod(flowOrder.getActivatePeriod());
                orderInfo.setMccLists(flowOrder.getMccLists());
                orderInfo.setSimUsePolicy(flowOrder.getSeedSimPolicy());
                JLog.logd(TAG, "getOrderInfoFromDispatchRsp:  get orderinfo!" + orderInfo);

                return orderInfo;
            }
        }
        JLog.loge(TAG, "getOrderInfoFromDispatchRsp: cannot find the order!" + order);
        return null;
    }

    private ArrayList<SoftsimBinLocalFile> getSoftsimBinFilesFromBinRsp(GetSoftsimBinRsp rsp) {
        ArrayList<SoftsimBinLocalFile> bins = new ArrayList<>();

        for (com.ucloudlink.framework.protocol.protobuf.SoftsimBinInfo tmp : rsp.bins) {
            int type = 1;
            if (tmp.type == SoftsimBinType.PLMN_LIST_BIN) {
                type = 1;
            } else if (tmp.type == SoftsimBinType.FEE_BIN) {
                type = 2;
            } else if (tmp.type == SoftsimBinType.FPLMN_BIN) {
                type = 3;
            }
            SoftsimBinLocalFile binFile = new SoftsimBinLocalFile(tmp.binref, type, tmp.data.toByteArray());
            bins.add(binFile);
        }
        return bins;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public FlowOrder getFlowOrder() {
        return flowOrder;
    }

    public void setFlowOrder(FlowOrder flowOrder) {
        this.flowOrder = flowOrder;
    }

    @CallSuper
    public void onSucc(String order, OrderInfo orderInfo, ArrayList<SoftsimLocalInfo> sims, ArrayList<SoftsimBinLocalFile> bins) {

    }

    @CallSuper
    public void onError(String order, int errcode, Throwable throwable) {

    }

    public void cancel() {
        mhandler.sendEmptyMessage(EVENT_STOP);
    }

    public boolean isOver() {
        return (curState == State.SUCC || curState == State.FAIL || curState == State.CANCEL);
    }
}

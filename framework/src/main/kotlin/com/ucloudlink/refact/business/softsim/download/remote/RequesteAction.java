package com.ucloudlink.refact.business.softsim.download.remote;



import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.protocol.protobuf.CommonErrorcode;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimReq;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimRsp;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimRspOrder;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimBinReq;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimBinRsp;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimInfoReq;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimInfoRsp;
import com.ucloudlink.framework.protocol.protobuf.SeedCardType;
import com.ucloudlink.framework.protocol.protobuf.SimpleLoginReq;
import com.ucloudlink.framework.protocol.protobuf.SimpleLoginRsp;
import com.ucloudlink.framework.protocol.protobuf.SimpleLogoutReq;
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinReqInfo;
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType;
import com.ucloudlink.framework.protocol.protobuf.SoftsimDetailUnusable;
import com.ucloudlink.framework.protocol.protobuf.SoftsimFlowUploadReq;
import com.ucloudlink.framework.protocol.protobuf.SoftsimFlowUploadRsp;
import com.ucloudlink.framework.protocol.protobuf.SoftsimStatus;
import com.ucloudlink.framework.protocol.protobuf.UpdateSoftsimStatusReq;
import com.ucloudlink.framework.protocol.protobuf.UpdateSoftsimStatusRsp;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.channel.transceiver.protobuf.Message;

import com.ucloudlink.refact.channel.transceiver.protobuf.MessagePacker;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil;
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimBinInfoSingleReq;
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimUnusable;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Created by shiqianhua on 2016/12/23.
 */

public class RequesteAction {
    private static String TAG = "RequesteAction";

//    private static AtomicInteger snValue = new AtomicInteger(0);


    private interface ResponseHandler {
        public Single<Object> rspProc(Observable<Object> observable);
    }

    private static int getSn() {
        return MessagePacker.INSTANCE.getSn();
    }

    private static Observable<Object> observeResponse(final ProtoPacket message, TransceiverAdapter transceiverAdapter) {
        return transceiverAdapter.getReceivedObservable().
                map(new Func1<Message, ProtoPacket>() {
                    @Override
                    public ProtoPacket call(Message message) {
//                        JLog.logd(TAG, "call: map--------->" + message);
                        return (ProtoPacket)message.getPayload();
                    }
                })
                .filter(new Func1<ProtoPacket, Boolean>() {
                    @Override
                    public Boolean call(ProtoPacket msg) {
//                        JLog.logd(TAG, "call: check sn:" + message.getSn() + " -- " + msg.getSn());
                        return message.getSn() == msg.getSn();
                    }
                }).map(new Func1<ProtoPacket, Object>() {
                    @Override
                    public Object call(ProtoPacket msg) {
                        JLog.logd(TAG, "call: pkg.getsn:" + msg.getSn() + "msgsn:" + message.getSn());
                        Object re = null;
                        try {
                            re = ProtoPacketUtil.getInstance().decodeProtoPacket(msg);
                            if (re instanceof CommonErrorcode) {
                                JLog.logd(TAG, "call: Session timeout!!! ------------------>");
                                return new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ((CommonErrorcode) re).getValue());
                            }else{
                                return re;
                            }
                        } catch (Exception e) {
                            JLog.loge(TAG, "observeResponse: some exception! " + e.getMessage());
                            return e;
                        }
                        //return new Exception("null object!");
                    }
                });
    }

    private static Single<Object> request(final ProtoPacket message,
                                          final TransceiverAdapter transceiverAdapter,
                                          final ResponseHandler handler, final long timeout) {

        return Single.create(new Single.OnSubscribe<Object>() {
            @Override
            public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                if (transceiverAdapter.sendData(message) != 0) {
                    singleSubscriber.onError(new Throwable("socket send data  failed!"));
                }
                handler.rspProc(observeResponse(message, transceiverAdapter).timeout(timeout, TimeUnit.SECONDS))
                        .timeout(timeout, TimeUnit.SECONDS).subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        singleSubscriber.onSuccess(o);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (throwable instanceof TimeoutException) {
                            singleSubscriber.onError(new Throwable("TIMEOUT:"));
                        }
                        singleSubscriber.onError(throwable);
                    }
                });
            }
        }).doOnUnsubscribe(new Action0() {
            @Override
            public void call() {

            }
        });
    }

    public static Single<Object> requestSimpleLogin(String username, String passwd, String imei, String devType, String version,
                                                    final TransceiverAdapter transceiverAdapter, int timeout) {
        JLog.logd(TAG, "requestSimpleLogin: ");
        JLog.logk("requestSimpleLogin");
//        JLog.logd("username:" + username + ",passwd:" + passwd + ",imei:" + imei + ",devType:" + devType + ",version:" + version);
        JLog.logd("username:" + username + ",imei:" + imei + ",devType:" + devType + ",version:" + version);
        SimpleLoginReq req = new SimpleLoginReq(1, username, passwd, devType, version, Long.valueOf(imei));
        ProtoPacket packet = ProtoPacketUtil.getInstance().createSimpleLoginReqPacket(req, (short) getSn());

        return request(packet, transceiverAdapter, new ResponseHandler() {
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                return Single.create(new Single.OnSubscribe<Object>() {
                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                SimpleLoginRsp rsp = (SimpleLoginRsp)o;
                                if(rsp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()) {
                                    singleSubscriber.onSuccess(rsp);
                                }else {
                                    singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + rsp.errorCode));
                                }
                                singleSubscriber.onSuccess(o);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);
    }

    public static Single<Object> requestDispatchSoftsim(String username, String order, String sessionid, TransceiverAdapter transceiverAdapter, int timeout) {
        JLog.logd(TAG, "requestDispatchSoftsim: ");
        JLog.logk("requestDispatchSoftsim");

        ArrayList<String> orderList = new ArrayList<>();
        orderList.add(order);
        DispatchSoftsimReq req = new DispatchSoftsimReq(username, Long.valueOf(Configuration.INSTANCE.getImei(ServiceManager.appContext)), orderList);
        ProtoPacket packet = ProtoPacketUtil.getInstance().createDispatchSoftsimReqPacket(req, (short) getSn(), sessionid);

        return request(packet, transceiverAdapter, new ResponseHandler() {
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                final Subscription subRespOb;
                return Single.create(new Single.OnSubscribe<Object>() {
                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                if (o instanceof DispatchSoftsimRsp) {
                                    DispatchSoftsimRsp rsp = (DispatchSoftsimRsp)o;
                                    if(rsp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                        boolean isError = false;
                                        for(DispatchSoftsimRspOrder order: rsp.orders){
                                            if (order.errcode != 0) {
                                                singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + order.errcode));
                                                isError = true;
                                                break;
                                            }
                                        }
                                        if(!isError) {
                                            singleSubscriber.onSuccess(rsp);
                                        }
                                    }else {
                                        singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + rsp.errorCode));
                                    }
                                } else if (o instanceof Exception){
                                    singleSubscriber.onError((Exception)o);
                                } else {
                                    singleSubscriber.onError(new Throwable("unknown output type:" + o.getClass().getName()));
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);
    }

    public static Single<Object> requestGetSoftsimInfo(ArrayList<String> imsis, String sessionid, TransceiverAdapter transceiverAdapter, int timeout) {
        JLog.logd(TAG, "requestGetSoftsimInfo: ");
        JLog.logk("requestGetSoftsimInfo");

        ArrayList<Long> imsiArray = new ArrayList<>();
        for (String i : imsis) {
            imsiArray.add(Long.valueOf(i));
        }
        GetSoftsimInfoReq req = new GetSoftsimInfoReq(imsiArray);
        ProtoPacket packet = ProtoPacketUtil.getInstance().createGetSoftsimInfoReqPacket(req, (short) getSn(), sessionid);

        return request(packet, transceiverAdapter, new ResponseHandler() {
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                return Single.create(new Single.OnSubscribe<Object>() {
                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                if (o instanceof GetSoftsimInfoRsp) {
                                    GetSoftsimInfoRsp rsp = (GetSoftsimInfoRsp)o;
                                    if(rsp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                        singleSubscriber.onSuccess(o);
                                    }else {
                                        singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + rsp.errorCode));
                                    }
                                } else {
                                    singleSubscriber.onError(new Throwable("unknown output type:" + o.getClass().getName()));
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);
    }

    public static Single<Object> reqeustSoftsimBinFile(ArrayList<SoftsimBinInfoSingleReq> bins, String sessionid, TransceiverAdapter transceiverAdapter, int timeout) {
        JLog.logd(TAG, "reqeustSoftsimBinFile: ");
        JLog.logk("reqeustSoftsimBinFile");

        SoftsimBinType type;
        ArrayList<SoftsimBinReqInfo> binReqList = new ArrayList<>();
        for (SoftsimBinInfoSingleReq t : bins) {
            if (t.getType() == 1) {
                type = SoftsimBinType.PLMN_LIST_BIN;
            } else if(t.getType() == 2) {
                type = SoftsimBinType.FEE_BIN;
            } else  if(t.getType() == 3){
                type = SoftsimBinType.FPLMN_BIN;
            }else {
                type = SoftsimBinType.PLMN_LIST_BIN; // TODO:!!!
            }
            binReqList.add(new SoftsimBinReqInfo(type, t.getRef()));
        }


        GetSoftsimBinReq req = new GetSoftsimBinReq(binReqList);
        ProtoPacket packet = ProtoPacketUtil.getInstance().createGetSoftsimBinReqPacket(req, (short) getSn(), sessionid);

        return request(packet, transceiverAdapter, new ResponseHandler() {
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                final Subscription subRespOb;
                return Single.create(new Single.OnSubscribe<Object>() {

                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                if (o instanceof GetSoftsimBinRsp) {
                                    GetSoftsimBinRsp rsp = (GetSoftsimBinRsp)o;
                                    if(rsp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()) {
                                        singleSubscriber.onSuccess(o);
                                    }else {
                                        singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + rsp.errorCode));
                                    }
                                } else {
                                    singleSubscriber.onError(new Throwable("unknown output type:" + o.getClass().getName()));
                                }

                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);
    }


    public static Single<Object> requestSimpleLogout(int reason, String sessionid, TransceiverAdapter transceiverAdapter, int timeout) {
        JLog.logd(TAG, "requestLogoutSoftsim: ");
        JLog.logk("requestLogoutSoftsim");

        SimpleLogoutReq req = new SimpleLogoutReq(reason);
        ProtoPacket packet = ProtoPacketUtil.getInstance().createSimpleLogoutReqPacket(req, (short) getSn(), sessionid);

        return request(packet, transceiverAdapter, new ResponseHandler() {
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                return Single.create(new Single.OnSubscribe<Object>() {
                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                singleSubscriber.onSuccess(o);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);
    }

    public static Single<Object> requestSoftsimUpdateSimple(String username, String mcc, String mnc,
                                                            ArrayList<SoftsimLocalInfo> softsims, String usingImsi,
                                                            String sessionid, TransceiverAdapter transceiverAdapter, int timeout) {
        JLog.logd(TAG, "requestSoftsimUpdateSimple: ");
        JLog.logk("requestSoftsimUpdateSimple");

        ArrayList<SoftsimStatus> lists = new ArrayList<>();
        for (SoftsimLocalInfo localInfo : softsims) {
            ArrayList<SoftsimDetailUnusable> unuseList = new ArrayList<>();
            for (SoftsimUnusable u : localInfo.getLocalUnuseReason()) {
                SoftsimDetailUnusable unuse = new SoftsimDetailUnusable(u.getMcc(), u.getMnc(), u.getErrcode(), u.getSubErr());
                unuseList.add(unuse);
            }
            SoftsimStatus info = new SoftsimStatus(Long.valueOf(localInfo.getImsi()), localInfo.getTimeStamp(), usingImsi.equals(localInfo.getImsi()), unuseList);
            lists.add(info);
        }

        UpdateSoftsimStatusReq req = new UpdateSoftsimStatusReq(username, mcc, mnc, Long.valueOf(Configuration.INSTANCE.getImei(ServiceManager.appContext)), lists);
        JLog.logd(TAG, "requestSoftsimUpdateSimple: req:" + req);

        ProtoPacket packet = ProtoPacketUtil.getInstance().createUpdateSoftsimStatusReqPacket(req, (short) getSn(), sessionid);

        return request(packet, transceiverAdapter, new ResponseHandler() {
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                return Single.create(new Single.OnSubscribe<Object>() {
                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                if(o instanceof UpdateSoftsimStatusRsp) {
                                    UpdateSoftsimStatusRsp rsp = (UpdateSoftsimStatusRsp)o;
                                    if(rsp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                        singleSubscriber.onSuccess(o);
                                    }else{
                                        singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + rsp.errorCode));
                                    }
                                }else {
                                    singleSubscriber.onError(new Throwable("unknown output type:" + o.getClass().getName()));
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);
    }

    public static Single<Object> requestUploadSoftsimFlow(final int reqId, String imei, SoftsimFlowStateInfo info,
                                                          String sessionid, TransceiverAdapter transceiverAdapter, int timeout){
        JLog.logk("SCFlowLog requestUploadSoftsimFlow ...");
        SoftsimFlowUploadReq req = new SoftsimFlowUploadReq(reqId, info.getUsername(), Long.valueOf(imei), info.getImsi(),
                                    (int)TimeUnit.MILLISECONDS.toSeconds(info.getStartTime()),
                                    (int)TimeUnit.MILLISECONDS.toSeconds(info.getEndTime()),
                                    info.getMcc(), info.getUpFlow(), info.getDownFlow(), info.getUpUserFlow(), info.getDownUserFlow(),
                                    info.getUpSysFlow(), info.getDownSysFlow(), (info.isSoftsim())? SeedCardType.SOFTSIM: SeedCardType.PHYSIM);
        JLog.logd(TAG, "SCFlowLog requestUploadSoftsimFlow: " + req);

        if(info.getUsername() == null || info.getImsi() == null|| info.getMcc() == null){
            return Single.create(new Single.OnSubscribe<Object>() {
                @Override
                public void call(SingleSubscriber<? super Object> singleSubscriber) {
                    singleSubscriber.onError(new Throwable("param is null"));
                }

            });
        }

        ProtoPacket packet = ProtoPacketUtil.getInstance().createSoftsimUploadFlowReqPacket(req, (short) getSn(), sessionid);

        return request(packet, transceiverAdapter, new ResponseHandler(){
            @Override
            public Single<Object> rspProc(final Observable<Object> observable) {
                return Single.create(new Single.OnSubscribe<Object>(){
                    @Override
                    public void call(final SingleSubscriber<? super Object> singleSubscriber) {
                        observable.subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                if(o instanceof SoftsimFlowUploadRsp){
                                    if(((SoftsimFlowUploadRsp) o).errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                        singleSubscriber.onSuccess(o);
                                    }else {
                                        singleSubscriber.onError(new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ((SoftsimFlowUploadRsp) o).errorCode));
                                    }
                                }else {
                                    singleSubscriber.onError(new Throwable("unknown output type:" + o.getClass().getName()));
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                singleSubscriber.onError(throwable);
                            }
                        }, new Action0() {
                            @Override
                            public void call() {
                                singleSubscriber.onError(new Throwable("complite"));
                            }
                        });
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                    }
                });
            }
        }, timeout);

    }

}

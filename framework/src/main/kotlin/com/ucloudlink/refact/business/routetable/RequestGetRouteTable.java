package com.ucloudlink.refact.business.routetable;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.protocol.protobuf.CommonErrorcode;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.channel.transceiver.protobuf.Message;
import com.ucloudlink.refact.channel.transceiver.protobuf.Priority;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil;
import com.ucloudlink.refact.channel.transceiver.NettyTransceiver;

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
 * Created by haiping.liu on 2017/7/15.
 */

public class RequestGetRouteTable {
    private static final String TAG = "RequestGetRouteTable";
    private static RequestGetRouteTable requestGetRouteTable;
    private static Object lock = new Object();
    private Subscription subHandler;
    private NettyTransceiver transceiver;

    private RequestGetRouteTable() {
        transceiver = ServiceManager.INSTANCE.getTransceiver();
    }

    public static RequestGetRouteTable getRequestGetRouteTable() {

        synchronized (lock) {
            if (requestGetRouteTable == null) {
                requestGetRouteTable = new RequestGetRouteTable();
            }
        }
        return requestGetRouteTable;
    }

    //请求路由表
    public Single<Object> requestGetRouteTable(ProtoPacket packet, int timeout) {
        return request(packet, new ResponseHandler() {
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
                                if (throwable instanceof TimeoutException) {
                                    singleSubscriber.onError(new Throwable("Fail:Server Socket Timeout"));
                                }
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

    private Single<Object> request(final ProtoPacket protoPacket, final ResponseHandler handler, final long timeout) {

        return Single.create(new Single.OnSubscribe<Object>() {

            @Override
            public void call(final SingleSubscriber<? super Object> singleSubscriber) {

                transceiver.send(new Message(protoPacket.getSn(), ServerRouter.Dest.ASS, Priority.ALWAYS_SEED_CHANNEL, protoPacket));
                subHandler = handler.rspProc(observeResponse(protoPacket)
                        .timeout(timeout, TimeUnit.SECONDS))
                        .subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object o) {
                                singleSubscriber.onSuccess(o);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                if (throwable instanceof TimeoutException) {
                                    singleSubscriber.onError(new Throwable("Fail:Server Response Timeout"));
                                }
                                singleSubscriber.onError(throwable);
                            }
                        });
            }
        }).doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                if (subHandler != null) {
                    subHandler.unsubscribe();
                }

            }
        });
    }

    private interface ResponseHandler {
        Single<Object> rspProc(Observable<Object> observable);
    }


    private Observable<Object> observeResponse(final ProtoPacket message) {
        return transceiver.receive(ServerRouter.Dest.ASS).
                map(new Func1<Message, ProtoPacket>() {
                    @Override
                    public ProtoPacket call(Message message) {
                        return (ProtoPacket) message.getPayload();
                    }
                })
                .filter(new Func1<ProtoPacket, Boolean>() {
                    @Override
                    public Boolean call(ProtoPacket msg) {
                        return message.getSn() == msg.getSn();
                    }
                }).map(new Func1<ProtoPacket, Object>() {
                    @Override
                    public Object call(ProtoPacket msg) {
                        Object re = null;
                        try {
                            re = ProtoPacketUtil.getInstance().decodeProtoPacket(msg);
                            if (re instanceof CommonErrorcode) {
                                JLog.logd(TAG, "tRoute call: Session timeout!!! ------------------>");
                                return new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ((CommonErrorcode) re).getValue());
                            } else {
                                return re;
                            }
                        } catch (Exception e) {
                            JLog.loge(TAG, "tRoute observeResponse: some exception! " + e.getMessage());
                            return e;
                        }
                    }
                });
    }

    public void connectSocket() {
        JLog.logd(TAG, "tRoute connectSocket: ");
        transceiver.setNeedSocketConnect(ServerRouter.Dest.ASS, "GetRouteTableNeed");
    }

    public void stopSocket() {
        JLog.logd(TAG, "tRoute stopSocket: ");
        transceiver.setForbidSocketConnect(ServerRouter.Dest.ASS, "GetRouteTableNeed");
    }

}

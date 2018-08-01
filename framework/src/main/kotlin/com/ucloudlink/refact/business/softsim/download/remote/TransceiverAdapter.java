package com.ucloudlink.refact.business.softsim.download.remote;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.channel.transceiver.protobuf.Message;
import com.ucloudlink.refact.channel.transceiver.protobuf.Priority;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.channel.transceiver.Transceiver;
import com.ucloudlink.refact.channel.transceiver.secure.SecureUtil;
import com.ucloudlink.refact.business.softsim.download.SoftsimDownloadState;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

/**
 * Created by shiqianhua on 2017/5/4.
 */

public class TransceiverAdapter {
    private static final String TAG = "TransceiverAdapter";
    private Transceiver transceiver;
    private Context ctx;
    private boolean isSocketOk = false;
    private boolean isNetworkOk = false;
    private boolean isNeedSocket = false;

    private PublishSubject<Boolean> socketStatus = PublishSubject.create();

    public Observable<Message> getReceivedObservable() {
        return transceiver.receive(ServerRouter.Dest.ASS);
    }

    private void updateSocketState(boolean value){
        if(isNeedSocket){
            isSocketOk = value;
            socketStatus.onNext(value);
        }
    }

    public TransceiverAdapter(Context ctx) {
        this.ctx = ctx;
        transceiver = ServiceManager.transceiver;
        transceiver.setNeedSocketConnect(ServerRouter.Dest.ASS, "DownloadSoftsimNeed");
        transceiver.statusObservable(ServerRouter.Dest.ASS).subscribe(
                // onNext
                new Action1<String>() {
                    @Override
                    public void call(String s) {
                        JLog.logd(TAG, "transceiver.statusObservable: " + s);
                        if (s.equals("SocketConnected")) {
                            updateSocketState(true);
                        } else if (s.equals("SocketDisconnected")) {
                            updateSocketState(false);
                        }else if(s.equals("secureError")){
                            updateSocketState(false);
                            ServiceManager.accessEntry.softsimEntry.notifyMsg(SoftsimDownloadState.SECURITY_SOCKET_FAIL, 0, 0, SecureUtil.INSTANCE.getSecureErrorCmd());
                        }else if(s.equals("secureTimeout")){
                            updateSocketState(false);
                            ServiceManager.accessEntry.softsimEntry.notifyMsg(SoftsimDownloadState.SECURITY_SOCKET_TIMEOUT, 0, 0, SecureUtil.INSTANCE.getSecureErrorCmd());
                        }
                    }
                },
                // onError
                new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        JLog.loge(TAG, "transceiver.statusObservable " + throwable + " " + throwable.getMessage());
                        updateSocketState(false);
                    }
                },
                // onCompleted
                new Action0() {
                    @Override
                    public void call() {
                        JLog.logd(TAG, "transceiver.statusObservable: completed");
                    }
                }
        );

        isNetworkOk = getSystemNetworkConn();
    }

    private boolean getSystemNetworkConn() {
        ConnectivityManager connectivityManager = ConnectivityManager.from(ctx);
        NetworkInfo[] networks = connectivityManager.getAllNetworkInfo();
        boolean connect = false;
        if (networks != null && networks.length > 0) {
            for (int i = 0; i < networks.length; i++) {
                JLog.logd("count:" + i + " type:" + networks[i].getTypeName() + " status:" + networks[i].getState());
                if (networks[i].getState() == NetworkInfo.State.CONNECTED) {
                    connect = true;
                }
            }
        }
        return connect;
    }

    private void startNetworkMonitor(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                JLog.logd("onReceive action=" + intent.getAction());
                if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                    NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    NetworkInfo.State state = ni.getState();
                    if(state == NetworkInfo.State.CONNECTED){
                        isNetworkOk = true;
                        if(isNeedSocket){
                            socketReconnect();
                        }
                    }else {
                        isNetworkOk = false;
                        if(isNeedSocket){
                            clearSocketNeed();
                        }
                    }
                }
            }
        }, filter);
    }

    public boolean getIsSocketOk() {
        return isSocketOk;
    }

    public int sendData(ProtoPacket msg) {
        if (isSocketOk) {
            transceiver.send(new Message(msg.getSn(), ServerRouter.Dest.ASS, Priority.ALWAYS_SEED_CHANNEL, msg));
            return 0;
        } else {
            JLog.loge(TAG, "sendData: socket is not OK");
            return -1;
        }
    }

    public void startSocket(){
        isNeedSocket = true;
        isSocketOk = transceiver.isSocketConnected(ServerRouter.Dest.ASS);
        socketReconnect();
    }

    public void stopSocket() {
        clearSocketNeed();
        isNeedSocket = false;
        socketStatus.onNext(false);
    }

    private void clearSocketNeed(){
        transceiver.setForbidSocketConnect(ServerRouter.Dest.ASS, "DownloadSoftsimNeed");
    }

    private void socketReconnect() {
        transceiver.setNeedSocketConnect(ServerRouter.Dest.ASS, "DownloadSoftsimNeed");
    }

    public PublishSubject<Boolean> getSocketStatusOb() {
        return socketStatus;
    }
}

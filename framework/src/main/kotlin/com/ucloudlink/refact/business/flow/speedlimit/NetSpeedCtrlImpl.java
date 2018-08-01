package com.ucloudlink.refact.business.flow.speedlimit;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.netlimit.common.DnsUtils;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * Created by jianguo.he on 2018/1/10.
 */

public class NetSpeedCtrlImpl implements INetSpeedCtrl {

    private INetSpeed mINetSpeed;// = new NetSpeedImpl();
    private final byte[] mINetSpeedLock = new byte[0];

    private HashMap<String, NetSpeedInfo> ipMap = new HashMap<>();
    // 里面的数据是通过IP来设置限速并且IP为域名，通过UID来设置限速的不需要解析域名
    private HashMap<String, NetSpeedInfo> dnsMap;
    private final byte[] lock = new byte[0];

    private boolean isRegisterCloudSimNetworkCallback = false;
    private byte[] isRegisterCloudSimNetworkCallbackLock = new byte[0];
    private ConnectivityManager.NetworkCallback cloudSimNetworkCallback;
    private NetworkRequest mNetworkRequest;

    private List<ConnectivityManager.NetworkCallback> listCallback = new ArrayList<>();
    private byte[] listCallbackOpLock = new byte[0];

    /*private CardStateMonitor.NetworkStateListen networkListen = new CardStateMonitor.NetworkStateListen(){

        @Override
        public void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
            JLog.logi("NetSpeedCtrlLog, NetworkStateChange -> ddsId = " + ddsId +", state = " + (state==null?"null":state) +
                    ", type = " + type +", ifName = "+(ifName==null?"null":ifName) +
                    ", isExistIfNameExtra = "+ isExistIfNameExtra +", subId = "+subId +
                    ", seedSim.cardState = " +ServiceManager.seedCardEnabler.getCardState() +
                    ", seedSim.netState = " + ServiceManager.seedCardEnabler.getNetState() +
                    ", seedSim.subId = " + ServiceManager.seedCardEnabler.getCard().getSubId() +
                    ", cloudSim.cardState = " + ServiceManager.cloudSimEnabler.getCardState() +
                    ", cloudSim.netState = " + ServiceManager.cloudSimEnabler.getNetState() +
                    ", cloudSim.subId = " + ServiceManager.cloudSimEnabler.getCard().getSubId());

            if(subId > -1 && isExistIfNameExtra && !TextUtils.isEmpty(ifName)){
                final IDataEnabler dataEnabler = ServiceManager.cloudSimEnabler;
                if(dataEnabler==null ){
                    return;
                }
                int cloudSimSubId = dataEnabler.getCard().getSubId();
                if(cloudSimSubId > -1 && cloudSimSubId == subId){
                    if(state == NetworkInfo.State.CONNECTED){
                        registerNetworkCallback(0, "");
                    } else {
                        unRegisterNetworkCallback(0,"");
                    }
                }

            }

        }
    };*/

    private void putDns(String dns, NetSpeedInfo info){
        if(dnsMap==null){
            dnsMap = new HashMap<>();
        }
        synchronized (lock){
            if(!TextUtils.isEmpty(dns) && info!=null){
                dnsMap.put(dns, info);
            }
        }
    }

    @NotNull
    @Override
    public INetSpeed getINetSpeed() {
        if(mINetSpeed==null){
            synchronized (mINetSpeedLock){
                if(mINetSpeed==null){
                    mINetSpeed = ServiceManager.systemApi.getINetSpeed();
                }
            }
        }
        return mINetSpeed;
    }

    @Override
    public void registerNetworkCallback(int type, @Nullable String tag) {
        synchronized(isRegisterCloudSimNetworkCallbackLock){

            JLog.logd("NetSpeedCtrlLog: registerCloudSimNetworkCallback:  isRegister = "+ isRegisterCloudSimNetworkCallback +
                    ", " + (cloudSimNetworkCallback==null? "null" : "NotNull" ));

            if(isRegisterCloudSimNetworkCallback){
                return;
            }
            isRegisterCloudSimNetworkCallback = true;

            ConnectivityManager cm = (ConnectivityManager) ServiceManager.appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest netRequest = mNetworkRequest;
            if(netRequest==null){
                netRequest = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();
                mNetworkRequest = netRequest;
            }

            ConnectivityManager.NetworkCallback callback = cloudSimNetworkCallback;
            if(callback == null){
                callback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        super.onAvailable(network);
                    }

                    @Override
                    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                        super.onLinkPropertiesChanged(network, linkProperties);
                        synchronized (listCallbackOpLock){
                            for(ConnectivityManager.NetworkCallback networkCallback : listCallback){
                                networkCallback.onLinkPropertiesChanged(network, linkProperties);
                            }
                        }
                    }
                };
                cloudSimNetworkCallback = callback;
            }
            cm.registerNetworkCallback(netRequest, callback);
        }

    }

    @Override
    public void unRegisterNetworkCallback(int type, @Nullable String tag) {
        synchronized (isRegisterCloudSimNetworkCallbackLock) {
            JLog.logd("NetSpeedCtrlLog: unregisterCloudSimNetworkCallback:  isRegister = " + isRegisterCloudSimNetworkCallback +
                    ", " + (cloudSimNetworkCallback == null ? "Null" : "NotNull"));
            try {
                if (isRegisterCloudSimNetworkCallback && cloudSimNetworkCallback != null) {
                    ((ConnectivityManager) ServiceManager.appContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                            .unregisterNetworkCallback(cloudSimNetworkCallback);
                }
            } catch (Exception e) {
                JLog.loge("NetSpeedCtrlLog: unregisterCloudSimNetworkCallback: FAILED: " + e.getMessage());
            }
        }
    }

    @Override
    public void addNetworkCallbackListener(@NotNull ConnectivityManager.NetworkCallback networkCallback) {
        if(networkCallback==null){
            return;
        }
        synchronized (listCallbackOpLock){
            listCallback.remove(networkCallback);
            listCallback.add(networkCallback);
        }
    }

    @Override
    public void removeNetworkCallbackListener(@NotNull ConnectivityManager.NetworkCallback networkCallback) {
        if(networkCallback==null){
            return;
        }
        synchronized (listCallbackOpLock){
            listCallback.remove(networkCallback);
        }
    }

    @Override
    public void checkDnsOnNewThread() {
        synchronized (lock){
            if(dnsMap==null|| dnsMap.isEmpty()){
                return;
            }
        }
        Observable mObservable = Observable.create(new Observable.OnSubscribe<ArrayList<NetSpeedInfo>>(){
            @Override
            public void call(Subscriber<? super ArrayList<NetSpeedInfo>> subscriber) {
                // 规避多线程dnsMap.remove(key)问题
                HashMap<String, NetSpeedInfo> dnsTempMap = new HashMap<>();
                synchronized (lock){
                    dnsTempMap.putAll(dnsMap);
                    dnsMap.clear();
                }
                ArrayList<NetSpeedInfo> listResult = new ArrayList<>();
                ArrayList<String> listDns = new ArrayList<>();
                Iterator<Map.Entry<String, NetSpeedInfo>> iter = dnsTempMap.entrySet().iterator();
                while (iter.hasNext()) {
                    try{
                        Map.Entry<String, NetSpeedInfo> entry = iter.next();
                        String key = entry.getKey();
                        NetSpeedInfo info = entry.getValue();
                        String address = null;//entry.getValue();
                        String ip = null;
                        if(info!=null && info.isIP && !TextUtils.isEmpty(info.ip)){
                            address = info.ip;
                            InetAddress x = java.net.InetAddress.getByName(address);
                            ip = x.getHostAddress();//得到字符串形式的ip地址
                            if(!TextUtils.isEmpty(ip)){
                                info.ip = ip;
                                listResult.add(info);
                            }
                        }
                        listDns.add(key);
                        JLog.logi("NetSpeedCtrlLog ","service dns("+(address==null?"null":address)+") 转ip："+(ip==null?"null":ip)/*+", run on "+Thread.currentThread()*/);
                    }  catch (Exception e)  {
                        JLog.loge("NetSpeedCtrlLog", "service dns parse Exception: "+e.toString());
                    }
                }

                for(String s : listDns){
                    try{
                        dnsTempMap.remove(s);
                    }catch (Exception e){
                        JLog.loge("NetSpeedCtrlLog dns HashMap remove key("+(s==null?"null":s)+") Exception: "+e.toString());
                    }
                }

                if(!dnsTempMap.isEmpty()){// 剩余的未处理的或处理失败的，重新添加到map中，进行下次重试
                    try{
                        Iterator<Map.Entry<String, NetSpeedInfo>> iterTemp = dnsTempMap.entrySet().iterator();
                        while (iterTemp.hasNext()) {
                            Map.Entry<String, NetSpeedInfo> entry = iterTemp.next();
                            String key = entry.getKey();
                            //NetSpeedInfo info = entry.getValue();
                            putDns(key, entry.getValue());
                        }
                        dnsTempMap.clear();
                    }catch (Exception e){
                        JLog.loge("NetSpeedCtrlLog", "service dns reTry Exception: "+e.toString());
                    }
                }

                subscriber.onNext(listResult);
            }
        });

        mObservable.subscribeOn(Schedulers.newThread()).subscribe(new Subscriber() {
                    @Override
                    public void onCompleted() {
                        if(!isUnsubscribed()){
                            unsubscribe();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if(!isUnsubscribed()){
                            unsubscribe();
                        }
                    }

                    @Override
                    public void onNext(Object o) {
                        try{
                            if(o!=null && o instanceof ArrayList){
                                ArrayList<NetSpeedInfo> listInfo = (ArrayList<NetSpeedInfo>) o;
                                for(NetSpeedInfo info : listInfo){
                                    if(info!=null && !TextUtils.isEmpty(info.ip)){
                                        configNetSpeedWithIP(info.ip, info.uid, info.isIP, info.isSet
                                                , Configuration.INSTANCE.isEnableBandWidth(), info.txBytes, info.rxBytes);
                                        JLog.logi("NetSpeedCtrlLog", "service after dns to ip, server set ip=" + info.ip);
                                    }
                                }
                            }

                        } catch (Exception e){
                            JLog.loge("NetSpeedCtrlLog", " service dns - object as ArrayList Exception: " + e.toString());
                        } finally {
                            if(!isUnsubscribed()){
                                unsubscribe();
                            }
                        }
                    }
                });
    }

    @Override
    public void configNetSpeedWithIP(@Nullable String ip, int uid, boolean isIp, boolean isSet, boolean isEnable, long txBytes, long rxBytes) {
        if(!isEnable)
            return;
        NetSpeedInfo info = NetSpeedInfo.as(isIp, isSet, uid, ip, txBytes, rxBytes);
        if(!DnsUtils.isDnsStartWithNumber(ip)){
            putDns(ip, info);
            return;
        }

        String bindKey = info.getBindKey();
        if(ipMap.get(bindKey)!=null){
            return;
        }
        ipMap.put(bindKey, info);

        if(isIp){
            if(TextUtils.isEmpty(ip))
                return;
            if(isSet){
                getINetSpeed().setFlowPermitByPassIpstr(ip);
            } else{
                getINetSpeed().clearFlowPermitByPassIpstr(ip);
            }
        } else {
            if(uid <=0 )
                return;
            if(isSet){
                getINetSpeed().setFlowPermitByPassUid(uid);
            } else {
                getINetSpeed().clearFlowPermitByPassUid(uid);
            }
        }
    }

    @Override
    public void configNetworkBandWidth(@Nullable final String jsonStr) {
//        //JLog.logi("NetSpeedCtrl","config remote: "+(jsonStr==null?"null":jsonStr));
        Observable mObservable = Observable.create(new Observable.OnSubscribe<ArrayList<NetSpeedInfo>>() {
            @Override
            public void call(Subscriber<? super ArrayList<NetSpeedInfo>> subscriber) {
                subscriber.onNext(parseNetworkBandWidthParam(jsonStr));
            }
        });
        mObservable
                .observeOn(Schedulers.newThread())
                .subscribe(new Subscriber() {
                    @Override
                    public void onCompleted() {
                        if(!isUnsubscribed()){
                            unsubscribe();
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if(!isUnsubscribed()){
                            unsubscribe();
                        }
                    }

                    @Override
                    public void onNext(Object o) {
                        try{
                            if(o!=null && o instanceof ArrayList){
                                ArrayList<NetSpeedInfo> configs = (ArrayList)o;
                                if(configs.size() <= 0) return;

                                String bindKey;
                                for(NetSpeedInfo info : configs){
                                    if(ipMap.get(bindKey = info.getBindKey())!=null) continue;

                                    ipMap.put(bindKey, info);
                                    //JLog.logi("SeedNetworkBandWidth","config remote: "+info.toString());
                                    if(info.isIP){
                                        if(TextUtils.isEmpty(info.ip)) continue;
                                        if(info.isSet){
                                            getINetSpeed().setFlowPermitByPassIpstr(info.ip);
                                        } else {
                                            getINetSpeed().clearFlowPermitByPassIpstr(info.ip);
                                        }
                                    } else {
                                        if(info.uid <= 0) continue;
                                        if(info.isSet){
                                            getINetSpeed().setFlowPermitByPassUid(info.uid);
                                        } else {
                                            getINetSpeed().clearFlowPermitByPassUid(info.uid);
                                        }
                                    }
                                }
                            }

                        }catch (Exception e){
                            // doNothing
                        }finally {
                            if(!isUnsubscribed()){
                                unsubscribe();
                            }
                        }
                    }
                });
    }

    private ArrayList<NetSpeedInfo> parseNetworkBandWidthParam(String jsonStr){
        JLog.logi("NetSpeedCtrlLog","config parse: "+(jsonStr==null?"null":jsonStr));
        if(TextUtils.isEmpty(jsonStr))
            return null;
        try{
            ArrayList<NetSpeedInfo> list = new ArrayList<>();
            Gson gson = new Gson();
            JsonParser jsonParser = new JsonParser();
            JsonArray jsonArray = (JsonArray)jsonParser.parse(jsonStr);
            Iterator<JsonElement> iterator = jsonArray.iterator();
            while (iterator.hasNext()){
                JsonObject jsonObject = (JsonObject) iterator.next();
                NetSpeedInfo info = new NetSpeedInfo();
                info.isIP = gson.fromJson(jsonObject.get("BAND_WIDTH_IS_IP"), Boolean.class);
                info.isSet = gson.fromJson(jsonObject.get("BAND_WIDTH_IS_SET"), Boolean.class);
                info.uid = gson.fromJson(jsonObject.get("BAND_WIDTH_UID"), Integer.class);
                info.ip = gson.fromJson(jsonObject.get("BAND_WIDTH_IP"), String.class);
                info.txBytes = gson.fromJson(jsonObject.get("BAND_WIDTH_TX_BYTES"), Long.class);
                info.rxBytes = gson.fromJson(jsonObject.get("BAND_WIDTH_RX_BYTES"), Long.class);

                list.add(info);
                JLog.logi("NetSpeedCtrlLog","config parse result: "+info.toString());
            }

            return list;

        } catch (Exception e){
            JLog.loge("NetSpeedCtrlLog","parse json exception: "+e.toString());
        }

        return null;

    }

}

package com.ucloudlink.refact.business.flow.netlimit.common;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ucloudlink.framework.flow.ISeedCardNetCtrl;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.LocalPackageInfo;
import com.ucloudlink.refact.channel.enabler.IDataEnabler;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.ucloudlink.refact.ServiceManager.systemApi;

/**
 * Created by jianguo.he on 2018/1/11.
 */

public class SeedCardNetLimitHolder {

    public interface OnUiSuppordSeedNetLimitChangeListener{

        void onUiSupportSeedNetLimitChange(boolean isUiSupportSeedNetworkLimitChange);
    }

    public interface OnIfNameAddListener{
        void onNewIfNameAddCallback(String ifName);
    }


    private static final String SP_KEY_IFNAMES = "SEED_NET_IFNAMES";

    private static SeedCardNetLimitHolder instance;
    private boolean isInit = false;

    //---  新种子卡方案需要的数据   begin --------
    private ConcurrentHashMap<String, String> mapIfName  = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, SeedCardNetInfo> mapIP= new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, SeedCardNetInfo> mapDns  = new ConcurrentHashMap<>();
    //---  新种子卡方案需要的数据   end   --------
    private final byte[] mapIfNameLock = new byte[0];
    private final byte[] mapIPLock = new byte[0];
    private final byte[] mapDnsLock = new byte[0];

    private boolean mIsExistIfNameExtra = false; // 网络广播是否带有ifName参数
    private boolean mIsUiSupportSeedNetworkLimitByUidAndIp = false;// ui 是否支持新种子卡网络限制方案
    private boolean mIsInRestrictAllNetworks = false;// 是否处于种子卡网络限制状态

    private ArrayList<CardStateMonitor.NetworkStateListen> listNetworkStateListen = new ArrayList<>();
    private final byte[] netStateListenerListLock = new byte[0];
    private ArrayList<OnUiSuppordSeedNetLimitChangeListener> listUiSuppordSeedNetLimitChangeListener = new ArrayList<>();
    private final byte[] uiSuppordSeedNetLimitLock = new byte[0];
    private List<LocalPackageInfo> listLocalPackageInfos = new ArrayList<>();// MIUI 需要的数据
    private final byte[] listLocalPackageInfosLock = new byte[0];
    private ArrayList<OnIfNameAddListener> listOnIfNameAddListener = new ArrayList<>();
    private final byte[] ifNameAddListenerLock = new byte[0];

    private ISeedCardNetCtrl mISeedCardNetCtrl;// 种子卡网络限制接口实现类
    private IDataEnabler seedSimEnable;
    private IDataEnabler cloudSimEnabler;

    private SeedCardNetLimitHolder(){
    }

    public static SeedCardNetLimitHolder getInstance(){
        if (instance == null){
            synchronized (SeedCardNetLimitHolder.class){
                if(instance == null){
                    instance = new SeedCardNetLimitHolder();
                }
            }
        }
        return instance;
    }

    public void init(IDataEnabler seedSimEnable, IDataEnabler cloudSimEnabler){
        if(!isInit){
            synchronized (SeedCardNetLimitHolder.class){
                if(!isInit){
                    isInit = true;
                    restoreIfNameFromSP();

                    this.seedSimEnable = seedSimEnable;
                    this.cloudSimEnabler = cloudSimEnabler;

                    ServiceManager.simMonitor.addNetworkStateListen(networkListen);

                    createAndInitISeedCardNetCtrl();
                }
            }
        }
    }

    private void createAndInitISeedCardNetCtrl(){
        mISeedCardNetCtrl = systemApi.getISeedCardNetCtrl(isExistIfNameExtra(), isUiSupportSeedNetworkLimitByUidAndIp());
        mISeedCardNetCtrl.initState(seedSimEnable, cloudSimEnabler);
    }

    private CardStateMonitor.NetworkStateListen networkListen = new CardStateMonitor.NetworkStateListen(){

        @Override
        public void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
            JLog.logi("SeedCardNetLog, CardStateMonitor.NetworkStateListen: NetworkStateChange..   ddsId = " + ddsId + ", state = " + state
                    + ", type = " + type + ", ifName = " + ifName + ", isExistIfNameExtra = " + isExistIfNameExtra + ", subId = " + subId);
            boolean isExistIfName = true;
            if (isExistIfNameExtra) {
                if (type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_MOBILE_DUN) {
                    mIsExistIfNameExtra = isExistIfNameExtra;
                    if (!TextUtils.isEmpty(ifName)) {
                        isExistIfName = mapIfName.contains(ifName);
                    }
                    putIfName(ifName);
                }
            }

            if(!isExistIfName){// Note: 注意调用顺序 listOnIfNameAddListener -> listNetworkStateListen
                for(OnIfNameAddListener l: listOnIfNameAddListener){
                    l.onNewIfNameAddCallback(ifName);
                }
            }

            for(CardStateMonitor.NetworkStateListen listener : listNetworkStateListen){
                listener.NetworkStateChange(ddsId, state, type, ifName, isExistIfNameExtra, subId);
            }
        }
    };

    public boolean isInRestrictAllNetworks(){
        return mIsInRestrictAllNetworks;
    }

    public void setRetrict(String tag){
        mIsInRestrictAllNetworks = true;
        mISeedCardNetCtrl.setRestrictAllNet(tag);
    }

    public void resetRetrict(String tag){
        mIsInRestrictAllNetworks = false;
        mISeedCardNetCtrl.clearRestrictAllRuleNet(tag);
    }

    public void addNetworkStateListen(CardStateMonitor.NetworkStateListen l){
        synchronized (netStateListenerListLock){
            listNetworkStateListen.remove(l);
            listNetworkStateListen.add(l);
        }
    }

    public void removeNetworkStateListen(CardStateMonitor.NetworkStateListen l){
        synchronized (netStateListenerListLock){
            listNetworkStateListen.remove(l);
        }
    }

    public void addUiSuppordSeedNetLimitChangeListener(OnUiSuppordSeedNetLimitChangeListener l){
        synchronized (uiSuppordSeedNetLimitLock){
            listUiSuppordSeedNetLimitChangeListener.remove(l);
            listUiSuppordSeedNetLimitChangeListener.add(l);
        }
    }

    public void removeUiSuppordSeedNetLimitChangeListener(OnUiSuppordSeedNetLimitChangeListener l){
        synchronized (uiSuppordSeedNetLimitLock){
            listUiSuppordSeedNetLimitChangeListener.remove(l);
        }
    }

    public void addOnIfNameAddListener(OnIfNameAddListener l){
        synchronized (ifNameAddListenerLock){
            listOnIfNameAddListener.remove(l);
            listOnIfNameAddListener.add(l);
        }
    }

    public void removeOnIfNameAddListener(OnIfNameAddListener l){
        synchronized (ifNameAddListenerLock){
            listOnIfNameAddListener.remove(l);
        }
    }

    /**  */
    public List<LocalPackageInfo> getCopyListLocalPackageInfos(){
        List<LocalPackageInfo> temp = new ArrayList<>();
        synchronized (listLocalPackageInfosLock){
            temp.addAll(listLocalPackageInfos);
        }
        return temp;
    }

    public void setListLocalPackageInfos(List<LocalPackageInfo> list){
        synchronized (listLocalPackageInfosLock){
            listLocalPackageInfos.clear();
            if(list!=null && list.size() > 0){
                listLocalPackageInfos.addAll(list);
            }
        }
    }

    /**
     * 是否是通过广播接收到的接口名称, 用来区分Framework是否是老版本,
     * 如果返回false则使用反射方法判断一下Framework是否是老版本
     */
    public boolean isExistIfNameExtra(){
        if(!mIsExistIfNameExtra){
            mIsExistIfNameExtra = systemApi.isFrameworkSupportSeedNetworkLimitByUidAndIp();
        }
        return mIsExistIfNameExtra;
    }

    public boolean isUiSupportSeedNetworkLimitByUidAndIp()  {
        return mIsUiSupportSeedNetworkLimitByUidAndIp;
    }

    public void setUiSupportSeedNetworkLimitByUidAndIp(boolean isUiSupportSeedNetworkLimitByUidAndIp) {
        if(mIsUiSupportSeedNetworkLimitByUidAndIp != isUiSupportSeedNetworkLimitByUidAndIp){
            mISeedCardNetCtrl.clearRestrictAllRuleNet("setUiSupSeedNet..() - change");
            mIsUiSupportSeedNetworkLimitByUidAndIp = isUiSupportSeedNetworkLimitByUidAndIp;
            synchronized (uiSuppordSeedNetLimitLock){
                for(OnUiSuppordSeedNetLimitChangeListener l : listUiSuppordSeedNetLimitChangeListener){
                    l.onUiSupportSeedNetLimitChange(mIsUiSupportSeedNetworkLimitByUidAndIp);
                }
            }
            createAndInitISeedCardNetCtrl();
            if(isInRestrictAllNetworks()){
                mISeedCardNetCtrl.setRestrictAllNet("setUiSupSeedNet..() - change");
            }
        }
        JLog.logi("SeedCardNetLog, setUiSupSeedNet..(): mIsUiSupSeedNet.. = " + mIsUiSupportSeedNetworkLimitByUidAndIp);
    }

    private void restoreIfNameFromSP(){
        try{
            String ifNameStr = SharedPreferencesUtils.getString(ServiceManager.appContext, SP_KEY_IFNAMES, "");
            JLog.logi("SeedCardNetLog, restoreIfNameFromSP -> ifNameStr " + (ifNameStr ==null ? "null":ifNameStr));
            if(!TextUtils.isEmpty(ifNameStr)){
                Gson gson = new Gson();
                Collection<String> mCollection = gson.fromJson(ifNameStr, new TypeToken<Collection<String>>(){}.getType());
                if(mCollection!=null && mCollection.size() > 0){
                    synchronized (mapIfNameLock){
                        for(String ifName: mCollection){
                            if(!TextUtils.isEmpty(ifName)){
                                mapIfName.put(ifName, ifName);
                            }
                        }
                    }
                }
            }
        }catch (Exception e){
            JLog.logi("SeedCardNetLog, restoreIfNameFromSP -> Exception = " + e.toString());
            e.printStackTrace();
        }
    }

    public void putIfName(final String ifName){
        if(TextUtils.isEmpty(ifName)){
            return;
        }
        synchronized (mapIfNameLock){
            boolean isExistIfName = mapIfName.contains(ifName);
            mapIfName.put(ifName, ifName);

            if(!isExistIfName){
                ServiceManager.appThreadPool.submit(new HandlerIfNameRunnable(ifName));
            }
        }
    }

    private class HandlerIfNameRunnable implements Runnable {

        private String ifName;

        private HandlerIfNameRunnable(String ifName){
            this.ifName = ifName;
        }
        @Override
        public void run() {
            for(OnIfNameAddListener l: listOnIfNameAddListener){
                try{
                    l.onNewIfNameAddCallback(ifName);
                }catch (Exception e){
                    JLog.logi("SeedCardNetLog, putIfName() -> ifName Callback Exception: " + e.toString());
                    e.printStackTrace();
                }
            }

            try{
                Gson gson = new Gson();
                String ifNameStr = gson.toJson(mapIfName.values());
                if(ifNameStr == null){
                    ifNameStr = "";
                }
                SharedPreferencesUtils.putString(ServiceManager.appContext, SP_KEY_IFNAMES, ifNameStr);

                JLog.logi("SeedCardNetLog, putIfName() -> ifNameStr: " + ifNameStr);

            }catch (Exception e){
                JLog.logi("SeedCardNetLog, putIfName() -> Gson..Exception: " + e.toString());
                e.printStackTrace();
            }
        }
    }

    public HashMap<String, String> getCopyMapIfName(){
        HashMap<String, String> tempMap = new HashMap<>();
        tempMap.putAll(mapIfName);
        return tempMap;
    }

    public HashMap<String, SeedCardNetInfo> getCopyMapIP(){
        HashMap<String, SeedCardNetInfo> tempMap = new HashMap<>();
        tempMap.putAll(mapIP);
        return tempMap;
    }

    public HashMap<String, SeedCardNetInfo> getCopyMapDns(){
        HashMap<String, SeedCardNetInfo> tempMap = new HashMap<>();
        tempMap.putAll(mapDns);
        return tempMap;
    }

    public void configDnsOrIp(final SeedCardNetInfo info){
        if(!TextUtils.isEmpty(info.vl) /*&& info.uid > 0*/){
            ServiceManager.appThreadPool.submit(new Runnable() {
                @Override
                public void run() {
                    if(DnsUtils.isDnsStartWithNumber(info.vl)){
                        configIP(info);
                    } else {
                        configDns(info);
                    }

                    if(mISeedCardNetCtrl!=null){
                        mISeedCardNetCtrl.configDnsOrIp(info);
                    }
                }
            });
        }

    }

    public void configDnsOrIp(final ArrayList<SeedCardNetInfo> list){
        if(list!=null && list.size() > 0){
            ServiceManager.appThreadPool.submit(new Runnable() {
                @Override
                public void run() {
                    for(SeedCardNetInfo info: list){
                        if(info!=null && !TextUtils.isEmpty(info.vl) /*&& info.uid > 0*/){
                            if(DnsUtils.isDnsStartWithNumber(info.vl)){
                                configIP(info);
                            } else {
                                configDns(info);
                            }

                            if(mISeedCardNetCtrl!=null){
                                mISeedCardNetCtrl.configDnsOrIp(info);
                            }
                        }
                    }
                }
            });
        }
    }

    private void configIP(SeedCardNetInfo info){
        if(info.enable){
            putIP(info);
        } else {
            removeIP(info);
        }
    }

    private void putIP(SeedCardNetInfo info){
        String cacheKey = getCacheKey(info);
        if(TextUtils.isEmpty(cacheKey)){
            return;
        }
        synchronized (mapIPLock){
            mapIP.put(cacheKey, info);
        }

    }

    private void removeIP(SeedCardNetInfo info) {
        String cacheKey = getCacheKey(info);
        if (TextUtils.isEmpty(cacheKey)) {
            return;
        }
        synchronized (mapIPLock){
            mapIP.remove(cacheKey);
        }
    }

    private void configDns(SeedCardNetInfo info){
        if(info.enable){
            putDns(info);
        } else {
            removeDns(info);
        }
    }

    private void putDns(SeedCardNetInfo info) {
        String cacheKey = getCacheKey(info);
        if (TextUtils.isEmpty(cacheKey)) {
            return;
        }
        synchronized (mapDnsLock){
            mapDns.put(cacheKey, info);
        }
    }

    private void removeDns(SeedCardNetInfo info) {
        String cacheKey = getCacheKey(info);
        if (TextUtils.isEmpty(cacheKey)) {
            return;
        }
        synchronized (mapDnsLock){
            mapDns.remove(cacheKey);
        }
    }

    public static String getCacheKey(SeedCardNetInfo info) {
        if(!TextUtils.isEmpty(info.vl)/* && info.uid > 0*/){
            return info.vl + "_" + info.uid;
        }
        return "";
    }

}

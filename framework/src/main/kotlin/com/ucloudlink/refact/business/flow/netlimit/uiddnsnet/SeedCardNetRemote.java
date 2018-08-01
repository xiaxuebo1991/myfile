package com.ucloudlink.refact.business.flow.netlimit.uiddnsnet;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.netlimit.common.DnsUtils;
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo;
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder;
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.business.routetable.RouteTableManager;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.channel.enabler.IDataEnabler;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * Created by jianguo.he on 2017/11/22.
 */

public class SeedCardNetRemote {

    public static String LAST_SEED_NETWORK_LIMIT_BY_UID_STR = "LAST_SEED_NETWORK_LIMIT_BY_UID_STR";
    public static String LAST_SEED_NETWORK_LIMIT_BY_UID_STR_EXC = "LAST_SEED_NETWORK_LIMIT_BY_UID_STR_EXC";// 上次是否设置uid访问网络

    private static String LAST_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR = "LAST_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR";
    private static String UI_SUPPORT_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR = "UI_SUPPORT_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR";



    public static void init(final IDataEnabler seedSimEnable, final IDataEnabler cloudSimEnabler) {
        ServiceManager.appThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                setUiSupportSeedNetworkLimitByUidAndIp(String.valueOf(SharedPreferencesUtils.getBoolean(ServiceManager.appContext
                        , SeedCardNetRemote.UI_SUPPORT_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR, false)));

                long beginTimeMillis = System.currentTimeMillis();

                SeedCardNetLimitHolder.getInstance().init(seedSimEnable, cloudSimEnabler);

                HashMap<String, String> mapIfName = SeedCardNetLimitHolder.getInstance().getCopyMapIfName();
                Iterator<Map.Entry<String, String>> iterator = mapIfName.entrySet().iterator();
                while (iterator.hasNext()){
                    Map.Entry<String, String> entry = iterator.next();
                    try{
                        FlowBandWidthControl.getInstance().getSeedCardNetManager().getISeedCardNet().removeRestrictAllNetworks(entry.getValue());
                    }catch (Exception e){
                        JLog.logi("SeedCardNetLog, SeedCardNetRemote.init.submit() -> remove Exception = " + e.toString());
                        e.printStackTrace();
                    }
                    try{
                        FlowBandWidthControl.getInstance().getSeedCardNetManager().getISeedCardNet().clearRestrictAllRule(entry.getValue());
                    }catch (Exception e){
                        JLog.logi("SeedCardNetLog, SeedCardNetRemote.init.submit() -> clear Exception = " + e.toString());
                        e.printStackTrace();
                    }
                }

                int mUServiceUid = SysUtils.getUServiceUid();
                String jsonStr = SharedPreferencesUtils.getString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR, "");
                final ArrayList<SeedCardNetInfo> list = filterParseSeedCardNetInfoList(parseAsSeedCardNetInfoList(jsonStr));

                list.add(new SeedCardNetInfo(true, DnsUtils.getDnsOrIp(Configuration.INSTANCE.getTIME_URL()), mUServiceUid));
                list.add(new SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ServerRouter.ROUTE_IP_SAAS2), mUServiceUid));
                list.add(new SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ServerRouter.ROUTE_IP_SAAS3), mUServiceUid));
                list.add(new SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ServerRouter.ROUTE_IP_BUSSINESS), mUServiceUid));
                list.add(new SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ServerRouter.ROUTE_IP_BUSSINESS_BACKUP), mUServiceUid));

                ArrayList<String> listA = new ArrayList<>();
                listA = addListB2A(listA, RouteTableManager.INSTANCE.getLocalAssIPList(ServerRouter.BUSINESS));
                listA = addListB2A(listA, RouteTableManager.INSTANCE.getLocalAssIPList(ServerRouter.SAAS2));
                listA = addListB2A(listA, RouteTableManager.INSTANCE.getLocalAssIPList(ServerRouter.SAAS3));
                listA = addListB2A(listA, RouteTableManager.INSTANCE.getLocalAssIPList(ServerRouter.FACTORY));

                for(String ip : listA){
                    if(!TextUtils.isEmpty(ip)){
                        JLog.logd("tRoute SeedCardNetRemote.init -> open net limit ip = "+ip);
                        list.add(new SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ip), mUServiceUid));
                    }
                }

                SeedCardNetLimitHolder.getInstance().configDnsOrIp(list);

//                parseSeedNetworkLimitByUidAndIp(SharedPreferencesUtils.getString(ServiceManager.appContext
//                        , SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR, ""));

                JLog.logi("SeedCardNetLog, SeedCardNetRemote.init.submit() -> ExcuteTimeMillis = " + (System.currentTimeMillis() - beginTimeMillis));
            }
        });
    }

    private static ArrayList<String> addListB2A(ArrayList<String> listA, List<String> listB){
        if(listA!=null && listB!=null && listB.size() > 0){
            listA.addAll(listB);
        }
        return listA;
    }

    public static boolean isRomSupportSeedNetworkLimitByUidAndIp() {
        return SeedCardNetLimitHolder.getInstance().isExistIfNameExtra();
    }

    public static int setUiSupportSeedNetworkLimitByUidAndIp(String str) {
        if (!TextUtils.isEmpty(str)) {
            try {
                boolean ret = Boolean.parseBoolean(str);
                SeedCardNetLimitHolder.getInstance().setUiSupportSeedNetworkLimitByUidAndIp(ret);
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, SeedCardNetRemote.UI_SUPPORT_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR, ret);
                return 0;
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * 清除uid限制网络访问规则
     */
    public static void clearSeedNetworkLimit() {
        if (SharedPreferencesUtils.getBoolean(ServiceManager.appContext, LAST_SEED_NETWORK_LIMIT_BY_UID_STR_EXC, false)) {
            resetLastSeedNetworkLimitByUid();
        }
    }

    /**
     * 重置上次设置的uid对种子卡访问的限制
     */
    public static void resetLastSeedNetworkLimitByUid() {
        String lastSeedNetworkLimitStr = SharedPreferencesUtils.getString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_STR, "");
        if (TextUtils.isEmpty(lastSeedNetworkLimitStr))
            return;

        try {
            Gson gson = new Gson();
            JsonParser jsonParser = new JsonParser();
            JsonArray jsonArray = (JsonArray) jsonParser.parse(lastSeedNetworkLimitStr);
            Iterator<JsonElement> iterator = jsonArray.iterator();
            ArrayList<Integer> falseList = new ArrayList<>();

            while (iterator.hasNext()) {
                JsonObject jsonObject = (JsonObject) iterator.next();
                boolean enable = gson.fromJson(jsonObject.get("enable"), Boolean.class);
                int uid = gson.fromJson(jsonObject.get("uid"), Integer.class);

                falseList.add(uid);

                JLog.logi("SeedNetworkLimit", " resetLastSeedNetworkLimitByUid -> parse result: [" + "enable=" + enable + ", uid=" + uid + "]");
            }

            // 将列表数据设置到filter表
            int falseSize = falseList.size();
            if (falseSize > 0) {
                int[] falseIntArray = new int[falseSize];
                for (int i = 0; i < falseSize; i++) {
                    falseIntArray[i] = falseList.get(i);
                }
                FlowBandWidthControl.getInstance().getSeedCardNetManager().getIUidSeedCardNet().setAllUserRestrictAppsOnDataByPass(0, falseIntArray);
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, LAST_SEED_NETWORK_LIMIT_BY_UID_STR_EXC, false);
            }
        } catch (Exception e) {
            JLog.loge("SeedNetworkLimit", "resetLastSeedNetworkLimitByUid -> parse json exception: " + e.toString());
        }

    }

    /**
     * 设置限制使用种子卡上网
     *
     * @param jsonStr
     */
    private static int setSeedNetworkLimitByUid(String jsonStr) {
        JLog.logi("SeedNetworkLimit - setSeedNetworkLimitByUid() param: " + (jsonStr == null ? "null" : jsonStr));
        if (TextUtils.isEmpty(jsonStr))
            return -1;
        try {
            Gson gson = new Gson();
            JsonParser jsonParser = new JsonParser();
            JsonArray jsonArray = (JsonArray) jsonParser.parse(jsonStr);
            Iterator<JsonElement> iterator = jsonArray.iterator();

            ArrayList<Integer> trueList = new ArrayList<>();
            ArrayList<Integer> falseList = new ArrayList<>();

            while (iterator.hasNext()) {
                JsonObject jsonObject = (JsonObject) iterator.next();
                boolean enable = gson.fromJson(jsonObject.get("enable"), Boolean.class);
                int uid = gson.fromJson(jsonObject.get("uid"), Integer.class);

                if (enable) {
                    trueList.add(uid);
                } else {
                    falseList.add(uid);
                }
                JLog.logi("SeedNetworkLimit", "setSeedNetworkLimitByUid -> parse result: [" + "enable=" + enable + ", uid=" + uid + "]");
            }

            // 将列表数据设置到filter表
            int trueSize = trueList.size();
            if (trueSize > 0) {
                int[] trueIntArray = new int[trueSize];
                for (int i = 0; i < trueSize; i++) {
                    trueIntArray[i] = trueList.get(i);
                }
                FlowBandWidthControl.getInstance().getSeedCardNetManager().getIUidSeedCardNet().setAllUserRestrictAppsOnDataByPass(1, trueIntArray);
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, LAST_SEED_NETWORK_LIMIT_BY_UID_STR_EXC, true);
            }

            int falseSize = falseList.size();
            if (falseSize > 0) {
                int[] falseIntArray = new int[falseSize];
                for (int i = 0; i < trueSize; i++) {
                    falseIntArray[i] = falseList.get(i);
                }
                // 0, 会删除掉filter表中的规则数据，所以可以不用保存到sp中
                FlowBandWidthControl.getInstance().getSeedCardNetManager().getIUidSeedCardNet().setAllUserRestrictAppsOnDataByPass(0, falseIntArray);
            }
            JLog.logi("SeedNetworkLimit", "setSeedNetworkLimitByUid -> json parse success!");
            return 1;

        } catch (Exception e) {
            JLog.loge("SeedNetworkLimit", "setSeedNetworkLimitByUid -> json parse exception: " + e.toString());
        }

        return -1;

    }

    /**
     * 解析从UI过来的配置限制UI使用种子卡网络
     *
     * @param jsonStr
     */
    public static int parseSeedNetworkLimitByUid(String jsonStr) {
        JLog.logi("SeedNetworkLimit - parseSeedNetworkLimitByUid() param: " + (jsonStr == null ? "null" : jsonStr));
        if (TextUtils.isEmpty(jsonStr))
            return -1;
        String lastSeedNetworkLimitStr = SharedPreferencesUtils.getString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_STR, "");
        if (!TextUtils.isEmpty(lastSeedNetworkLimitStr)) {
            resetLastSeedNetworkLimitByUid();
        }
        int ret = setSeedNetworkLimitByUid(jsonStr);
        SharedPreferencesUtils.putString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_STR, jsonStr);
        return ret;
    }

    /**
     * 设置uid访问种子卡网络
     */
    public static int setSeedNetworkLimit() {
        String jsonStr = SharedPreferencesUtils.getString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_STR, "");
        JLog.logi("SeedNetworkLimit - setSeedNetworkLimit() jsonStr: " + (jsonStr == null ? "null" : jsonStr));
        if (TextUtils.isEmpty(jsonStr))
            return -1;
        return setSeedNetworkLimitByUid(jsonStr);
    }

    private static ArrayList<SeedCardNetInfo> filterParseSeedCardNetInfoList(ArrayList<SeedCardNetInfo> listParams){
        ArrayList<SeedCardNetInfo> listRet = new ArrayList<>();
        if (listParams!=null && listParams.size() > 0) {
            for (SeedCardNetInfo info : listParams) {
                if (info == null || TextUtils.isEmpty(info.vl) /*|| info.uid <=0 */) {
                    continue;
                }
                String dns = DnsUtils.getDnsOrIp(info.vl);
                if (!TextUtils.isEmpty(dns)) {
                    listRet.add(info);
                    //SeedCardNetLimitHolder.getInstance().configDnsOrIp(info);
                }
            }

        }
        return listRet;
    }

    private static ArrayList<SeedCardNetInfo> parseAsSeedCardNetInfoList(String jsonStr){
        ArrayList<SeedCardNetInfo> list = new ArrayList<>();
        if(!TextUtils.isEmpty(jsonStr)){
            try {
                Gson gson = new Gson();
                JsonParser jsonParser = new JsonParser();
                JsonArray jsonArray = (JsonArray) jsonParser.parse(jsonStr);
                Iterator<JsonElement> iterator = jsonArray.iterator();
                while (iterator.hasNext()) {
                    JsonObject jsonObject = (JsonObject) iterator.next();
                    boolean enable = gson.fromJson(jsonObject.get("enable"), Boolean.class);
                    String dnsOrIp = gson.fromJson(jsonObject.get("dnsOrIp"), String.class);
                    String packageName = gson.fromJson(jsonObject.get("packageName"), String.class);
                    dnsOrIp = DnsUtils.getDnsOrIp(dnsOrIp);
                    if (!TextUtils.isEmpty(dnsOrIp) && !TextUtils.isEmpty(packageName)) {
                        list.add(new SeedCardNetInfo(enable, dnsOrIp, SysUtils.getAppUid(packageName)));
                    }
                    JLog.logi("SeedCardNetLog", "config parse result: {enable:" + enable + ", dnsOrIp=" + (dnsOrIp == null ? "null" : dnsOrIp) + "}");
                }

            } catch (Exception e) {
                JLog.loge("SeedCardNetLog", "parse exception: " + e.toString());
            }
        }
        return list;

    }

    public static int parseSeedNetworkLimitByUidAndIp(final String jsonStr) {
        JLog.logi("SeedCardNetLog - parseSeedNetworkLimitByUidAndIp() param: " + (jsonStr == null ? "null" : jsonStr));
        SharedPreferencesUtils.putString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_AND_IP_STR, jsonStr);
        if (TextUtils.isEmpty(jsonStr)) {
            return -1;
        }

        Observable mObservable = Observable.create(new Observable.OnSubscribe<ArrayList<SeedCardNetInfo>>() {
            @Override
            public void call(Subscriber<? super ArrayList<SeedCardNetInfo>> subscriber) {
                subscriber.onNext(parseAsSeedCardNetInfoList(jsonStr));
            }
        });

        mObservable
                .observeOn(Schedulers.newThread())
                .subscribe(new Subscriber() {
                    @Override
                    public void onCompleted() {
                        if (!isUnsubscribed()) {
                            unsubscribe();
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (!isUnsubscribed()) {
                            unsubscribe();
                        }
                    }

                    @Override
                    public void onNext(Object o) {
                        try {
                            if (o != null && o instanceof ArrayList) {
                                ArrayList<SeedCardNetInfo> configs = (ArrayList) o;
                                if (configs.size() <= 0) {
                                    return;
                                }
                                /*ArrayList<SeedCardNetInfo> listRet = new ArrayList<>();
                                for (SeedCardNetInfo info : configs) {
                                    if (info == null || TextUtils.isEmpty(info.vl) *//*|| info.uid <=0 *//*) {
                                        continue;
                                    }
                                    String dns = DnsUtils.getDnsOrIp(info.vl);
                                    if (!TextUtils.isEmpty(dns)) {
                                        listRet.add(info);
                                        //SeedCardNetLimitHolder.getInstance().configDnsOrIp(info);
                                    }
                                }*/
                                ArrayList<SeedCardNetInfo> listRet = filterParseSeedCardNetInfoList(configs);
                                SeedCardNetLimitHolder.getInstance().configDnsOrIp(listRet);

                            }

                        } catch (Exception e) {
                            // doNothing
                        } finally {
                            if (!isUnsubscribed()) {
                                unsubscribe();
                            }
                        }
                    }
                });

        return 0;
    }
}

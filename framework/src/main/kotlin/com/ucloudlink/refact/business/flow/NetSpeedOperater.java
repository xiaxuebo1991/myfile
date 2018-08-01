package com.ucloudlink.refact.business.flow;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder;
import com.ucloudlink.refact.utils.JLog;

/**
 * Created by jianguo.he on 2017/9/26.
 * 种子卡限制网络访问操作类
 */

public class NetSpeedOperater {

    public static void setRetrict(final String tag) {
        ServiceManager.appThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                JLog.logd("SeedCardNetLog setRetrict tag=" + (tag == null ? "null" : tag));
                SeedCardNetLimitHolder.getInstance().setRetrict(tag);
            }
        });

//        boolean isExistIfNameExtra = ServiceManager.mISeedCardNetCtrl.isExistIfNameExtra();
//        boolean isUiSupportSeedNetworkLimitByUidAndIp = ServiceManager.mISeedCardNetCtrl.isUiSupportSeedNetworkLimitByUidAndIp();
//        JLog.logd("SeedCardNet setRetrict tag = " + (tag==null ? "null" : tag)
//                + ", isExistIfNameExtra = " + isExistIfNameExtra + ", isUiSupportSeedNetworkLimitByUidAndIp = " + isUiSupportSeedNetworkLimitByUidAndIp);
//
//        if(isExistIfNameExtra && isUiSupportSeedNetworkLimitByUidAndIp){
//            ServiceManager.mISeedCardNetCtrl.setRestrictAllNet(tag);
//        } else {
//            SeedCardNetRemote.setSeedNetworkLimit();
//            FlowBandWidthControl.getInstance().getSeedCardNetManager().setLocalPackagesRestrict();// 设置后uid > 10000的都不能上网
//        }

    }

    public static void resetRetrict(final String tag) {
        ServiceManager.appThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                SeedCardNetLimitHolder.getInstance().resetRetrict(tag);
                JLog.logd("SeedCardNetLog resetRetrict tag=" + (tag == null ? "null" : tag));

//        SeedCardNetRemote.clearSeedNetworkLimit(); // 旧方案清除uid可访问种子卡网络, 可能还未接受到IfName广播, 所以都调一下
//        if(ServiceManager.mISeedCardNetCtrl.isExistIfNameExtra()
//                && ServiceManager.mISeedCardNetCtrl.isUiSupportSeedNetworkLimitByUidAndIp()){ // 新方案，则将旧方案清除掉
//
//            SharedPreferencesUtils.putString(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_STR, "");
//            SharedPreferencesUtils.putBoolean(ServiceManager.appContext, SeedCardNetRemote.LAST_SEED_NETWORK_LIMIT_BY_UID_STR_EXC, false);
//        }
//        ServiceManager.mISeedCardNetCtrl.clearRestrictAllRuleNet(tag);

                if (FlowBandWidthControl.getInstance().getSeedCardNetManager().getBwccRestrict()) {
                    FlowBandWidthControl.getInstance().getSeedCardNetManager().restoreLocalPackagesRestrict();
                } else {
                    FlowBandWidthControl.getInstance().getSeedCardNetManager().clearUserRestrict();
                }
            }
        });
    }

}


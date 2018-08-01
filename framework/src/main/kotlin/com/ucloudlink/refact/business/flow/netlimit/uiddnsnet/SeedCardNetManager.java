package com.ucloudlink.refact.business.flow.netlimit.uiddnsnet;

import com.ucloudlink.framework.flow.ISeedCardNet;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.netlimit.uidnet.IUidSeedCardNet;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

/**
 * Created by jianguo.he on 2018/1/9.
 */

public class SeedCardNetManager {

    private boolean  bwccRestrict = false;
    public static final String SAVE_USER_RESTRICT = "restrict";

    private ISeedCardNet mISeedCardNet;
    private final byte[] mISeedCardNetLock = new byte[0];
    private IUidSeedCardNet mIUidSeedCardNet;
    private final byte[] mIUidSeedCardNetLock = new byte[0];

    public ISeedCardNet getISeedCardNet(){
        if(mISeedCardNet==null){
            synchronized (mISeedCardNetLock){
                if(mISeedCardNet==null){
                    mISeedCardNet = ServiceManager.systemApi.getISeedCardNet();
                }
            }
        }
        return mISeedCardNet;
    }

    public IUidSeedCardNet getIUidSeedCardNet(){
        if(mIUidSeedCardNet == null){
            synchronized (mIUidSeedCardNetLock){
                if(mIUidSeedCardNet == null){
                    mIUidSeedCardNet = ServiceManager.systemApi.getIUidSeedCardNet();
                }
            }
        }
        return mIUidSeedCardNet;
    }

    public boolean getBwccRestrict(){
        return bwccRestrict;
    }

    public void setLocalPackagesRestrict(){
        JLog.logd("try to set local packages restrict on data.");

        if (!bwccRestrict){
            getIUidSeedCardNet().setAllUserRestrictAppsOnData();
            saveUserRestrict(true);
            bwccRestrict = true;
        }

        /*
        if (localPackageInfos.size() < 1){
            getLocalPackagesInfo();
        }
        for (int i = 0; i < localPackageInfos.size(); i++){
            LocalPackageInfo lpi =  localPackageInfos.get(i);
            if (!lpi.getRestrict()){
                if (isMiui) {
                    setMobileRestrict(lpi.getPackageName(), true);
                }else{
                    addRestritAppOnData(lpi.getPackageUid());
                }
            }
        }
        if (isMiui) {
            enableMyselfNetworkLink();
        }
        */
    }

    public void restoreLocalPackagesRestrict() {
        JLog.logd("UidSeedCardNetLog, restoreLocalPackagesRestrict ->  bwccRestrict = "+bwccRestrict);

        if(bwccRestrict) {
            getIUidSeedCardNet().resetAllUserRestrictAppsOnData();
            saveUserRestrict(false);
            bwccRestrict = false;
        }
    }

    public void clearUserRestrict(){
        boolean re = SharedPreferencesUtils.getBoolean(ServiceManager.INSTANCE.getAppContext(), SAVE_USER_RESTRICT, false);
        JLog.logd("UidSeedCardNetLog, clearUserRestrict ->  re = "+re);
        if(re){
            getIUidSeedCardNet().resetAllUserRestrictAppsOnData();
            saveUserRestrict(false);
            bwccRestrict = false;
        }
    }

    public void saveUserRestrict(boolean value){
        SharedPreferencesUtils.putBoolean(ServiceManager.INSTANCE.getAppContext(), SeedCardNetManager.SAVE_USER_RESTRICT, value);
    }


}

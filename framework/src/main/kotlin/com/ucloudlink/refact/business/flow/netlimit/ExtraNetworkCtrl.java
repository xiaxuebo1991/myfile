package com.ucloudlink.refact.business.flow.netlimit;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.LocalPackageInfo;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jianguo.he on 2018/1/9.
 */

public class ExtraNetworkCtrl {

    private final String STR_CLOUDSIM_PACKAGE_NAME = "com.ucloudlink.cloudsim";
    private Method mExtraNetworkIsMobileRestrict = null;
    private Method mExtraNetworkSetMobileRestrict = null;
    private List<LocalPackageInfo> localPackageInfos = new ArrayList<LocalPackageInfo>();

    private IExtraNetwork mIExtraNetwork = new ExtraNetworkImpl();

    private boolean isMiui = false;

    public IExtraNetwork getIExtraNetwork(){
        return mIExtraNetwork;
    }

    class  initProcess extends Thread{
        @Override
        public void run(){
            if (ServiceManager.INSTANCE.getSystemType() == Configuration.ANDROID_MIUI_V8){
                isMiui = true;
            }

            if (isMiui) {
                getMiuiRestrictMethod();
            }
        }
    }

    public void init(){
        new initProcess().start();
    }

    private boolean getMiuiRestrictMethod(){
        boolean bt = false;
        try {
            //if (com.ucloudlink.refact.Configuration.INSTANCE.getCurrentSystemVersion() == ANDROID_MIUI_V8) {
            if (ServiceManager.INSTANCE.getSystemType() == Configuration.ANDROID_MIUI_V8){
                JLog.logd("get restrictMethod from miui");
                String extraNetworkClassName = "miui.provider.ExtraNetwork";
                Class mExtraNetworkClass = Class.forName(extraNetworkClassName);
                //Method getDefault = mExtraNetworkClass.getMethod("getDefault");
                //Object mExtraNetworkObject = getDefault.invoke(null);
                mExtraNetworkIsMobileRestrict = mExtraNetworkClass.getMethod("isMobileRestrict", new Class[]{Context.class, String.class});
                mExtraNetworkSetMobileRestrict = mExtraNetworkClass.getMethod("setMobileRestrict", new Class[] {Context.class, String.class, boolean.class});
                bt = true;
            }
            else {
                JLog.logd("It is not a Xiaomi phone.");
            }
        } catch (Exception e) {
            JLog.logd(e);
            mExtraNetworkIsMobileRestrict = null;
            mExtraNetworkSetMobileRestrict = null;
            JLog.logd("Failed to get restrictMethod from miui!");
        }
        return bt;
    }

    /**
     * miui接口，放开自身的网络限制
     */
    public void enableMyselfNetworkLink(){
        setMobileRestrict(ServiceManager.appContext.getApplicationInfo().packageName, false);  /* service */
        setMobileRestrict(STR_CLOUDSIM_PACKAGE_NAME, false);   /*UI package*/
    }

    public boolean setMobileRestrict(String apkName, boolean bSet){
        boolean bt = false;

        if (mExtraNetworkSetMobileRestrict != null) {
            try {
                bt = ((Boolean) mExtraNetworkSetMobileRestrict.invoke(null, new Object[]{ServiceManager.appContext.getApplicationContext(), apkName,
                        new Boolean(bSet)})).booleanValue();
            } catch (Exception e) {
                e.printStackTrace();
            }
            JLog.logd("setMobileRestrict:" + apkName + " " + bSet + "," + "Result:" + bt);
        }
        else {
            JLog.logd("Function setMobileRestrict is invalid.");
        }
        return bt;
    }

    public boolean isMobileRestrict(String apkName){
        boolean bt = false;
        if (mExtraNetworkIsMobileRestrict != null) {
            try {
                bt = ((Boolean) mExtraNetworkIsMobileRestrict.invoke(null, new Object[]{ServiceManager.appContext.getApplicationContext(), apkName})).booleanValue();
            } catch (Exception e) {
                e.printStackTrace();
            }
            JLog.logd(apkName + " now is Restrict:" + bt);
        }
        else{
            JLog.logd("Function isMobileRestrict is invalid.");
        }

        return bt;
    }

    private void getLocalPackagesInfo(){
        localPackageInfos.clear();
        PackageManager pmr = ServiceManager.appContext.getPackageManager();
        List<PackageInfo> packageInfos = pmr.getInstalledPackages(0);
        JLog.logd("get packageInfors Number is " + packageInfos.size());
        for (int i = 0; i < packageInfos.size(); i++){
            PackageInfo packageInfo = packageInfos.get(i);

            if (packageInfo.applicationInfo.uid > 10000){
                if ((!packageInfo.packageName.equals(ServiceManager.appContext.getApplicationInfo().packageName))
                        && (!packageInfo.packageName.equals(STR_CLOUDSIM_PACKAGE_NAME))) {
                    LocalPackageInfo lpi = new LocalPackageInfo();
                    boolean tf = false;

                    lpi.setPackageUid(packageInfo.applicationInfo.uid);
                    lpi.setPackageName(packageInfo.packageName);
                    if (isMiui) {
                        tf = isMobileRestrict(packageInfo.packageName);
                    }

                    if (!tf) {
                        lpi.setRestrict(false);
                        JLog.logd(packageInfo.packageName + ", isRestrict:" + tf + ", uid:" + packageInfo.applicationInfo.uid);
                        localPackageInfos.add(lpi);
                    }
                    //  SharedPreferencesUtils.putInt(ServiceManager.appContext,PACKAGE_INFO_RESTRICT_SP_NAME, packageInfo.packageName,tf);
                }
            }
        }

        JLog.logd("get Local Packages num:" + localPackageInfos.size());
    }
}

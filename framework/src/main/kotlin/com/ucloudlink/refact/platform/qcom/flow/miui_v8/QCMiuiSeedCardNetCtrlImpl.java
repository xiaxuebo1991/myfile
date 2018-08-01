package com.ucloudlink.refact.platform.qcom.flow.miui_v8;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.ucloudlink.framework.flow.ISeedCardNetCtrl;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.LocalPackageInfo;
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo;
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder;
import com.ucloudlink.refact.channel.enabler.IDataEnabler;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jianguo.he on 2018/1/12.
 */

public class QCMiuiSeedCardNetCtrlImpl implements ISeedCardNetCtrl {

    private final String STR_CLOUDSIM_PACKAGE_NAME = "com.ucloudlink.cloudsim";

    @Override
    public void initState(@NotNull IDataEnabler seedSimEnable, @NotNull IDataEnabler cloudSimEnabler) {

    }

    @Override
    public void setRestrictAllNet(@Nullable String tag) {
        List<LocalPackageInfo> listLocalPackageInfos = SeedCardNetLimitHolder.getInstance().getCopyListLocalPackageInfos();
        if (listLocalPackageInfos.size() < 1){
            getLocalPackagesInfo();
        }
        for (int i = 0; i < listLocalPackageInfos.size(); i++){
            LocalPackageInfo lpi =  listLocalPackageInfos.get(i);
            if (!lpi.getRestrict()){
                FlowBandWidthControl.getInstance().getExtraNetworkCtrl().setMobileRestrict(lpi.getPackageName(), true);
            }
        }
        enableMyselfNetworkLink();
    }

    @Override
    public void clearRestrictAllRuleNet(@Nullable String tag) {
        List<LocalPackageInfo> listLocalPackageInfos = SeedCardNetLimitHolder.getInstance().getCopyListLocalPackageInfos();
        if (listLocalPackageInfos.size() < 1){
            getLocalPackagesInfo();
        }
        for (int i = 0; i < listLocalPackageInfos.size(); i++){
            LocalPackageInfo lpi =  listLocalPackageInfos.get(i);
            if (!lpi.getRestrict()){
                FlowBandWidthControl.getInstance().getExtraNetworkCtrl().setMobileRestrict(lpi.getPackageName(), false);
            }
        }
        enableMyselfNetworkLink();
    }

    @Override
    public void configDnsOrIp(@NotNull SeedCardNetInfo info) {

    }

    private void getLocalPackagesInfo(){
        List<LocalPackageInfo> listLocalPackageInfos = new ArrayList<>();
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
                    tf = FlowBandWidthControl.getInstance().getExtraNetworkCtrl().isMobileRestrict(packageInfo.packageName);

                    if (!tf) {
                        lpi.setRestrict(false);
                        JLog.logd(packageInfo.packageName + ", isRestrict:" + tf + ", uid:" + packageInfo.applicationInfo.uid);
                        listLocalPackageInfos.add(lpi);
                    }
                    //  SharedPreferencesUtils.putInt(ServiceManager.appContext,PACKAGE_INFO_RESTRICT_SP_NAME, packageInfo.packageName,tf);
                }
            }
        }
        SeedCardNetLimitHolder.getInstance().setListLocalPackageInfos(listLocalPackageInfos);

        JLog.logd("get Local Packages num:" + listLocalPackageInfos.size());
    }
    /**
     * miui接口，放开自身的网络限制
     */
    public void enableMyselfNetworkLink(){
        /* service */
        FlowBandWidthControl.getInstance().getExtraNetworkCtrl().setMobileRestrict(ServiceManager.appContext.getApplicationInfo().packageName, false);
        /*UI package*/
        FlowBandWidthControl.getInstance().getExtraNetworkCtrl().setMobileRestrict(STR_CLOUDSIM_PACKAGE_NAME, false);
    }

}

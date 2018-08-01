package com.ucloudlink.refact.platform.qcom.flow.netlimit;

import com.ucloudlink.framework.flow.ISeedCardNetCtrl;
import com.ucloudlink.framework.flow.QCSeedCardNetCtrlImpl;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.netlimit.qualcomm.QCUidSeedCardNetCtrlImpl;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.platform.qcom.flow.miui_v8.QCMiuiSeedCardNetCtrlImpl;

/**
 * Created by jianguo.he on 2018/1/11.
 */


public class QCSeedCardNetFactory {

    public static ISeedCardNetCtrl getISeedCardNetCtrl(boolean isFrameworkSupportSeedNetLimitByUidAndIP
            , boolean isUiSupportSeedNetLimitByUidAndIP){
        if(ServiceManager.INSTANCE.getSystemType() == Configuration.ANDROID_MIUI_V8){
            return new QCMiuiSeedCardNetCtrlImpl();
        }
        //if(SeedCardNetLimitHolder.getInstance().isExistIfNameExtra() && SeedCardNetLimitHolder.getInstance().isUiSupportSeedNetworkLimitByUidAndIp()){
        if(isFrameworkSupportSeedNetLimitByUidAndIP && isUiSupportSeedNetLimitByUidAndIP){
            return new QCSeedCardNetCtrlImpl();
        }
        return  new QCUidSeedCardNetCtrlImpl();
    }

}

package com.ucloudlink.refact.business.flow.netlimit;

import android.annotation.Nullable;
import android.content.Context;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Created by jianguo.he on 2018/1/9.
 */

public class ExtraNetworkImpl implements IExtraNetwork {

    @Override
    public boolean setMobileRestrict(@NotNull Context context, @NotNull String apkName, boolean bSet) {
        JLog.logd("miui setMobileRestrict(): apkName = "+(apkName==null?"null":apkName)+", bSet = "+bSet);
        boolean ret = false;
        try{
            String extraNetworkClassName = "miui.provider.ExtraNetwork";
            Class mExtraNetworkClass = Class.forName(extraNetworkClassName);
            Method method = mExtraNetworkClass.getMethod("setMobileRestrict", new Class[] {Context.class, String.class, boolean.class});
            ret = (boolean) method.invoke(null, new Object[]{ServiceManager.appContext.getApplicationContext(), apkName,
                    new Boolean(bSet)});
        }catch (Exception e){
            JLog.logd("miui, setMobileRestrict return : " + ret);
        }
        return ret;
    }

    @Override
    public boolean isMobileRestrict(@NotNull Context context, @Nullable String apkName) {
        JLog.logd("miui isMobileRestrict(): apkName = "+(apkName==null?"null":apkName));
        boolean ret = false;
        try{
            String extraNetworkClassName = "miui.provider.ExtraNetwork";
            Class mExtraNetworkClass = Class.forName(extraNetworkClassName);
            Method method = mExtraNetworkClass.getMethod("isMobileRestrict", new Class[] {Context.class, String.class});
            ret = (boolean) method.invoke(null, new Object[]{ServiceManager.appContext.getApplicationContext(), apkName});
        }catch (Exception e){
            JLog.logd("miui, isMobileRestrict return : " + ret);
        }
        return ret;
    }
}

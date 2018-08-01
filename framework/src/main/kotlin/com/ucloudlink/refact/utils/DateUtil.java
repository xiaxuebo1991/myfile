package com.ucloudlink.refact.utils;

import java.text.SimpleDateFormat;

/**
 * Created by jianguo.he on 2018/1/19.
 */

public class DateUtil {

    public static String format_YYYY_MM_DD_HH_SS_SSS(long timeMillis){
        String resultStr = "";
        try{
            resultStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(timeMillis);
        }catch (Exception e){
            JLog.logi("SCFlowLog format Exception -: "+e.toString());
            resultStr = "";
        }

        return resultStr;
    }
}

package com.ucloudlink.refact.business.flow.netlimit.common;

import android.text.TextUtils;


import com.ucloudlink.refact.utils.JLog;

import java.util.regex.Pattern;

/**
 * Created by jianguo.he on 2017/11/22.
 */

public class DnsUtils {

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";

    /**
     * 返回的ip 可能是域名，可能是数字ip
     * @param str
     * @return
     */
    public static String getDnsOrIp(String str){
        if(TextUtils.isEmpty(str))
            return null;
        try{
            String result = str.replaceFirst(HTTP,"");
            result = result.replaceFirst(HTTP.toUpperCase(),"");
            result = result.replaceFirst(HTTPS,"");
            result = result.replaceFirst(HTTPS.toUpperCase(),"");
            JLog.logi("getDnsOrIp（） param str= "+str+", result=" + result);

            String[] sArray = result.split("/");
            if(sArray.length > 0 ){
                result =  sArray[0];
                if(result.contains(":")){
                    result = result.substring(0, result.indexOf(":"));
                }
                if(result.contains("?")){
                    result = result.substring(0, result.indexOf("?"));
                }
                return result;
            }
        }catch (Exception e){
            JLog.loge("getDnsOrIp() param: " + str + ", Exception: "+e.toString());
        }
        return null;
    }

    /**
     * 域名地址是否为数字开始
     * @param ipOrDns
     * @return
     */
    public static boolean isDnsStartWithNumber(String ipOrDns){
        boolean b = false;
        if(!TextUtils.isEmpty(ipOrDns)){
            String[] array = ipOrDns.split("\\.");
            if(array!=null && array.length > 0 ){
                return DnsUtils.isNumeric(array[0]);
            }
        }
        return b;
    }

    /**
     * 字符串是否为数字
     * @param str
     * @return
     */
    public static boolean isNumeric(String str){
        if(TextUtils.isEmpty(str))
            return false;
        Pattern pattern = Pattern.compile("[0-9]*");
        return pattern.matcher(str).matches();
    }

}

package com.ucloudlink.refact.utils

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.model.m2.ATMsgEncode

/**
 * Created by zhifeng.gao on 2018/6/15.
 */
object WifiApUtil{
    fun getWifiConfig(): WifiConfiguration? {
        try {
            val wifiManager= ServiceManager.appContext.applicationContext.getSystemService(Context.WIFI_SERVICE)
            val method = WifiManager::class.java.getMethod("getWifiApConfiguration")
            method.setAccessible(true)
            var config = method.invoke(wifiManager) as WifiConfiguration
            return config
        }catch (e: Exception) {
            JLog.loge(e)
            return null
        }
    }
    fun setWifiApStatus(conf:WifiConfiguration?,apStatus:Boolean){
       var config :WifiConfiguration?
        if(conf!=null){
            config = conf
        }else{
            config = getWifiConfig()
        }
        val wifiManager= ServiceManager.appContext.applicationContext.getSystemService(Context.WIFI_SERVICE)
        try {
            val method2 = WifiManager::class.java.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            method2.invoke(wifiManager, config, apStatus)
        } catch (e: Exception) {
            JLog.loge(e)
        }
    }
}
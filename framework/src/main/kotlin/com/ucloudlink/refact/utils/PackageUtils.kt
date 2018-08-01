package com.ucloudlink.refact.utils

import com.ucloudlink.refact.ServiceManager

/**
 * 获取包数据相关的Utils
 * @author zhe.li
 */
object PackageUtils {
    /**
     * 返回当前程序版本名
     */
    fun getAppVersionName(): String {
        return try {
            ServiceManager.appContext.packageManager.getPackageInfo(ServiceManager.appContext.packageName, 0).versionName
        } catch (e: Exception) {
            JLog.logke("VersionInfo: Exception$e")
            ""
        }
    }
}
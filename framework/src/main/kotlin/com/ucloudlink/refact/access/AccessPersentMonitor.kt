package com.ucloudlink.refact.access

import android.content.Context
import android.net.ConnectivityManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.uploadsocketok.UploadSocketOkTask
import com.ucloudlink.refact.utils.JLog

/**
 * Created by haiping.liu on 2017/12/15.
 * 状态机百分比监听器
 */
object AccessPersentMonitor {
    const val STATUS_STOP: Int = 1
    const val STATUS_RUNNING: Int = 2
    const val STATUS_SUCCESS: Int = 3
    var accessState = STATUS_STOP
    var upSocketOkTask:UploadSocketOkTask? = null

    fun accessPersentReg() {
        val accessEntry = ServiceManager.accessEntry
        accessEntry.accessState.statePersentOb.subscribe(
                {
                    if (getDsdsServiceStatus(it) == STATUS_STOP) {
                        accessState = STATUS_STOP
                        accessEntry.softsimEntry.softsimUpdateManager.accessStateRunningOrStop()

                    } else if (getDsdsServiceStatus(it) == STATUS_RUNNING) {
                        accessState = STATUS_RUNNING
                        accessEntry.softsimEntry.downloadState.stopWhenAccessStateRunning()
                        accessEntry.softsimEntry.softsimUpdateManager.accessStateRunningOrStop()

                    } else if (getDsdsServiceStatus(it) == STATUS_SUCCESS) {
                        accessState = STATUS_SUCCESS
                        accessEntry.softsimEntry.softsimUpdateManager.accessStateSuccess()

                    }
                    JLog.logd("persent change -> $it , accessState = ${accessState} (1-stop 2-running 3-success)")
                    if (it == 100){
                        if (upSocketOkTask == null){
                            upSocketOkTask = UploadSocketOkTask()
                            upSocketOkTask?.uploadSocketOk()
                        }
                    }else{
                        if (upSocketOkTask != null){
                            upSocketOkTask?.unSubscription()
                            upSocketOkTask = null
                        }
                    }
                }
        )
    }

    fun getDsdsServiceStatus(persent: Int): Int {
        if (persent == 0) {
            return STATUS_STOP
        } else if (persent > 0 && persent < 90) {
            return STATUS_RUNNING
        } else if (persent == 90) {
            if (isWifiConnected(ServiceManager.appContext)) {
                return STATUS_SUCCESS
            } else {
                return STATUS_RUNNING
            }
        } else if (persent > 90 && persent < 100) {
            return STATUS_RUNNING
        } else if (persent == 100) {
            return STATUS_SUCCESS
        }
        return STATUS_STOP
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connectivityManager.allNetworks
        for (network in networks){
            val networkInfo =  connectivityManager.getNetworkInfo(network)
            if (networkInfo.type == ConnectivityManager.TYPE_WIFI && networkInfo.isAvailable && networkInfo.isConnected){
                return true
            }
        }
        return false
    }
}
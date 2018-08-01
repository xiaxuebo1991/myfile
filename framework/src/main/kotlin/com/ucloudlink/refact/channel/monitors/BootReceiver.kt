package com.ucloudlink.refact.channel.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.access.ui.AccessEntryService
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.logs.PerfLogPownOn
import com.ucloudlink.refact.utils.SharedPreferencesUtils

/**
 * Created by jiaming.liang on 2017/2/22.
 */
class BootReceiver : BroadcastReceiver(){
    override fun onReceive(context: Context, intent: Intent) {
        logd("BootReceiver system boot")
        val service = Intent(context, AccessEntryService::class.java)
        context.startService(service)
        SharedPreferencesUtils.putInt(context, PerfLogPownOn.SP_BOOT_TIME,PerfUntil.getBootTime())
        SharedPreferencesUtils.putInt(context, PerfLogPownOn.SP_BOOT_POWER_LEFT,PerfUntil.getBatteryLevel(context))
    }
}
package com.ucloudlink.refact.business.performancelog.data

import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import com.ucloudlink.framework.protocol.protobuf.preflog.Sys_start_type
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import android.provider.Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL



/**
 * Created by shiqianhua on 2018/3/24.
 */
object RebootCheck {
    var rebootType = Sys_start_type.SYS_START_NORMAL
    var saveLowBattery:Boolean
        set(value) {
            try {
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext,"saveLowBattery", value)
            }catch (e:Exception){
                e.printStackTrace()
            }
        }
        get() {
            return SharedPreferencesUtils.getBoolean(ServiceManager.appContext,"saveLowBattery", false)
        }

    var rebootCount:Int
        set(value) {
            SharedPreferencesUtils.putInt(ServiceManager.appContext,"rebootCount", value)
        }
        get() {
            return SharedPreferencesUtils.getInt(ServiceManager.appContext,"rebootCount", 0)
        }

    var curPersent:Int
        set(value){
            SharedPreferencesUtils.putInt(ServiceManager.appContext,"RebootCheckcurPersent", value)
        }
        get() {
            return SharedPreferencesUtils.getInt(ServiceManager.appContext,"RebootCheckcurPersent", 0)
        }

    fun init(context: Context){
        rebootCount += 1
        logd("rebootCount $rebootCount")
        val persent = curPersent
        logd("curPersent $persent $saveLowBattery")
        if(persent > 0){
            if(saveLowBattery) {
                rebootType = Sys_start_type.SYS_START_LOW_POWER
            }else{
                rebootType = Sys_start_type.SYS_START_OTHER_ABNORMAL
            }
        }

        val defWarnLevel = context.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel)
        val mLowBatteryWarningLevel = Settings.Global.getInt(context.contentResolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel)
        val batteryM = context.getSystemService(Context.BATTERY_SERVICE)  as BatteryManager
        val level = batteryM.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        logd("get battery level $level th $mLowBatteryWarningLevel")
        if(level < mLowBatteryWarningLevel){
            saveLowBattery = true
        }

        ServiceManager.accessEntry.statePersentOb.subscribe(
                {
                    logd("state persent to $it")
                    curPersent = it
                }
        )

    }

    fun clearRebootCount(){
        rebootCount = 0
    }
}
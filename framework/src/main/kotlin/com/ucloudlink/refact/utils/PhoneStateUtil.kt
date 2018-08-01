package com.ucloudlink.refact.utils

import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv

/**
 * Created by jiaming.liang on 2016/10/22.
 */
class PhoneStateUtil {
    companion object {
        fun isMobileDataEnabled(context: Context): Boolean {
            val mTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val dataEnabled = mTelephonyManager.getDataEnabled()
            logd("isMobileDataEnabled:$dataEnabled")
            return dataEnabled
        }

        fun isMobileDataEnabled(context: Context, subid: Int): Boolean {
            val mTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val dataEnabled = mTelephonyManager.getDataEnabled(subid)
            logd("isMobileDataEnabled:$dataEnabled")
            return dataEnabled
        }

        fun isRoamEnabled(context: Context, subId: Int): Boolean {
            var enableState = 0
            try {
                enableState = TelephonyManager.getIntWithSubId(context.contentResolver, Settings.Global.DATA_ROAMING, subId)
            }catch (e: Settings.SettingNotFoundException){
                logv("SettingNotFoundException subId :$subId")
            }
            return enableState != 0
        }

        fun setMobileDataEnable(context: Context, subId: Int, enable: Boolean) {
            var mTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//            mTelephonyManager.setDataEnabled(enable)
            mTelephonyManager.setDataEnabled(subId, enable)
        }

        fun getMobileDataEnable(context: Context, subId: Int): Boolean {
            var mTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//            mTelephonyManager.setDataEnabled(enable)
            return mTelephonyManager.getDataEnabled(subId)
        }

        fun setRoamEnable(context: Context, subId: Int, isOn:Boolean) {
            Settings.Global.putInt(context.contentResolver, Settings.Global.DATA_ROAMING + subId, if (isOn) 1 else 0)
//            context.getContentResolver().registerContentObserver()
        }

        fun getNetworkModeBySubId(subId: Int): Int {
            return TelephonyManager.getIntWithSubId(ServiceManager.appContext.contentResolver, Settings.Global.PREFERRED_NETWORK_MODE, subId)
        }
    }
}

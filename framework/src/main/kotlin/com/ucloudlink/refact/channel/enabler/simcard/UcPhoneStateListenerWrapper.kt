package com.ucloudlink.refact.channel.enabler.simcard

import android.os.Build
import android.os.Looper
import android.telephony.PhoneStateListener

/**
 * 注意，由于8.0 中 直接引用mSubId 会有异常，需要使用PhoneStateListener 的mSubId 请使用这个类
 */
open class UcPhoneStateListenerWrapper(subId: Int, looper: Looper) : PhoneStateListener(looper) {
    protected var uSubId = subId

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val field = PhoneStateListener::class.java.getDeclaredField("mSubId")
            val _subId = Integer.valueOf(subId)
            field.set(this, _subId)
        } else {
            mSubId = subId
        }
    }

}
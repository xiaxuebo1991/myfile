package com.ucloudlink.refact.platform.sprd.channel.enabler.simcard

import android.content.Context
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.simcard.CloudSimEnabler2
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.watcher.SprdCloudSimWatcher
import com.ucloudlink.refact.product.mifi.PhyCardApn.ApnNetworkErrCB
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2018/1/12.
 */
class SprdCloudsimEnabler(mContext: Context, mLooper: Looper) : CloudSimEnabler2(mContext, mLooper) {
    override fun onCardInService() {
        super.onCardInService()
        logd("set dds to subid ${mCard.subId}")
        ServiceManager.systemApi.setDefaultDataSubId(mCard.subId)
    }

    val errcodeCb = object : ApnNetworkErrCB{
        var lastDeniedReason = 0
        var sameDeniedReasonHit = 0
            set(value) {
                field = value
                JLog.logv("sameDeniedReasonHit = $field")
                if(value > 0 && lastDeniedReason > 0) {
                    handleDenied(lastDeniedReason, field >= MAX_SAME_DENIED_HIT)
                }
            }

        private fun handleDenied(reason: Int, isDisable: Boolean) {
            val exception: EnablerException
            if (isDisable) {
                sameDeniedReasonHit = 0
                exception = EnablerException.EXCEPTION_REG_DENIED
            } else {
                exception = EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE
            }

            if(reason > 0) {
                exception.reason.errorCode = reason
                notifyException(exception, "reason:$reason", isDisable)
                logd("handleDenied Registration Denied reason:$reason")
            }
        }

        override fun networkErrUpdate(phoneId: Int, errcode: Int) {
            if(phoneId != mCard.slot){
                logd("ignore")
                return
            }
            logd("SprdNetworkErrUpdate phoneId $phoneId, errcode $errcode")
            val deniedReason = errcode
            val shouldIgnore = (SystemClock.uptimeMillis() - regListenTime) < 5000
            if (!shouldIgnore && deniedReason != 0) {
                if (deniedReason == lastDeniedReason) {
                    sameDeniedReasonHit++
                } else {
                    sameDeniedReasonHit = 1
                }
                lastDeniedReason = deniedReason
                //处理马上换卡原因
                when (deniedReason) {
                    3, 4, 5, 6, 7, 8, 11 -> {
                        handleDenied(deniedReason, true)
                    }
                    15 -> if (mCard.rat == PreferredNetworkType.SERVER_RAT_TYPE_4G) {
                        handleDenied(deniedReason, true)
                    }
                }
            }
        }
    }

    override fun onCardReady() {
        super.onCardReady()
        ServiceManager.systemApi.registerNetworkErrCB(errcodeCb)
    }

    override fun onCardAbsent(enablerClosing: Boolean, logout: Boolean, keepChannel: Boolean) {
        super.onCardAbsent(enablerClosing, logout,keepChannel)
        ServiceManager.systemApi.deregsiterNetworkErrCB(errcodeCb)
    }

    override fun initWatcher() {
        val watchThread = HandlerThread("${this.javaClass.simpleName}-watchThread")
        watchThread.start()
        timeoutWatcher = SprdCloudSimWatcher(watchThread.looper, this, "${this.javaClass.simpleName}-TO")
        timeoutWatcher.exceptionObser.asObservable().subscribe {
            notifyException(it)
        }
    }
}
package com.ucloudlink.refact.business.crossborder


import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.utils.JLog
import rx.lang.kotlin.subscribeWith

/**
 * Created by zhangxian on 2016/12/21.
 */

object CrossBorder : Thread() {
    private val MCC_CHANGE_TRACKER_START = 1

    private var netOperatorTimeout: Long = 2000
    private var mLastNetOperator: String = ""
    private var mLooper: Looper? = null
    private lateinit var mHandler: myHandler

    private fun listenCardState() {
        ServiceManager.seedCardEnabler.cardStatusObser().subscribeWith {
            onNext {
                cardStatus ->
                if (cardStatus == CardStatus.IN_SERVICE) {
                    JLog.logd("seedCardEnabler cardStatus:" + cardStatus)
                    updateSeedPlmnList()
                }
            }
        }
        ServiceManager.cloudSimEnabler.cardStatusObser().subscribeWith {
            onNext {
                cardStatus ->
                if (cardStatus == CardStatus.OUT_OF_SERVICE) {
                    JLog.logd("cloudSimEnabler cardStatus:" + cardStatus)
                    mHandler.obtainMessage(MCC_CHANGE_TRACKER_START).sendToTarget()
                }
            }
        }
    }

    init {
        val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val seedOperator = mPhone.getNetworkOperatorForPhone(Configuration.seedSimSlot)
        if (!TextUtils.isEmpty(seedOperator) && seedOperator != "00000" && seedOperator != "000000") {
            OperatorNetworkInfo.mccmnc = seedOperator
        }
    }

    private fun updateSeedPlmnList() {
        val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val seedOperator = mPhone.getNetworkOperatorForPhone(Configuration.seedSimSlot)

        JLog.logd("seedSimSlot=" + Configuration.seedSimSlot + " seedOperator=" + seedOperator + " mLastNetOperator=" + mLastNetOperator)
        if (seedOperator.isNotEmpty() && seedOperator != "00000" && seedOperator != "000000") {
            if (mLastNetOperator.isEmpty()) {
                mLastNetOperator = seedOperator
            }
            //更新mccmnc
            OperatorNetworkInfo.mccmnc = seedOperator
            OperatorNetworkInfo.reflashSeedPlmnList()

            val oldmcc = mLastNetOperator.substring(0, 3)
            val newmcc = seedOperator.substring(0, 3)

            JLog.logd("oldmcc" + oldmcc + "newmcc" + newmcc)
            try {
                if (oldmcc.isNotEmpty() && newmcc.isNotEmpty() && oldmcc != newmcc) {
                    if (!(MccTypeMap[oldmcc] == MccTypeMap[newmcc] && MccTypeMap[newmcc] != null)) {
                        JLog.logd("mcc change!!")
                        mLastNetOperator = seedOperator
                        com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEED_MCC_CHANGE, newmcc)
                    }
                }
            } catch(e: Exception) {
                JLog.loge("updateSeedPlmnList", e)
            }
        }
    }

    private val getNetworkOperator: Runnable = Runnable {
        if ((ServiceManager.cloudSimEnabler.getCard().status != CardStatus.OUT_OF_SERVICE) &&
                (ServiceManager.cloudSimEnabler.getCard().status != CardStatus.EMERGENCY_ONLY)) {
            return@Runnable
        }

        updateSeedPlmnList()
        netOperatorTimeout = 5000
        mHandler.obtainMessage(MCC_CHANGE_TRACKER_START).sendToTarget()
    }

    private class myHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            JLog.logd("handleMessage:" + msg.what)
            try {
                when (msg.what) {//不同状态不同处理
                    MCC_CHANGE_TRACKER_START -> {
                        mHandler.removeCallbacks(getNetworkOperator)
                        mHandler.postDelayed(getNetworkOperator, netOperatorTimeout)
                    }
                }
            } catch (e: Exception) {
                JLog.loge("handleMessage", e)
            }

        }
    }

    override fun run() {
        try {
            JLog.logd("thread run")
            listenCardState()
            Looper.prepare()
            mLooper = Looper.myLooper()
            mHandler = myHandler(mLooper!!)
            Looper.loop()
        } catch (e: Exception) {
            JLog.loge("thread run", e)
        } finally {
            JLog.logd("thread end")
        }
    }
}


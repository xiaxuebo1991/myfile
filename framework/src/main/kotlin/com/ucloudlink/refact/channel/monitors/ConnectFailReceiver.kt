package com.ucloudlink.refact.channel.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.plmnselect.SwitchNetHelper
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.toHex
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ConnectFailReceiver : BroadcastReceiver() {
    private val SUBSCRIPTION_KEY = "subscription"
    private val FAILURE_REASON_KEY = "reason"
    private val NEW_REJECT_INTENT_DATA_LEN = 10
    private val OLD_REJECT_INTENT_DATA_LEN = 8
    private var newRejectType = 0

    /**
     * attach被拒数据类型如下
     * com.ucloudlink.attach.reject.cause
     *
     * uint16_t rej_cause;
     * uint16_t mcc;
     * uint16_t mnc;
     * uint8_t rat;
     * uint8_t pcs_digit;
     * uint8_t reject_type;
     * uint8_t sim_type;
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.DATA_CONNECTION_FAILED") {//云卡物理卡拨号被拒
            if (newRejectType == 1) {
                return
            }
            val failReasonStr = intent.getStringExtra(FAILURE_REASON_KEY)
            val subId = intent.getIntExtra(SUBSCRIPTION_KEY, -1)
            logd("ucloudlink ACTION_DATA_CONNECTION_FAILED reason is $failReasonStr")
            logd("ucloudlink ACTION_DATA_CONNECTION_FAILED sub id is $subId")
            SwitchNetHelper.switchOnDataConnectFailed(failReasonStr, subId)
        } else if (intent.action == "com.ucloudlink.attach.reject.cause") {//云卡物理卡注册被拒
            val data = intent.getByteArrayExtra("cause")
            try {
                logd("ucloudlink attach.reject.cause data:" + data.toHex() + " len:" + data.size)
                val payload = ByteBuffer.wrap(data)
                payload.order(ByteOrder.nativeOrder())

                val cause = payload.short
                val mcc = payload.short
                val mnc = payload.short
                val rat = payload.get()
                val bitNum = payload.get()
                val subId = ServiceManager.seedCardEnabler.getCard().subId

                val addBitNum = if (bitNum == 1.toByte()) {
                    3 - mnc.toString().length
                } else {
                    2 - mnc.toString().length
                }
                val plmn = when (addBitNum) {
                    1 -> mcc.toString() + "0" + mnc.toString()
                    2 -> mcc.toString() + "00" + mnc.toString()
                    else -> mcc.toString() + mnc.toString()
                }
                if (data.size == NEW_REJECT_INTENT_DATA_LEN) {
                    newRejectType = 1
                    val rejectType = payload.get()
                    val slotId = payload.get()//slotId 1,2
                    logd("ucloudlink attach.reject.cause data:$data cause:$cause mcc:$mcc mnc:$mnc rat:$rat plmn:$plmn reject_type:$rejectType sim_type:$slotId rejectRetryTimes:" + ServiceManager.seedCardEnabler.getCard().rejectRetryTimes)
                    SwitchNetHelper.switchOnAttachReject(cause.toInt(), rejectType, AttachReject(subId, plmn),slotId.toInt()-1)
                } else if (data.size == OLD_REJECT_INTENT_DATA_LEN) {
                    logd("ucloudlink attach.reject.cause data:$data cause:$cause mcc:$mcc mnc:$mnc rat:$rat plmn:$plmn")
                    SwitchNetHelper.switchOnAttachRejectOld(cause.toInt(), AttachReject(subId, plmn))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loge("catch exception ${e.message}")
            }
        }
    }
}

data class AttachReject(val subId: Int, val plmn: String)
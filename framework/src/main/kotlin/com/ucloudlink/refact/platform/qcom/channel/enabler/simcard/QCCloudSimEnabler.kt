package com.ucloudlink.refact.platform.qcom.channel.enabler.simcard

import android.content.Context
import android.os.Looper
import com.ucloudlink.framework.softsim.OnDemandPsCallUtil
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.plmnselect.CardReject
import com.ucloudlink.refact.channel.enabler.simcard.CloudSimEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.dds.switchDdsToNext
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import java.util.*

/**
 * Created by jiaming.liang on 2017/11/29.
 */
class QCCloudSimEnabler(mContext: Context, mLooper: Looper) : CloudSimEnabler2(mContext, mLooper) {

    private var isOutgoingCall = false 
    
    override fun onCardReady() {
        super.onCardReady()
        switchDdsToNext(mCard.subId, mCard.slot).subscribe({
            JLog.logk("VSim Ready and set DDS at Vsim!")
            delayEnableSeedCard(Configuration.delayToEnableSeedCard)
        }, {
            loge("set DDS fail")
        })
    }

    private fun delayEnableSeedCard(delayTime: Long) {
        postDelayed({

            if (!isCardOn() || isClosing()) {
                logd("[delayEnableSeedCard] Cloud sim is off return ")
                return@postDelayed
            }
            if (isOutgoingCall){
                logd("[delayEnableSeedCard] isOutgoingCall == true ")
                return@postDelayed
            }
            
            Requestor.requireChannel(Requestor.apduid)
            val ret = ServiceManager.seedCardEnabler.enable(ArrayList<Card>())
            if (ret != 0) {
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_ENABLE_FAIL, ret)
            }
            logd("ServiceManager.seedCardEnabler.enable ret:" + ret)
        }, delayTime)
    }

    override fun onCloseDataEnabler(reason: String): Long {

        var sleepTime = 0L
        if (isCardOn()) {
            sleepTime = OnDemandPsCallUtil.undoOnDemandPsCall()
            logd(" onCloseDataEnabler sleepTime $sleepTime")
        }
        super.onCloseDataEnabler(reason)

        return sleepTime
    }

    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        super.notifyEventToCard(event, obj)
        when(event){
            DataEnableEvent.OUT_GOING_CALL ->{
                obj?:return
                isOutgoingCall = obj as Boolean
                
            }
            DataEnableEvent.ENENT_CARD_REJECT->{
                val rej = obj as CardReject
                logd("rej == $rej  ")
                notifyException(rej.exception,rej.msg,rej.isShouldDisable)
            }
        }
    }   
}
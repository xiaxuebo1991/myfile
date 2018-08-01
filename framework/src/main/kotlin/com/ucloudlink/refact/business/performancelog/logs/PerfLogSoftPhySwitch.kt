package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_soft_phy_swicth
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by haiping.liu on 2018/3/31.
 * 手机软硬卡切换事件
 */
object PerfLogSoftPhySwitch : PerfLogEventBase() {
    val ID_SWITCH_START = 0
    val ID_SWITCH_END = 1
    var startTime = -1L
    var card: Card? = null
    /**
     * arg1  ID
     * arg2  isSuccess
     */
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogSoftPhySwitch createMsg id=$arg1")
        when (arg1) {
            ID_SWITCH_START -> {
                startTime = System.currentTimeMillis()
                card = ServiceManager.seedCardEnabler.getCard()
            }
            ID_SWITCH_END -> {
                val endTime = System.currentTimeMillis()
                var isSuccess = false
                var type = -1
                var iccid = ""
                var imsi = ""
                if (arg2 > 0) {
                    //切换成功
                    isSuccess = true
                    val newCard = ServiceManager.seedCardEnabler.getCard()
                    val cardType = newCard.cardType
                    if (cardType == CardType.PHYSICALSIM) {
                        type = 2
                    } else {
                        type = 1
                        val tempIccid = newCard.iccId
                        if (tempIccid != null){
                            iccid =tempIccid
                        }else {
                           val netInfo =  PerfUntil.getNetInfo(Configuration.seedSimSlot)
                            iccid = netInfo.iccid
                        }
                        imsi =newCard.imsi
                    }
                } else {
                    //切换失败
                    val mCard = card
                    if (mCard != null) {
                        val cardType = mCard.cardType
                        if (cardType == CardType.PHYSICALSIM){
                            type = 1
                        }else{
                            type = 2
                            imsi = mCard.imsi
                            val tempIccid = mCard.iccId
                            if (tempIccid != null){
                                iccid =tempIccid
                            }
                        }
                    }
                }

                val switch = Ter_soft_phy_swicth.Builder()
                        .head(PerfUntil.getCommnoHead())
                        .iccid(iccid)
                        .imsi(imsi)
                        .occur_time(if (startTime > 0L) (startTime / 1000).toInt() else -1)
                        .type(type)
                        .isSuccess(isSuccess)
                        .duration(if (startTime > 0L) (endTime - startTime).toInt() else -1)
                        .build()
                PerfUntil.saveFreqEventToList(switch)
                startTime = -1L
            }
        }
    }
}

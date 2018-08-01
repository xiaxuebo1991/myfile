package com.ucloudlink.refact.sprd.uimsession


import android.os.SystemClock
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.APDU
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthState
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.cardcontroller.SprdCardController
import com.ucloudlink.refact.platform.sprd.nativeapi.SprdNativeIntf
import com.ucloudlink.refact.platform.sprd.struct.CARD_ACTION_POWERDOWN
import com.ucloudlink.refact.platform.sprd.struct.SprdApduParam
import com.ucloudlink.refact.utils.HexUtil
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by shiqianhua on 2017/11/29.
 */
class SprdNativeIntfCb(ctrl: SprdCardController, slot:Int): SprdNativeIntf {
    val APDU_TIMEOUT = (5400) // ms, 根据展讯优化建议，调整为5.4s
    val cardCtrl = ctrl

    var saveApdu:ByteArray? = null

    private fun procApduEntry(slot: Int, apdu_req: ByteArray?):ByteArray?{
        var rspApdu: ByteArray? = null

        if(cardCtrl.vsimSlot == slot){
            if(!ServiceManager.cloudSimEnabler.isCardOn()){
                logd("card is not on!")
                return byteArrayOf(0x98.toByte(),0x64)
            }
            if(ServiceManager.cloudSimEnabler.isClosing()){
                logd("card is closing")
                return byteArrayOf(0x98.toByte(),0x64)
            }
        }else if(cardCtrl.seedSlot == slot){
            if(!ServiceManager.seedCardEnabler.isCardOn()){
                logd("card is not on!")
                return byteArrayOf(0x98.toByte(),0x64)
            }
            if(ServiceManager.seedCardEnabler.isClosing()){
                logd("card is closing")
                return byteArrayOf(0x98.toByte(),0x64)
            }
        }

        return null
    }

    private fun procApduRsp(slot:Int, apdu_req: ByteArray?, apduParam: SprdApduParam, apdu:APDU):ByteArray?{
        var rspApdu: ByteArray? = null
        if(apduParam.rsp != null){
            if(apdu.isAuth && apdu.isSucc){
                rspApdu = byteArrayOf(0x61.toByte(), (apduParam.rsp!!.size - 2).toByte())
                saveApdu = apduParam.rsp!!
            }else{
                rspApdu = apduParam.rsp!!
            }
            return rspApdu
        }
        return null
    }

    override fun cb(slot: Int, apdu_req: ByteArray?): ByteArray? {
        synchronized(cardCtrl.getLock(slot)) {
            val startTime = SystemClock.uptimeMillis()
            val reqHex = HexUtil.encodeHexStr(apdu_req)
            JLog.logd("recv SprdNativeIntf cb!!! time($startTime) $slot  apdu_req: $reqHex ")
            if(apdu_req == null){
                JLog.loge("apdu_req == null")
                return null
            }

            var rspApdu: ByteArray? = null

            rspApdu = procApduEntry(slot, apdu_req)
            if(rspApdu != null){
                logd("SprdNativeIntf rsp slot($slot) 1: ${if(rspApdu != null) HexUtil.encodeHexStr(rspApdu) else "null" }" + "    request:$reqHex")
                return rspApdu
            }

            if(saveApdu != null){
                rspApdu = saveApdu
                saveApdu = null

                if(slot == cardCtrl.vsimSlot){
                    logd("rsp vsim apdu succ")
                    cardCtrl.updateAuthState(AuthState.AUTH_SUCCESS)
                }
                logd("SprdNativeIntf rsp slot($slot) 4: ${if(rspApdu != null) HexUtil.encodeHexStr(rspApdu) else "null" }" + "    request:$reqHex")
                return rspApdu
            }

            var apduParam = SprdApduParam(lock = Object(), startLock = false)
            var apdu = APDU(slot, apdu_req, args = apduParam)
            cardCtrl.getApduLocal(apdu)
            logd("after call apduparam $apduParam")
            rspApdu = procApduRsp(slot, apdu_req, apduParam, apdu)
            if(rspApdu != null){
                logd("SprdNativeIntf rsp slot($slot) 2: ${if(rspApdu != null) HexUtil.encodeHexStr(rspApdu) else "null" }" + "    request:$reqHex")
                return rspApdu
            }

            var sub = cardCtrl.mCardActionOb.subscribe(
                    {
                        logd("rmtSession.mCardActionOb !! $it")
                        if(it.card.slot == slot && it.action == CARD_ACTION_POWERDOWN){
                            synchronized(apduParam.lock) {
                                apduParam.lock.notifyAll()
                            }
                        }
                    }
            )

            val startTime2 = SystemClock.uptimeMillis()
            logd("wait for rsp!!! time($startTime2) real wait time ${APDU_TIMEOUT.toLong() - startTime2 + startTime}" + "request:$reqHex")
            if(startTime2 - startTime >= APDU_TIMEOUT.toLong()){
                //loge("timeout already!!!!")
                rspApdu = byteArrayOf(0x98.toByte(),0x64)
                if(sub != null && !sub.isUnsubscribed){
                    sub.unsubscribe()
                }
                logd("SprdNativeIntf rsp slot($slot) 5: ${if (rspApdu != null) HexUtil.encodeHexStr(rspApdu) else "null"}" + "    request:$reqHex")
                return rspApdu
            }

            apduParam.startLock = true
            synchronized(apduParam.lock){
                apduParam.lock.wait(APDU_TIMEOUT.toLong() - startTime2 + startTime)
            }

            rspApdu = procApduRsp(slot, apdu_req, apduParam, apdu)
            if(rspApdu == null){
                rspApdu = byteArrayOf(0x98.toByte(),0x64)
            }
            if(sub != null && !sub.isUnsubscribed){
                sub.unsubscribe()
            }
            logd("SprdNativeIntf rsp slot($slot) 3: ${if (rspApdu != null) HexUtil.encodeHexStr(rspApdu) else "null"}" + "    request:$reqHex")
            return rspApdu
        }
    }
}
package com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.cardcontroller

import android.os.Handler
import android.os.Message
import com.android.internal.R.attr.id
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.framework.util.ApnUtil
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.APDU
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthState
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.CardController
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.platform.sprd.api.SprdApiInst
import com.ucloudlink.refact.platform.sprd.struct.CARD_ACTION_POWERDOWN
import com.ucloudlink.refact.platform.sprd.struct.CARD_ACTION_POWERUP
import com.ucloudlink.refact.platform.sprd.struct.CardActionWapper
import com.ucloudlink.refact.platform.sprd.struct.SprdApduParam
import com.ucloudlink.refact.sprd.uimsession.SprdNativeIntfCb
import com.ucloudlink.refact.utils.HexUtil
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.nullToString
import io.netty.util.internal.StringUtil
import org.codehaus.jackson.util.ByteArrayBuilder
import rx.lang.kotlin.PublishSubject
import java.util.concurrent.TimeUnit

/**
 * Created by shiqianhua on 2018/1/11.
 */
class SprdCardController : CardController() {
    private val cbList = listOf(SprdNativeIntfCb(this, 0), SprdNativeIntfCb(this, 1))
    private val MAX_SLOT = 2
    var mCardActionOb = PublishSubject<CardActionWapper>()
    private var authListenKey: String? = null
    var vsimSlot = -1
    val seedSlot: Int
        get() {
            return Configuration.seedSimSlot
        }
    var isInAuth = false
    private val AUTH_TIMEOUT_VALUE = 8 // 鉴权超时，8s，超过这个时间，需要将dds切换到云卡
    val mHandlerThread = ServiceManager.commHandlerThread

    val AUTH_TIMEOUT_MSG = 1
    val mHandler = object : Handler(mHandlerThread.looper) {
        override fun handleMessage(msg: Message?) {
            if (msg == null) return
            when (msg.what) {
                AUTH_TIMEOUT_MSG -> {
                    logd("auth timeout ,so if cloudsim in service, change dds to cloudsim!")
                    if (ServiceManager.cloudSimEnabler.getCardState() == CardStatus.IN_SERVICE) {
                        if (ServiceManager.subMnger.defaultDataPhoneId != vsimSlot) {
                            logd("default dds is not cloudsim! so set it $vsimSlot ${ServiceManager.subMnger.defaultDataPhoneId}")
                            ServiceManager.systemApi.setDefaultDataSlotId(vsimSlot)
                        }
                    }
                }
            }
        }
    }

    // 每个卡槽一个锁
    private val cbLockId1 = Any()
    private val cbLockId2 = Any()

    init {
        SprdApiInst.getInstance().init(mHandlerThread.looper, this)
        authListenKey = addAuthListen { apduStatus ->
            logd("update apdu status: $apduStatus")
            // 开始发送鉴权包的时候，需要设置鉴权卡
            if (apduStatus == AuthState.AUTH_BEGIN) {
                isInAuth = true
                val result = SprdApiInst.getInstance().vsimSetAuthId(seedSlot)
                logd("SprdNative.vsim_set_authid $seedSlot ret : $result")
                val authCause = SprdApiInst.getInstance().getVsimApduCause(vsimSlot)
                logd("SprdNative.vsim_get_auth_cause $vsimSlot $authCause")
                if (ServiceManager.subMnger.defaultDataPhoneId != seedSlot) {
                    logd("default dds is not seed sim! so set it set dds to slot $seedSlot")
                    ServiceManager.systemApi.setDefaultDataSlotId(seedSlot)
                }
                mHandler.removeMessages(AUTH_TIMEOUT_MSG)
                mHandler.sendEmptyMessageDelayed(AUTH_TIMEOUT_MSG, TimeUnit.SECONDS.toMillis(AUTH_TIMEOUT_VALUE.toLong()))
            } else if (apduStatus == AuthState.AUTH_SUCCESS) {
                isInAuth = false
                logd("current state: ${ServiceManager.cloudSimEnabler.getCardState()}")
                if (ServiceManager.cloudSimEnabler.getCardState() == CardStatus.IN_SERVICE) {
                    if (ServiceManager.subMnger.defaultDataPhoneId != vsimSlot) {
                        logd("default dds is not cloudsim! so set it, set dds to slot $vsimSlot ${ServiceManager.subMnger.defaultDataPhoneId}")
                        ServiceManager.systemApi.setDefaultDataSlotId(vsimSlot)
                    }
                }
                mHandler.removeMessages(AUTH_TIMEOUT_MSG)
            } else if (apduStatus == AuthState.AUTH_FAIL) {
                isInAuth = false
            }
        }
    }

    /**
     * 初始化环境，可以强行关闭虚拟卡
     */
    override fun initEnv(slot: Int) {
        super.initEnv(slot)
        if (slot == -1) {
            SprdApiInst.getInstance().clearAllSim()
        } else {
            SprdApiInst.getInstance().clearSim(slot)
        }
    }

    override fun clearEnv(slot: Int) {
        super.clearEnv(slot)
        if (slot == -1) {
            for (i in 0..1) {
                disableRSIMChannelReal(i, true)
            }
        } else {
            disableRSIMChannelReal(slot, true)
        }
    }

    fun open_adf(slot: Int): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()

        buider.append(0x00)
        buider.append(0xa4)
        buider.append(0x04)
        buider.append(0x04)
        buider.append(0x10)
        buider.append(0xa0)
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0x00)

        buider.append(0x87)
        buider.append(0x10)
        buider.append(0x02)
        buider.append(0xff)
        buider.append(0x49)
        buider.append(0xff)
        buider.append(0xff)
        buider.append(0x89)

        buider.append(0x04)
        buider.append(0x0b)
        buider.append(0x00)
        buider.append(0xff)
        var request = buider.toByteArray()
        logd("open_adf:${slot} + request: ${HexUtil.encodeHexStr(request)}")

        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(16)
        var ret = SoftSimNative.apdu(slot, request, rsp, rspLen)
        logd("open_adf ret $ret")

        return ret
    }

    fun open_df(slot: Int): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()

        buider.append(0x00)
        buider.append(0xa4)
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0x02)
        buider.append(0x3f)
        buider.append(0x00)

        var request = buider.toByteArray()
        logd("open_df:${slot} + request: ${HexUtil.encodeHexStr(request)}")

        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(16)
        var ret = SoftSimNative.apdu(slot, request, rsp, rspLen)
        logd("open_df ret $ret")

        return ret
    }

    override fun powerUpCard(card: Card) {
        var ret = 0
        SprdApiInst.getInstance().clearNetworkErrcode(card.slot)
        super.powerUpCard(card)
        open_adf(card.vslot)
        open_df(card.vslot)
        synchronized(this) {
            if (card.cardType == CardType.VSIM || card.cardType == CardType.SOFTSIM) {
                enableRSIMChannelReal(card.slot)
                logd("start init sprd card $card")
                val apn = ApnUtil.getDefaultApnFromList(card.apn)
                if (apn == null) {
                    loge("cloudsim apn is NULL")
                } else {
                    if (!ServiceManager.systemApi.isUnderDevelopMode()) {
                        val id = ApnUtil.InsertApnToDatabaseIfNeed(ServiceManager.appContext, apn)
                    }
                    logd("InsertApnToDatabaseIfNeed $id $apn")
                    if (card.cardType == CardType.VSIM) {
                        logd("set init attach apn! for vsim $apn")
                        val protocol = if(StringUtil.isNullOrEmpty(apn.protocol)) "IPV4V6" else apn.protocol!!
                        ret = SprdApiInst.getInstance().setAttachApn(card.slot, protocol, apn.apn,
                                nullToString(apn.user), nullToString(apn.password), apn.authtype!!.toInt())
                        logd("set initial attach apn return $ret")
                    }
                }
                ret = SprdApiInst.getInstance().vsimQueryVirtual(card.slot)
                logd("SprdNative.vsim_query_virtual ${card.slot}  return " + ret)
                ret = SprdApiInst.getInstance().vsimSetVirtual(card.slot, 1)
                logd("SprdNative.vsim_set_virtual ${card.slot}  1 return " + ret)
                ret = SprdApiInst.getInstance().vsimInit(card.slot, cbList[card.slot], 0)
                logd("SprdNative.vsim_init ${card.slot} ${cbList[card.slot]} return " + ret)
            }
        }
        if (card.cardType == CardType.VSIM) {
            vsimSlot = card.slot
            isInAuth = false
        }
        mCardActionOb.onNext(CardActionWapper(card, CARD_ACTION_POWERUP))
    }

    override fun powerDownCard(card: Card, keepChannel: Boolean) {
        var slot = card.slot
        if (slot > -1 && slot < 2) {
            SprdApiInst.getInstance().clearNetworkErrcode(slot)
            mCardActionOb.onNext(CardActionWapper(card, CARD_ACTION_POWERDOWN))
            super.powerDownCard(card, keepChannel)
            synchronized(this) {
                var ret = SprdApiInst.getInstance().vsimExit(slot)
                logd("SprdNative.vsim_exit  slot " + slot + " ret " + ret)
                ret = SprdApiInst.getInstance().vsimSetVirtual(slot, 0)
                logd("SprdNative.vsim_set_virtual  slot " + slot + " 0 ret " + ret)
                disableRSIMChannelReal(slot, true)
            }
        }
        if (card.cardType == CardType.VSIM) {
            vsimSlot = -1
            isInAuth = false
        }
    }

    fun getApduLocal(apdu: APDU) {
        doGetApdu(apdu)
    }

    fun updateAuthState(state: AuthState) {
        apduHandler.onAuthStateChange(state)
    }


    override fun replyApdu(apduRsp: APDU) {
        super.replyApdu(apduRsp)
        //logd("reply apdu: $apduRsp")
        val param = apduRsp.args as SprdApduParam
        param.rsp = apduRsp.rsp
        //logd("param is $param")
        if (param.startLock) {
            synchronized(param.lock) {
                param.lock.notifyAll()
            }
        }
    }

    var isChannelOn = arrayOf<Boolean>(false, false)
    fun enableRSIMChannelReal(slot: Int) {
        if (slot >= MAX_SLOT || slot < 0) {
            throw Exception("slotid is over $MAX_SLOT $slot")
        }

        synchronized(isChannelOn) {
            if (!isChannelOn[slot]) {
                var ret = SprdApiInst.getInstance().setSimPowerStateForSlot(slot, false)
                logd("SprdNative.setSimPowerStateForSlot ${slot} false return $ret")
                isChannelOn[slot] = true
            }
        }
    }

    /**
     * 关通道的时候，判断如果设置过虚拟卡，则先要把虚拟卡关掉
     */
    fun disableRSIMChannelReal(slot: Int, force: Boolean) {
        if (slot >= MAX_SLOT || slot < 0) {
            throw Exception("slotid is over $MAX_SLOT $slot")
        }
        synchronized(isChannelOn) {
            if (isChannelOn[slot]) {
                var ret = 0
                val isVirtual = SprdApiInst.getInstance().vsimQueryVirtual(slot)
                logd("vsimQueryVirtual slot $slot return $isVirtual")
                if(isVirtual != 0){
                    ret = SprdApiInst.getInstance().vsimExit(slot)
                    logd("SprdNative.vsim_exit  slot " + slot + " ret " + ret)
                    ret = SprdApiInst.getInstance().vsimSetVirtual(slot, 0)
                    logd("SprdNative.vsim_set_virtual  slot " + slot + " 0 ret " + ret)
                }
                ret = SprdApiInst.getInstance().setSimPowerStateForSlot(slot, true)
                logd("SprdNative.setSimPowerStateForSlot ${slot} true return $ret")
                isChannelOn[slot] = false
            }
        }
    }

    override fun enableRSIMChannel(slot: Int) {
        super.enableRSIMChannel(slot)
        if (slot == -1) {
            for (i in 0..(MAX_SLOT - 1)) {
                enableRSIMChannelReal(i)
            }
        } else {
            enableRSIMChannelReal(slot)
        }
    }

    override fun disableRSIMChannel(slot: Int, force: Boolean) {
        super.disableRSIMChannel(slot, force)
        if (slot == -1) {
            for (i in 0..(MAX_SLOT - 1)) {
                disableRSIMChannelReal(i, force)
            }
        } else {
            disableRSIMChannelReal(slot, force)
        }
    }

    /**
     * 执行展讯cb方法的lock锁，确保所有cb成对执行，等待执行完后再进行native方法的调用
     */
    fun getLock(slot: Int): Any =
            when (slot) {
                Configuration.seedSimSlot -> cbLockId1
                Configuration.cloudSimSlot -> cbLockId2
                else -> Any()
            }
}
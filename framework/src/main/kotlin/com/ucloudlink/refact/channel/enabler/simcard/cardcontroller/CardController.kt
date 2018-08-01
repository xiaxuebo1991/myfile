package com.ucloudlink.refact.channel.enabler.simcard.cardcontroller

import android.content.Context
import android.os.Handler
import android.os.Message
import android.os.PowerManager
import android.util.SparseArray
import com.ucloudlink.framework.remoteuim.CardException
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardModel
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.toHex
import rx.Single
import rx.lang.kotlin.subscribeWith

/**
 * Created by jiaming.liang on 2018/1/5.
 *  initEnv() ,初始化环境，
 *  clearEnv(),强行关闭虚拟卡
storeCard(),保存卡信息到so库
deleCard(),删除数据库的卡
queryCard(),查询卡信息

connectTransfer
disconnectTransfer

enableRSIMChannel
disableRSIMChannel(force)

insertCard(),表示插卡到卡槽上，包括原来so的insert+pownOn动作
pullOutCard(),表示从卡槽拔出卡，包括remove+powndown动作
addAuthListen(),增加对卡鉴权的监听
removeAuthListen

启动卡，关卡
apdu防休眠

 */
//todo 用一个looper处理
abstract class ICardController : Handler() {
    val APP_CRASH_EVENT: Int = 0x10000

    protected val SIM_BUSY = byteArrayOf(0x6F, 0x00)

    protected var apduHandler: IAPDUHandler
    protected var platformTransfer: IPlatformTransfer

    private val pm = ServiceManager.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    protected val apduWakeLock: PowerManager.WakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "APDU LOCK_0")


    init {
        apduHandler = ApduController()
        platformTransfer = ServiceManager.systemApi.getPlatformTransfer()
        platformTransfer.apduHandler = apduHandler
    }

    val SUCCESS = 0

    /**
     * 初始化环境，可以强行关闭虚拟卡
     * @param slot 初始化卡槽Index ，传-1 初始化全部
     */
    open fun initEnv(slot: Int) {

    }


    /**
     * 清除虚卡环境，可以强行关闭虚拟卡
     * @param slot 初始化卡槽Index ，传-1 初始化全部
     */
    open fun clearEnv(slot: Int) {

    }

    /**
     * 保存卡信息到so库
     * @param card 要保存的卡
     * @return result 处理结果
     */
    open fun storeCard(card: Card): Int {
        return 0
    }

    /**
     * 删除so库中的卡信息
     * @param card 要删除的卡
     * @return result 处理结果
     */
    open fun deleCard(card: Card): Int {
        return 0
    }

    /**
     * 查询so库中的有没有这张卡
     * @param imsi 要查询的卡的imsi
     * @return result 处理结果
     */
    open fun queryCard(imsi: String): Int {
        return 0
    }

    /**
     * 启动卡
     * @param card 要启动的卡
     * @return 异步处理的订阅者
     */
    open fun insertCard(card: Card): Single<Any> {
        return Single.create { }
    }

    /**
     * 拔出卡
     * @param card 要拔的卡
     * @param:args 额外参数
     * @return 异步处理的订阅者
     */
    open fun pullOutCard(card: Card, isKeepChannel: Boolean = false, args: Any? = null): Single<Any> {
        return Single.create { }
    }

    /**
     * 连接平台通讯
     * @param slot 卡槽  -1 表示关闭所有卡槽的通道
     */
    open fun connectTransfer(slot: Int) {
        //TODO 之前虚卡关闭完成后，会断开平台通讯连接，现在改为由上层控制，
        // TODO 请注意虚卡关闭完成后调用disableRSIMChannel 与 disconnectTransfer
    }

    /**
     * 断开平台通讯连接
     * @param slot 卡槽  -1 表示关闭所有卡槽的通道
     */
    open fun disconnectTransfer(slot: Int) {

    }


    /**
     * 使能虚卡通道
     * @param slot 卡槽
     */
    open fun enableRSIMChannel(slot: Int) {

    }

    /**
     * 关闭虚卡通道
     * @param slot 卡槽  -1 表示关闭所有卡槽的通道
     * @param force 如果force==true ,会自动建立虚卡平台通讯，然后发送断开关闭虚卡通道消息，但不会自动关闭通讯连接
     *              如果force==false 只发送命令，这时候可能没连接
     */
    open fun disableRSIMChannel(slot: Int, force: Boolean = false) {

    }

    /**
     * 增加监听回调，返回一个回调的key 用于停止监听
     */
    open fun addAuthListen(listen: (AuthState) -> Unit): String {
        return apduHandler.addAuthListen(listen)
    }

    /**
     * 移除监听回调
     * @param listenKey 监听回调的key
     */
    open fun removeAuthListen(listenKey: String) {
        apduHandler.removeAuthListen(listenKey)
    }

    /**
     * @param msg 命令
     */
    open fun EventCmd(msg: Message) {
        sendMessage(msg)
    }

    /**
     * 获取一个消息体
     */
    open fun getCmdMsg(): Message {
        return obtainMessage()
    }
}

open class IPlatformTransfer {
    lateinit var apduHandler: IAPDUHandler

    open fun isReady(): Boolean {
        return true
    }
}

data class APDU(val slot: Int, val req: ByteArray, var rsp: ByteArray = byteArrayOf(0x6F, 0x00), var isAuth: Boolean = false, var args: Any? = null, var isSucc:Boolean = false)

open class CardController : ICardController() {

    protected val availableCard = SparseArray<Card?>()
    private var cloudsimAuthListenKey: String? = null


    override fun queryCard(imsi: String): Int {
        val ret = SoftSimNative.queryCard(imsi)
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            return SUCCESS
        }
        return ret
    }

    //insert + powerUp
    override fun insertCard(card: Card): Single<Any> {
        return Single.create<Any> { sub ->
            if (card.cardType == CardType.VSIM) {
                cloudsimAuthListenKey = addAuthListen(vsimSimAuthStateListen)
            }
            insertToSo(card)
            //power up
            powerUpCardWrapper(card).subscribe {
                sub.onSuccess(it)
            }
        }
    }

    open protected fun powerUpCardWrapper(card: Card): Single<Any> {
        return Single.create<Any> { sub ->
            powerUpCard(card)
            sub.onSuccess(Any())
        }
    }

    /**
     * 返回so库的处理结果
     */
    open protected fun powerUpCard(card: Card) {
        if (card.status >= CardStatus.POWERON) {
            throw CardException("card had power On")
        }
        val atr = ByteArray(128)
        val atrLen = IntArray(1)

        setCardType(card)

        val ret = SoftSimNative.powerUp(card.vslot, atr, atrLen)
        when (ret) {
            SoftSimNative.E_SOFTSIM_SUCCESS, SoftSimNative.E_SOFTSIM_CARD_POWERUP -> {
                val atrBytes = atr.copyOfRange(0, atrLen[0])
                card.atr = atrBytes
                availableCard.append(card.slot, card)
                apduHandler.initAPDUEnv(card)
            }
            else -> {
                throw CardException("powerUp card failed: $ret")
            }
        }
    }

    private fun setCardType(card: Card) {
        val cardType: ByteArray = ByteArray(1)
        val vsimType: ByteArray = ByteArray(1)

        val ret = SoftSimNative.queryCardType(card.imsi, cardType, vsimType)
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            when (cardType[0]) {
                SoftSimNative.MODE_SOFTSIM_GSM.toByte() -> {
                    card.cardModel = CardModel.GSM
                }
                SoftSimNative.MODE_SOFTSIM_UICC.toByte() -> {
                    card.cardModel = CardModel.UICC
                }
                else -> {
                    loge("setCardType ERR!!!")
                    card.cardModel = CardModel.UNKNOWMODEL
                }
            }

            val vsimTypeStr = kotlin.run {
                when (vsimType[0]) {
                    SoftSimNative.CARD_VSIM.toByte() -> return@run "VSIM"
                    SoftSimNative.CARD_SOFTSIM.toByte() -> return@run "SOFTSIM"
                    else -> return@run "UNKNOWN"
                }
            }
            logv("setCardType model:${card.cardModel} vsimType:$vsimTypeStr")
        } else {
            loge("SoftSimNative.queryCardType fail ret:$ret")
        }

    }

    private fun insertToSo(card: Card) {
        if (card.status >= CardStatus.INSERTED) {
            throw CardException("card had inserted")
        }
        //insert
        val vSlot = IntArray(1)
        val ret = SoftSimNative.insertCard(card.imsi, vSlot)

        when (ret) {
            SoftSimNative.E_SOFTSIM_SUCCESS, SoftSimNative.E_SOFTSIM_CARD_INSERTED
            -> {
                card.vslot = if (vSlot[0] == 0) 1 else vSlot[0]
                card.status = CardStatus.INSERTED
            }
            else -> {
                JLog.loge("insertCard card!!: $card ret:$ret  vSlot:${vSlot[0]}")
                throw CardException("insert card failed: $ret")
            }
        }
    }

    override fun pullOutCard(card: Card, isKeepChannel: Boolean, args: Any?): Single<Any> {
        return Single.create {
            removeCard(card,isKeepChannel)
            powerDownCard(card,isKeepChannel)
            it.onSuccess(Any())
        }
    }

    open protected fun removeCard(card: Card, keepChannel: Boolean) {
        if (card.status < CardStatus.INSERTED) {
            throw CardException("card status error: ${card.status}")
        }
        val ret = SoftSimNative.removeCard(card.vslot)
        if (ret != SoftSimNative.E_SOFTSIM_SUCCESS) {
            throw CardException("remove card failed: $ret")
        }

    }

    open protected fun powerDownCard(card: Card, keepChannel: Boolean) {
        if (card.status < CardStatus.POWERON) {
            throw CardException("card status error: ${card.status}")
        }

        val ret = SoftSimNative.powerDown(card.vslot)
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            card.status = CardStatus.INSERTED
            logd("powerDownCard success :$card")
            availableCard.remove(card.slot)
        } else if (ret == SoftSimNative.E_SOFTSIM_CARD_NOINSERTED) {
            loge("not inserted")
            availableCard.remove(card.slot)
        } else {
            throw CardException("powerDownCard failed: $ret")
        }
    }

    open protected fun doGetApdu(apdu: APDU) {
        if (!apduWakeLock.isHeld) {
            lockCPU(5000)
        }

        val card = availableCard[apdu.slot]
        apdu.rsp = SIM_BUSY
        card ?: return replyApdu(apdu)

        JLog.logv("${card.cardType} get Apdu request at slot(${card.slot}:${apdu.req.toHex()})")
        apduHandler.getAPDUResponse(card, apdu).subscribeWith {
            onSuccess {
                replyApdu(it)
            }
            onError {
                replyApdu(apdu)

                if (it is InvalidAPDUResponseException) {
                    notifyInvalidApdu()
                }
            }
        }
    }

    private val vsimSimAuthStateListen: (AuthState) -> Unit = { authState ->
        when (authState) {
            AuthState.AUTH_BEGIN -> {
                lockCPU(36000)
            }
            AuthState.AUTH_SUCCESS, AuthState.AUTH_FAIL -> {
                unLockCPU()
            }
        }
    }

    private fun notifyInvalidApdu() {
        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_APDU_INVALID)
    }

    open protected fun replyApdu(apduRsp: APDU) {
        /*由子类实现*/
    }

    protected fun lockCPU(timeout: Long = 36000) {
        apduWakeLock.setReferenceCounted(false)
        logd("lockCPU $apduWakeLock")
        apduWakeLock.acquire(timeout)
        
    }

    private fun unLockCPU() {
        logd("unLockCPU $apduWakeLock")
        apduWakeLock.release()
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            APP_CRASH_EVENT -> disableRSIMChannel(-1)
        }
    }
}

fun Card.softSimType(): Boolean {
    val softType = this.ki != null && this.opc != null
    return softType
}

class InvalidAPDUResponseException(val msg: String? = null) : Exception(msg)
package com.ucloudlink.refact.channel.enabler


import android.net.NetworkInfo
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import rx.Observable
import java.util.*

/**
 * Created by jiaming.liang on 2016/12/1.
 */
//enum class EnablerState {
//    DISABLED,
//    ENABLING,
//    SUSPENDED,
//    ENABLED,
//    DISABLING
//}

data class ExceptionReasn(var errorCode: Int = -1, var reason: String = "")

enum class EnablerException(var reason: ExceptionReasn = ExceptionReasn()) {
    EXCEP_CARD_PARAMETER_WRONG(), //准备卡时参数错误
    EXCEP_PHY_CARD_IS_NULL(), //物理卡为空
    EXCEP_PHY_CARD_DEFAULT_LOST(), //物理卡网络不通
    INSERT_SOFT_SIM_TIMEOUT(),
    /**
     * 插卡超时
     */
    ADD_SOFT_SIM_TIMEOUT(),
    /**
     * 卡ready超时
     */
    READY_TIMEOUT(),
    /**
     * 等待注册上营运商超时
     */
    INSERVICE_TIMEOUT(),
    /**
     * 等待拨号成功超时
     */
    CONNECT_TIMEOUT(),
    /**
     * 对应卡的数据开关没有打开
     */
    DATA_ENABLE_CLOSED(),
    /**
     * 漫游开关没打开
     */
    ROAM_DATA_ENABLE_CLOSED(),
    /**
     * 退出超时异常
     */
    CLOSE_CARD_TIMEOUT(),
    /**
     * sim carsh
     */
    SIM_CRASH(),

    /* 没有可用软卡*/
    EXCEPT_NO_AVAILABLE_SOFTSIM(),

    EXCEPTION_FAIL(),

    EXCEPTION_ENABLE_TIMEOUT(),

    EXCEPTION_DATA_ENABLE_CLOSED(),

    EXCEPTION_REG_DENIED(),
    EXCEPTION_REG_DENIED_NOT_DISABLE(),
    
    EXCEPTION_CARD_NET_FAIL(),//卡网络异常，可能是拨号成功，测速失败的

    EXCEPTION_USER_PHY_ROAM_DISABLE(),//用户不允许使用物理卡的数据漫游业务
    
    EXCEPTION_UNSUPPORT_CDMA_PHY_CARD(),//不支持CDMA卡作为种子卡
    
    EXCEPTION_PHY_CARD_MAY_UNAVAILABLE(),

    EXCEPTION_NO_NETWORK_SIGNAL(),

    EXCEPTION_LOCAL_DEPTH_OPT_CLOSE()
//    
//    EXCEPTION_REG_DENIED_ILLEGAL_MS,
//    EXCEPTION_REG_DENIED_IMSI_UNKNOWN_IN_VLR,
//    EXCEPTION_REG_DENIED_IMEI_NOT_ACCEPTED,
//    EXCEPTION_REG_DENIED_ILLEGAL_ME,
//    EXCEPTION_REG_DENIED_GPRS_SERVICES_NOT_ALLOWED,
//    EXCEPTION_REG_DENIED_GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED


}

enum class DataEnableEvent {
    EVENT_RELEASE_DUN_OUTSIDE,
    EVENT_NET_FAIL,
    EVENT_ATTACH_ENABLER, //关闭enabler
    EVENT_DETACH_ENABLER,
    EVENT_MODEM_RESET,
    EVENT_SOFTSIM_IMAGE_UPDATED,
    OUT_GOING_CALL,

    ENV_MCC_CHANGED,

    EVENT_PHY_TO_SOFT,
    ENENT_CARD_REJECT

}

/**
 * DataEnabler 的类型
 */
enum class DeType {
    WIFI,
    SIMCARD,
    BLUETOOTH
}

data class DataEnableInfo(val iccid:String, val imsi:String, val ip:Int, val lastException:EnablerException?,
                          val dataReg:Boolean, val dataRoam:Boolean, val voiceReg:Boolean, val voiceRoam:Boolean,
                          val singleStrength:Int, val dataConnect:Boolean)

interface IDataEnabler {
    fun getDeType(): DeType
    fun enable(cardList: ArrayList<Card>): Int//使能DataEnabler 参数:要使能的卡的数据,返回:结果0 表示参数可用,不代表使能成功
    fun disable(reason: String = "",isKeepChannel:Boolean = false): Int//关闭DataEnabler 返回表示要关闭的卡的subId
    //    fun getEnablerState(): EnablerState //获得DataEnabler的上一次通知的状态
//    fun enablerStatusObser(): Observable<EnablerState> //获得DataEnabler的观察者
    fun getCardState(): CardStatus //获得DataEnabler的中的卡的状态

    fun cardStatusObser(): Observable<CardStatus> //获得卡状态的观察者
    fun getNetState(): NetworkInfo.State//获得DataEnabler的中的网络的状态
    fun netStatusObser(): Observable<NetworkInfo.State>//获得网络状态的观察者
    fun exceptionObser(): Observable<EnablerException>//获得异常的观察者  //出现异常由这里上报
    fun switchRemoteSim(card: Card): Int//换卡的接口 入参:新卡的参数,返回:结果0 表示参数可用,不代表使能成功
    fun getCard(): Card //获得这个使能中的卡的对象
    fun isCardOn(): Boolean //表示是否有对应的卡
    fun isClosing(): Boolean //表示DataEnabler是否正在执行关闭动作
    fun isDefaultNet(): Boolean //表示是否为default网络,只有网络已经连通了（getNetState()==connected），这个值才有意义，否则无意义
    fun cloudSimRestOver() // 通知 DataEnabler 云卡reset完成
    fun notifyEventToCard(event: DataEnableEvent, obj: Any?)
    fun cardSignalStrengthObser():Observable<Int>
    fun getDataEnableInfo():DataEnableInfo
}
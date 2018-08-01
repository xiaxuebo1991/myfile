package com.ucloudlink.refact.product.mifi.cardselect


import android.content.Context
import android.net.NetworkInfo
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.google.gson.Gson
import com.ucloudlink.framework.protocol.protobuf.mifi.uc_glome_account
import com.ucloudlink.framework.protocol.protobuf.ruleItem
import com.ucloudlink.framework.protocol.protobuf.user_account_display_resp_type
import com.ucloudlink.framework.util.ApnUtil
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.StateMessageId
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.access.struct.LoginInfo
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.Configuration.RULE_ITEM_SPLIT
import com.ucloudlink.refact.config.Configuration.RULE_ITEM_VALUE_SPLIT
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.product.mifi.cardselect.CardSelectConfig.CONFIG_VSIM_SLOT
import com.ucloudlink.refact.product.mifi.cardselect.CardSelectConfig.SELECT_MODE_AUTO
import com.ucloudlink.refact.product.mifi.cardselect.CardSelectConfig.SELECT_MODE_MANUAL
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgEncode
import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.product.mifi.connect.struct.*
import com.ucloudlink.refact.product.mifi.downloadcfg.DownCfgService
import com.ucloudlink.refact.product.mifi.misc.MsgUpdate
import com.ucloudlink.refact.utils.FileIOUtils
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import java.io.File
import java.util.*


/**
 * 1、开机后先判断模式，如果是自动模式，直接走第二步，如果是普通模式，则进入第四步
 *
 * 2、启动后根据当前的卡状态选择对应的卡（智能模式）
 *      1）如果卡在本地，有限使用本地卡
 *      2）如果卡在漫游，有限使用vsim
 *      3）如果卡在跨境，本地到漫游，则从本地卡切换到漫游卡
 *      4）如果卡在跨境，漫游到本地，则从vsim切换到本地卡
 *
 * 问题：如何解决反复跨境的问题
 *
 * 3、在使用的过程中，可以切换模式（模式切换）
 *      1）智能模式切换到普通模式
 *          a）如果当前使用的卡和选择的卡是一致的，则只修改配置
 *          b）如果不一致，切换到云卡，则修改配置，启动云卡
 *          c）云卡切换到物理卡，则退云卡
 *          d）物理卡切换到物理卡，则切换dds
 *      2）普通模式切换到智能模式
 *      首先修改配置
 *          a）如果当前为云卡，则判断当前的状态是否需要启动云卡，如果需要启动云卡，则不处理，如果需要启动物理卡，则将云卡退出
 *          b）如果当前为物理卡，则判断最终的使用卡，如果是云卡，则启动云卡，如果是另外一张物理卡，则切换dds
 *
 * 问题：vsim卡切换到物理卡，是否需要给web回应？用户是否能操作页面。
 *
 *
 * 4、普通模式，第一次启动后判断配置（先执行第一步）
 *      1）如果上次配置的卡（物理卡）在位，则直接启动
 *      2）如果上次配置的卡（物理卡）不在位，判断是否还有别的物理卡，没有则直接启动云卡，否则弹portal
 *      3）如果上次配置的是vsim卡，则直接启动
 *      4）这种模式下，用户跨境不处理
 */
/**
 * Created by shiqianhua on 2018/2/3.
 */
class CardSelect(context: Context, mqttClient: UcMqttClient, looperIn: Looper) {
    val ctx = context
    val mqtt = mqttClient
    var userPhyCardState: UserPhyCardState? = null
    val looper = looperIn
    var curSlot = 0
    var userSimCfg = WebSimUserConfig()
    val PHY_SLOT1 = 1
    val PHY_SLOT2 = 2
//    var isPhyCardConnected:Boolean = false
    val downServer :DownCfgService = DownCfgService()
    var firstPortal = false
    var checkOver = false
    var cardListener : CardStateMonitor.CardStateListen? =null
    var toGetSubidRunnable : Runnable? =null
    var mcc : String ?=null
    /**
     * 返回值大于0 表示使用物理卡
     * == 0 （CONFIG_VSIM_SLOT） 表示使用vsim卡
     * < 0 表示失败 其中== -ErrorCode.LOCAL_USER_PHY_CARD_NOT_EXIST 表示弹portal让用户选择
     *
     */
    val networkListen: CardStateMonitor.NetworkStateListen = object : CardStateMonitor.NetworkStateListen {
        override fun hashCode(): Int {
            return 456789987
        }

        override fun NetworkStateChange(ddsId: Int, networkState: NetworkInfo.State, type: Int, ifName: String, isExistIfNameExtra: Boolean, subId: Int) {
            JLog.logd("NetStatuChange isNetAvailable:$ddsId, $networkState, $type, $ifName, $isExistIfNameExtra, $subId")
            if(networkState == NetworkInfo.State.CONNECTED){
                JLog.logd("network ok ")
                if(ServiceManager.cloudSimEnabler.isCardOn() && (ddsId == ServiceManager.cloudSimEnabler.getCard().slot)) {
                    JLog.logd("account display")
                    Requestor.getUserAccountDisplay(android.os.SystemProperties.get("ucloud.oem.conf.language"), 30).subscribe({
                        if (it is user_account_display_resp_type) {
                            JLog.logd("get user account display  " + it)
                            if (it.errorCode == 100) {
                                val accountServer = it.user_account_combo
                                var accountWebList: ArrayList<uc_glome_account> = ArrayList()
                                for (account in accountServer) {
                                    var isUsed = false
                                    if (account.isused == 1) {
                                        isUsed = true
                                    }
                                    val accountItem = uc_glome_account(account.name, account.intflowbyte, account.surplusflowbyte, account.start_time, account.end_time, isUsed)
                                    accountWebList.add(accountItem)
                                }
                                val acount_info = UserAccountInfo(it.amount, it.rate, it.country_name, it.user_account_combo.size, accountWebList, 0,
                                        PERSONAL, it.accumulated_flow, "", it.dispaly_flag, it.cssType, it.unit)
                                MsgEncode.sendWebUserAccountInfo(mqttClient, acount_info)
                            }
                        } else {
                            JLog.loge("get user account display fail")
                        }
                    }, { t ->
                        t.printStackTrace()
                        JLog.loge("get user account display fail:" + t.message)
                    })
                    MsgUpdate.updateServiceOk(ctx, mqtt)
                    //检测下载更新定制文件
                    JLog.logd("supdate xml")
                    downServer.onHandleIntent()
                    JLog.logd("ServiceManager.cloudSimEnabler.getCard().slot : "+ServiceManager.cloudSimEnabler.getCard().slot +" ServiceManager.seedCardEnabler.getCard().slot :"+ServiceManager.seedCardEnabler.getCard().slot)
                    MsgUpdate.updateServiceOk(ctx, mqtt)
                    val telephoneManager = TelephonyManager.from(ServiceManager.appContext)
                    JLog.logd("ServiceManager.cloudSimEnabler.getCard().subId : "+ServiceManager.cloudSimEnabler.getCard().subId + "ServiceManager.seedCardEnabler.getCard().subId : "+ServiceManager.seedCardEnabler.getCard().subId)
                    var netRat = telephoneManager.getNetworkType(ServiceManager.cloudSimEnabler.getCard().subId)
                    JLog.logd("netRat : "+netRat)
                    MsgEncode.sendCloudSimRat(mqtt,netRat)
                }
            } else if(networkState == NetworkInfo.State.DISCONNECTED && ddsId != Configuration.seedSimSlot ){
                val datacall = MsgDatacallInfo(0, "IPV4", "", "", "", "", 1)
                MsgEncode.sendLedDatacall(mqttClient, datacall)
                MsgEncode.sendWebDatacall(mqttClient, datacall)
                MsgEncode.sendFotaDatacall(mqttClient, datacall)
            }
        }
    }
    init {
        JLog.logd("init CardSelect")
        ServiceManager.simMonitor.addNetworkStateListen(networkListen)
        var phone=ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phone.listen(PhoneListener(), PhoneStateListener.LISTEN_SERVICE_STATE )
    }

    fun getCurrentCardStateList(): ArrayList<CardSave> {
        val list = ArrayList<CardSave>()
        for (card in CardSelectConfig.phyCardSlotMap) {
            logd("card is $card")
            val cardSave = CardSave(logicSlot = card.logicSlot, realSlot = card.realSlot)
            val lastInfo = ServiceManager.phyCardWatcher.getLastInfoBySlot(card.realSlot)
            JLog.logd("last info == "+lastInfo)
            if (lastInfo != null) {
                cardSave.isOn = lastInfo.isOn
                cardSave.imsi = lastInfo.imsi
                val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if(TextUtils.isEmpty(mcc)){
                    if(OperatorNetworkInfo.mccmnc!=null){
                        if(OperatorNetworkInfo.mccmnc.length >= 3 &&  !OperatorNetworkInfo.mccmnc.equals("00000") && !OperatorNetworkInfo.mccmnc.equals("000000")){
                            mcc=OperatorNetworkInfo.mccmnc!!.substring(0, 3)
                        }
                    }else{
                        val seedOperator = mPhone.getNetworkOperatorForPhone(Configuration.seedSimSlot)
                        if(seedOperator!=null&&seedOperator.length >= 3 &&  !seedOperator.equals("00000") && !seedOperator.equals("000000")){
                            mcc=seedOperator!!.substring(0, 3)
                        }
                    }
                }
                JLog.logd("mcc == ${mcc}" )
                JLog.logi("processAutoMode lastInfo:${lastInfo}")
                if(lastInfo!=null&&lastInfo.imsi != null&&lastInfo.imsi!!.length>=3){
                    if(!TextUtils.isEmpty(mcc) &&
                            !lastInfo.imsi!!.substring(0, 3).equals(mcc)){
                        cardSave.roam = true
                    }else{
                        cardSave.roam = ServiceManager.phyCardWatcher.isCardRoam(card.realSlot)
                    }
                }else{
                    cardSave.roam = ServiceManager.phyCardWatcher.isCardRoam(card.realSlot)
                }
            } else {
                cardSave.isOn = false
                cardSave.imsi = ""
                cardSave.roam = false
            }
            list.add(cardSave)
        }
        logd("getCurrentCardStateList ${list}")
        return list
    }

    fun getSaveCardStateList(): ArrayList<CardSave> {
        val stateStr = CardSelectConfig.phyCardStatus
        val list = ArrayList<CardSave>()
        if (stateStr.length == 0) {
            logd("save list is null $stateStr")
            return list
        }
        try {
            val orgList = Gson().fromJson(stateStr, Array<CardSave>::class.java)
            for (o in orgList) {
                list.add(o)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logd("get save list $list")
        return list
    }

    fun saveCardStateList(list: ArrayList<CardSave>): Boolean {
        val array = list.toArray()
        try {
            val str = Gson().toJson(array)
            logd("saveCardStateList $str")
            CardSelectConfig.phyCardStatus = str
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 如果lastState为空，则表示只上报当前的sim状态，curState必须不能为null
     * 格式 "1,1,1" "0,0,0"
     */
    fun updateCardStatus(lastState: ArrayList<CardSave>?, curState: ArrayList<CardSave>) {
        logd("updateCardStatus $lastState $curState")
//        var userSimCfg = WebSimUserConfig()
        userSimCfg.dataChannel = kotlin.run {
            if (CardSelectConfig.selectMode == SELECT_MODE_AUTO) {
                return@run userSimCfg.dataChannel
            } else {
                when (CardSelectConfig.configCardSlot) {
                    CONFIG_VSIM_SLOT -> return@run DATA_CHANNEL_GLOCALME
                    PHY_SLOT1 -> return@run DATA_CHANNEL_SIM1
                    PHY_SLOT2 -> return@run DATA_CHANNEL_SIM2
                    else -> return@run DATA_CHANNEL_GLOCALME
                }
            }
        }
        val oldStateList = lastState
        val simStatList = curState
        for (simStat in simStatList) {
            logd("process cur card $simStat")
            val logicSlot = simStat.logicSlot
            val realSlot = simStat.realSlot
            val state = simStat.isOn
            if (logicSlot == PHY_SLOT1) {
                userSimCfg.sim1Exist = if (state) 1 else 0
                if (state) {
//                    userSimCfg.imsi1 = ServiceManager.systemApi.getSubscriberIdBySlot(realSlot)
                    userSimCfg.imsi1 = simStat.imsi
                    val tsimState = ServiceManager.systemApi.getSimState(realSlot)
                    userSimCfg.pin1 = tsimState == TelephonyManager.SIM_STATE_PIN_REQUIRED
                    userSimCfg.puk1 = tsimState == TelephonyManager.SIM_STATE_PUK_REQUIRED
                    userSimCfg.sim1New = true
                    if (oldStateList != null && oldStateList.size > 0) {
                        for (old in oldStateList) {
                            if (old.realSlot == realSlot && (old.imsi == simStat.imsi)) {
                                userSimCfg.sim1New = false
                            }
                        }
                    }
                    userSimCfg.roam1 = if(simStat.roam) 1 else 0
                }
            } else if (logicSlot == PHY_SLOT2) {
                userSimCfg.sim2Exist = if (state) 1 else 0
                if (state) {
//                    userSimCfg.imsi2 = ServiceManager.systemApi.getSubscriberIdBySlot(realSlot)
                    userSimCfg.imsi2 = simStat.imsi
                    val tsimState = ServiceManager.systemApi.getSimState(realSlot)
                    userSimCfg.pin2 = tsimState == TelephonyManager.SIM_STATE_PIN_REQUIRED
                    userSimCfg.puk2 = tsimState == TelephonyManager.SIM_STATE_PUK_REQUIRED
                    userSimCfg.sim2New = true
                    if (oldStateList != null && oldStateList.size > 0) {
                        for (old in oldStateList) {
                            if (old.realSlot == realSlot && (old.imsi == simStat.imsi)) {
                                userSimCfg.sim2New = false
                            }
                        }
                    }
                    userSimCfg.roam2 = if(simStat.roam) 1 else 0
                }
            }
        }
        MsgEncode.sendWebCardStatus(mqtt, userSimCfg)
    }

    private fun isPhyCardOnByStateString(stateList: ArrayList<CardSave>): Boolean {
        for (i in stateList) {
            if (i.isOn) {
                return true
            }
        }
        return false
    }

    private fun checkHaveNewPhySim(lastState: ArrayList<CardSave>?, curState: ArrayList<CardSave>): Boolean {
        logd("checkHaveNewPhySim $lastState, $curState")
        if (lastState!!.size == 0) {
            return true
        }

        for (cur in curState) {
            var find = false
            for (last in lastState) {
                if (last.logicSlot == cur.logicSlot) {
                    if (cur.isOn && !last.isOn) {
                        return true
                    }

                    if (cur.isOn && last.isOn && cur.imsi != last.imsi) {
                        return true
                    }

                    if (cur.isOn && last.isOn && cur.imsi == last.imsi && cur.roam && !last.roam) {
                        return true
                    }
                    find = true
                }
            }

            if (cur.isOn && !find) {
                return true
            }
        }
        return false
    }

    /**
     * 智能选卡，配置相关参数
     *
     * @param lastCardState 上报用，首次null
     * @param perLogicSlot 代表执行前选择的卡，0云卡 >0物理卡，
     */
    fun processAutoMode(curCardState: ArrayList<CardSave>,perLogicSlot: Int):Int{
        updateCardStatus(null, curCardState)
        var ret = 0
        for (slot in CardSelectConfig.getRealPhySlotList()) {
            val lastInfo = ServiceManager.phyCardWatcher.getLastInfoBySlot(slot)
            var isOn = false
            if (perLogicSlot == CONFIG_VSIM_SLOT) {
                if (lastInfo != null && lastInfo.isOn) {
                    isOn = true
                }
            } else
                if (perLogicSlot != CONFIG_VSIM_SLOT) {
                    isOn = ServiceManager.phyCardWatcher.isCardOn(slot)
                }
            var isRoam=false
            JLog.logi("OperatorNetworkInfo.mccmnc:${OperatorNetworkInfo.mccmnc}  lastInfo:$lastInfo")
            if(TextUtils.isEmpty(OperatorNetworkInfo.mccmnc)||
                    OperatorNetworkInfo.mccmnc.length<3||lastInfo == null || lastInfo.imsi == null || lastInfo.imsi!!.length < 3 ){
                isRoam=ServiceManager.phyCardWatcher.isCardRoam(slot)
            } else {
                JLog.logi("processAutoMode lastInfo:${lastInfo}")
                if (!lastInfo.imsi!!.substring(0, 3).equals(OperatorNetworkInfo.mccmnc.substring(0,3))) {
                    isRoam = true
                }
            }
            var isPin=false
            val tsimState = ServiceManager.systemApi.getSimState(slot)
            if (tsimState == TelephonyManager.SIM_STATE_PIN_REQUIRED || tsimState == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                isPin = true
            }
            JLog.logd("processAutoMode isCardOn:$isOn isRoam:$isRoam isPin:$isPin")
            if (isOn && !isRoam && !isPin) {
                userSimCfg.dataChannel = CardSelectConfig.getLogicSlotByRealSlot(slot)
                MsgEncode.sendWebCardStatus(mqtt,userSimCfg)
                if(userSimCfg.dataChannel == perLogicSlot){
                    //卡相同则不处理
                    return userSimCfg.dataChannel
                }
                if(ret == 0){
                    ret = userSimCfg.dataChannel
                }
            }
        }
        if(ret > 0){
            JLog.logd("use user sim card")
            userSimCfg.dataChannel = ret
            curSlot = ret
            CardSelectConfig.configCardSlot =  ret
            return CardSelectConfig.configCardSlot
        }
        // 使用vsim卡
        userSimCfg.dataChannel = DATA_CHANNEL_GLOCALME
        curSlot = CardSelectConfig.CONFIG_VSIM_SLOT
        CardSelectConfig.configCardSlot =  CONFIG_VSIM_SLOT
        return CardSelectConfig.configCardSlot
    }

    /**
     * return 0, use vsim
     * return > 0 use phy
     * return < 0 error or wait for user choose card
     */
    private fun getUseCardSlot(): Int {
        val lastSaveState = getSaveCardStateList()
        val curCardState = getCurrentCardStateList()
        JLog.logd("selectMode == " + CardSelectConfig.selectMode)
        if (CardSelectConfig.selectMode == SELECT_MODE_AUTO) {
            var preRet = CardSelectConfig.configCardSlot
            var ret = processAutoMode(curCardState,-1)
            if(ret != CONFIG_VSIM_SLOT){
                setUsePhyCardFromReboot()
            }
            return ret
        } else { // manual
            logd("last save state :$lastSaveState $curCardState")

            if (checkHaveNewPhySim(lastSaveState, curCardState)) {
                // 弹portal让用户选择对应的sim卡
                logd("get new card!")
                if (isPhyCardOnByStateString(curCardState)) {
                    MsgEncode.sendLedAbnormal(mqtt, true, 1)
                    MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_SIM_SELECT, 0, 0, 0, ""))
                    updateCardStatus(lastSaveState, curCardState)
                    return -1
                } else {
                    CardSelectConfig.configCardSlot = CONFIG_VSIM_SLOT
                    userSimCfg.dataChannel = DATA_CHANNEL_GLOCALME
                    curSlot = CONFIG_VSIM_SLOT
                    updateCardStatus(lastSaveState, curCardState)
                    return CONFIG_VSIM_SLOT
                }
            } else {
                val slot = CardSelectConfig.configCardSlot
                logd("config slot $slot")
                if (slot == CONFIG_VSIM_SLOT) { // vsim
                    CardSelectConfig.configCardSlot = CONFIG_VSIM_SLOT
                    userSimCfg.dataChannel = DATA_CHANNEL_GLOCALME
                    curSlot = CONFIG_VSIM_SLOT
                    updateCardStatus(null, curCardState)
                    return CONFIG_VSIM_SLOT
                } else {
                    val realSlot = CardSelectConfig.getRealPhySlot(slot)
                    if (realSlot < 0) {
                        MsgEncode.sendLedAbnormal(mqtt, true, 1)
                        MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_SYSTEM_BUSY, ErrorCode.LOCAL_CONFIG_ERROR, 0, 1, ErrorCode.getErrMsgByCode(ErrorCode.LOCAL_CONFIG_ERROR)))
                        updateCardStatus(null, curCardState)
                        return -1
                    }
                    //会不在位，checkHaveNewPhySim只是判断与当前卡槽不同的状态，如果当前卡槽没卡则会false
                    // sim卡不在位，需要弹portal,重新选择sim卡
                    if (!ServiceManager.phyCardWatcher.isCardOn(realSlot)||ServiceManager.systemApi.getSubIdBySlotId(realSlot)<=0) {
                        CardSelectConfig.configCardSlot = CONFIG_VSIM_SLOT
                        userSimCfg.dataChannel = DATA_CHANNEL_GLOCALME
                        curSlot = CONFIG_VSIM_SLOT
                        updateCardStatus(null, curCardState)
                        return CONFIG_VSIM_SLOT
                    }

                    userSimCfg.dataChannel = CardSelectConfig.configCardSlot
                    curSlot = CardSelectConfig.configCardSlot
                    setUsePhyCardFromReboot()
                    updateCardStatus(null, curCardState)
                    return slot
                }
            }
        }
    }

    fun checkExitVsim(): Boolean {
        if (ServiceManager.accessEntry.accessState.isServiceRunning) {
            logd("service is running!, so exit!")
            ServiceManager.accessEntry.logoutReq(6)
            return true
        }
        return false
    }

    fun checkVsimLogin(): Boolean {
        if (ServiceManager.accessEntry.accessState.isServiceRunning == false) {
            logd("service is not running!, so login!!")
            val loginInfo = LoginInfo(RunningStates.getUserName(), RunningStates.getPassWord())
            ServiceManager.accessEntry.accessState.sendMessage(StateMessageId.USER_LOGIN_REQ_CMD, loginInfo)
            return true
        }
        return false
    }

    fun setPhyCard(dataChannel: Int): Int {
        logd("recv setPhyCard $dataChannel   pre ${userSimCfg.dataChannel}  firstPortal:$firstPortal")
        if (!firstPortal) {
            if (dataChannel == DATA_CHANNEL_SMART && CardSelectConfig.selectMode == SELECT_MODE_AUTO ||
                    dataChannel == userSimCfg.dataChannel) {
                //同样的操作
                MsgEncode.sendWebCardStatus(mqtt, userSimCfg)
                return 0
            }
        }
        userPhyCardState?.deInit()
        userPhyCardState = null
        var preRet = if(firstPortal){-1}else CardSelectConfig.configCardSlot
        firstPortal = false
        val curCardStateList = getCurrentCardStateList()
        JLog.logi("configCardSlot ${preRet}")
        when (dataChannel) {
            DATA_CHANNEL_GLOCALME -> {
                CardSelectConfig.selectMode = SELECT_MODE_MANUAL
                CardSelectConfig.configCardSlot = CONFIG_VSIM_SLOT
                userSimCfg.dataChannel = DATA_CHANNEL_GLOCALME
                curSlot = CONFIG_VSIM_SLOT
                if (preRet != CardSelectConfig.configCardSlot) {
                    MsgUpdate.updateServiceAb(mqtt)
                    setUseVsimCard()
                }
            }
            DATA_CHANNEL_SIM1 -> {
                CardSelectConfig.selectMode = SELECT_MODE_MANUAL
                CardSelectConfig.configCardSlot = DATA_CHANNEL_SIM1
                userSimCfg.dataChannel = DATA_CHANNEL_SIM1
                curSlot = DATA_CHANNEL_SIM1
                if (preRet != CardSelectConfig.configCardSlot){
                    if(preRet == DATA_CHANNEL_GLOCALME){
                        MsgUpdate.updateServiceAb(mqtt)
                    }
                    setUsePhyCardFromWeb(preRet == CONFIG_VSIM_SLOT)
                }
            }
            DATA_CHANNEL_SIM2 -> {
                CardSelectConfig.selectMode = SELECT_MODE_MANUAL
                CardSelectConfig.configCardSlot = DATA_CHANNEL_SIM2
                userSimCfg.dataChannel = DATA_CHANNEL_SIM2
                curSlot = DATA_CHANNEL_SIM2
                if (preRet != CardSelectConfig.configCardSlot) {
                    if (preRet == DATA_CHANNEL_GLOCALME) {
                        MsgUpdate.updateServiceAb(mqtt)
                    }
                    setUsePhyCardFromWeb(preRet == CONFIG_VSIM_SLOT)
                }
            }
            DATA_CHANNEL_SMART -> {
                CardSelectConfig.selectMode = SELECT_MODE_AUTO
                val ret = processAutoMode(curCardStateList, CardSelectConfig.configCardSlot)
                JLog.logi("processAutoMode ret ${ret}")
                if (preRet != ret) {
                    if (ret == CardSelectConfig.CONFIG_VSIM_SLOT) {
                        MsgUpdate.updateServiceAb(mqtt)
                        setUseVsimCard()
                    } else {
                        if(preRet == DATA_CHANNEL_GLOCALME){
                            MsgUpdate.updateServiceAb(mqtt)
                        }
                        setUsePhyCardFromWeb(preRet == CONFIG_VSIM_SLOT)
                    }
                }
            }
            else -> {
                // do thing!
            }
        }
        saveCardStateList(curCardStateList)
        updateCardStatus(null, curCardStateList)
        MsgEncode.sendWebCardStatus(mqtt,userSimCfg)
        return 0
    }


    private fun setUseVsimCard(){
        removeListener()
        JLog.logd("setUseVsimCard() curSlot=$curSlot configCardSlot:${CardSelectConfig.configCardSlot} userSimCfg:$userSimCfg")

        if (checkVsimLogin()) {
            logd("use vsim so close cloudsim phy card protocol stack! user select!")
            ServiceManager.systemApi.setDefaultDataSlotId(Configuration.seedSimSlot)
            //Bug 30358
            ServiceManager.cardController.enableRSIMChannel(Configuration.cloudSimSlot)
        }

        logd("smart has selected vism crad,now cloudSim netstate:" +ServiceManager.cloudSimEnabler.getNetState())
        if (ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED){
            MsgUpdate.updateServiceOk(ctx, mqtt)
        }else{
            MsgEncode.sendLedServiceStart(mqtt)
        }
        userPhyCardState?.deInit()
        userPhyCardState=null
    }

    private fun setUsePhyCardFromReboot(){
        setUsePhyCard(false,true)
        MsgEncode.sendWebCardStatus(mqtt,userSimCfg)
    }

    private fun setUsePhyCardFromWeb(useLastInfoImsi : Boolean){
        setUsePhyCard(useLastInfoImsi,false)
    }

    /**
     * @param useLastInfoImsi 如果上一次是云卡，则使用lastinfo中的imsi
     */
    private fun setUsePhyCard(useLastInfoImsi: Boolean, fromReboot: Boolean){
        JLog.logd("setUsePhyCard() curSlot=$curSlot configCardSlot:${CardSelectConfig.configCardSlot} userSimCfg:$userSimCfg useLastInfoImsi:$useLastInfoImsi fromReboot:$")
        removeListener()
        checkExitVsim()
        var realSlot = CardSelectConfig.getRealPhySlot(curSlot)
        // 设置dds，保证sim卡能运行
        // 33263 需要保证卡load（能获取到subid才能最终设置成功，设置dds实际上是转化成subid在进行设置的,广播也可能先到这里在到rom，而且代码收到imsi也认为是load）
        if (!fromReboot||ServiceManager.systemApi.getSubIdBySlotId(realSlot) > 0) {
            ServiceManager.systemApi.setDefaultDataSlotId(realSlot)
        } else {
            startListener()
        }
        logd("setUsePhyCard useLastInfoImsi:$useLastInfoImsi")
        if (useLastInfoImsi) {
            var imsi = ServiceManager.phyCardWatcher.getImsiBySlot(realSlot)
            val lastInfo = ServiceManager.phyCardWatcher.getLastInfoBySlot(realSlot)
            if (imsi != null && imsi!!.length != 0) {
                userPhyCardState = UserPhyCardState(ctx, mqtt, realSlot, this, looper, imsi)
            } else if (lastInfo != null && lastInfo.imsi != null && lastInfo.imsi!!.length != 0) {
                userPhyCardState = UserPhyCardState(ctx, mqtt, realSlot, this, looper, lastInfo.imsi!!)
            } else {
                logd("both imsi and lastimsi is empty!!!")
            }
        } else {
            userPhyCardState = UserPhyCardState(ctx, mqtt, realSlot, this, looper, ServiceManager.systemApi.getSubscriberIdBySlot(realSlot))
        }
        val telephoneManager = TelephonyManager.from(ServiceManager.appContext)
        var netRat = telephoneManager.getNetworkType(ServiceManager.phyCardWatcher.getSubIdBySlot(realSlot))
        MsgEncode.sendCloudSimRat(mqtt,netRat)
    }

    /**
     * 方案包括下一次load（代码中）状态的监听，即系统的load监听，以及每5秒获取一次subid
     */
    private fun startListener() {
        logd("startListener! wait phy load")
        cardListener = CardStateMonitor.CardStateListen { slotId, subId, state ->
            logd("startListener! new phy state slotId:$slotId subId:$subId state:$state")
            if (slotId == curSlot && curSlot != CONFIG_VSIM_SLOT && subId > 0 && state == CardStateMonitor.SIM_STATE_LOAD) {
                var realSlot = CardSelectConfig.getRealPhySlot(curSlot)
                ServiceManager.systemApi.setDefaultDataSlotId(realSlot)
                removeListener()
            }
        }
        ServiceManager.simMonitor.addCardStateListen(cardListener)
        if (toGetSubidRunnable == null) {
            toGetSubidRunnable = Runnable {
                var realSlot = CardSelectConfig.getRealPhySlot(curSlot)
                var subId=ServiceManager.systemApi.getSubIdBySlotId(realSlot)
                logd("startListener! next 5s  subId:$subId")
                if ( subId> 0) {
                    ServiceManager.systemApi.setDefaultDataSlotId(realSlot)
                    removeListener()
                    return@Runnable
                }
                ServiceManager.handler.postDelayed(toGetSubidRunnable, 5000)
            }
        }
        ServiceManager.handler.postDelayed(toGetSubidRunnable, 5000)
    }

    private fun removeListener() {
        logd("removeListener!")
        if (toGetSubidRunnable != null) {
            logd("removeListener! remove toGetSubidRunnable")
            ServiceManager.handler.removeCallbacks(toGetSubidRunnable)
        }
        if (cardListener == null)
            return
        val _cardListener = cardListener
        cardListener = null
        Thread{
            logd("removeListener! remove cardListener")
            ServiceManager.simMonitor.removeStatuListen(_cardListener)
        }.start()
    }

    /**
     * 在服务启动前调用此方法，如果返回0，则表示使用vsim卡，其他情况，业务不处理
     */
    fun checkCardSelect(): Int {
        logd("checkCardSelect!")
        val ret = getUseCardSlot()
        logd("checkCardSelect! ret$ret")
        if(ret == -1){
            //首次选择
            firstPortal = true
        }
        // Bug 30358
        if (ret == 0) {
            logd("use vsim so close cloudsim phy card protocol stack!")
            ServiceManager.cardController.enableRSIMChannel(Configuration.cloudSimSlot)
        }
        if(ret >= 0) {
            val curCardState = getCurrentCardStateList()
            saveCardStateList(curCardState)
        }
        checkOver=true
        return ret
    }

    /**
     * 设置物理卡apn， 0 成功，其他失败
     */
    fun setPhySimApn(slot: Int, apn: Apn?): Int {
        logd("setPhySimApn $slot $apn")
        if (apn==null||!CardSelectConfig.contansPhySlot(slot)) {
            return ErrorCode.LOCAL_CONFIG_ERROR
        }

        val realSlot = CardSelectConfig.getRealPhySlot(slot)
        val imsi = ServiceManager.phyCardWatcher.getImsiBySlot(realSlot)
        logd("setPhySimApn imsi:$imsi")
        if (imsi == null || imsi.length < 5) {
            loge("imsi invalid $imsi")
            return ErrorCode.LOCAL_SYSTEM_ERROR
        }

        apn.numeric = imsi.substring(0, 5)
        apn.mcc = apn.numeric.substring(0, 3)
        apn.mnc = apn.numeric.substring(3, 5)

        if (!ServiceManager.phyCardWatcher.isCardOn(realSlot)) {
            return ErrorCode.LOCAL_PHY_CARD_NOT_EXIST
        }

        val subId = ServiceManager.phyCardWatcher.getSubIdBySlot(realSlot)
        if (subId == -1) {
            return ErrorCode.LOCAL_USER_PHY_SUBID_INVALID
        }


        val id = ApnUtil.InsertApnToDatabaseIfNeed(ctx, apn)
        //#32714 SIM卡管理中实体卡apn设置不生效
        if (id == null) {
            loge("insert apn to database failed!")
            return ErrorCode.LOCAL_SYSTEM_ERROR
        }
        logd("update apn id:$id subId:$subId realSlot:$realSlot apn:$apn")
        ApnUtil.selectApnBySubId(ctx, id, subId, true)

        return 0
    }

    /**
     * return true,表示需要重登陆
     * return false 表示不处理，智能选卡最终会设置启动云卡或关闭云卡操作，外层不需要处理。使用物理卡也不需要处理
     */
    fun mccChange(newmcc: String): Boolean {
        logd("mccChange! CardSelectConfig.selectMode:${CardSelectConfig.selectMode} CardSelectConfig.configCardSlot:${CardSelectConfig.configCardSlot}")
        if (mcc == newmcc) {
            logd("mccChange! newmcc equals curmcc")
            return false
        }
        mcc = newmcc
        if (CardSelectConfig.selectMode == SELECT_MODE_AUTO) {
            var hasNextPhyCard = false
            for (card in CardSelectConfig.phyCardSlotMap) {
                val lastInfo = ServiceManager.phyCardWatcher.getLastInfoBySlot(card.realSlot)
                logd("mccChange lastInfo $lastInfo  new mcc $mcc isRoam:${ServiceManager.phyCardWatcher.isCardRoam(card.realSlot)}")
                    if (lastInfo != null && lastInfo.imsi != null && lastInfo.imsi!!.length >= 3) {
                        if (lastInfo.imsi!!.substring(0, 3).equals(mcc)) {
                            logd("slot ${card.realSlot} is localcard use it")
                            hasNextPhyCard = true
                        }
                }
            }
            logd("mccChange! hasNextPhyCard:$hasNextPhyCard configCardSlot:${CardSelectConfig.configCardSlot}")
            if (hasNextPhyCard || !hasNextPhyCard && CardSelectConfig.configCardSlot != CardSelectConfig.CONFIG_VSIM_SLOT) {
                //卡存在或者卡不存在且当前是物理卡
                var preRet = CardSelectConfig.configCardSlot
                var ret = processAutoMode(getSaveCardStateList(), preRet)
                if (preRet != ret) {
                    if (ret == CardSelectConfig.CONFIG_VSIM_SLOT) {
                        logd("use vism")
                        setUseVsimCard()
                        return false
                    } else {
                        logd("use physim")
                        setUsePhyCardFromWeb(preRet == CONFIG_VSIM_SLOT)
                        return false
                    }
                }else {
                    if(ret != CardSelectConfig.CONFIG_VSIM_SLOT){
                        logd("relogin because vsim need change")
                        return false
                    }
                }

            }
            MsgEncode.sendWebCardStatus(mqtt,userSimCfg)

            logd("no local sim! so use vsim")
            return true
        } else {
            /**
             * 用户跨境，还是按照之前的选卡来走。。。，不支持热插拔，不判断卡是否在位
             */
            if (CardSelectConfig.configCardSlot == CONFIG_VSIM_SLOT) {
                return true
            }
            return false
        }

        return true
    }

    private inner class PhoneListener : PhoneStateListener() {
        override fun onServiceStateChanged(serviceState: ServiceState?) {
            super.onServiceStateChanged(serviceState)
            logd("PhoneListener onServiceStateChanged! $serviceState")
            if (checkOver&&!ServiceManager.seedCardEnabler.isCardOn()) {
                if (serviceState != null && !TextUtils.isEmpty(serviceState.getOperatorAlphaLong())) {
                    var seedOperator = serviceState.getOperatorNumeric()
                    if (!TextUtils.isEmpty(seedOperator)
                            && seedOperator.length > 3 && !seedOperator.equals("00000") && !seedOperator.equals("000000")) {
                        logd("PhoneListener onServiceStateChanged! mcc ok")
                        if (!TextUtils.isEmpty(seedOperator)
                                && seedOperator.length > 3) {
                            mccChange(seedOperator!!.substring(0, 3))
                        }
                    }
                }
            }

        }
    }

    fun setRoamEnable(enable: Boolean): Boolean {
        logd("set roam!!! ${CardSelectConfig.configCardSlot} $enable $curSlot")
        if (curSlot == PHY_SLOT1 || curSlot == PHY_SLOT2) {
            val realSlot = CardSelectConfig.getRealPhySlot(curSlot)
            val subId = ServiceManager.phyCardWatcher.getSubIdBySlot(realSlot)
            if (subId >= 0) {
                logd("set subid $subId roam $enable slot $realSlot")
                Settings.Global.putInt(ctx.contentResolver, Settings.Global.DATA_ROAMING + subId, if(enable) 1 else 0)
                val ret = ServiceManager.systemApi.setDataRoaming(subId, enable)
                if (ret == 0) {
                    return true
                }
            }
        }
        return false
    }


    fun ruleLog(log: String) {
        logd("[reorderByRule]", log)
    }

    fun ruleLoge(log: String) {
        loge("[reorderByRule]", log)
    }


    /**
     * 根据规则文件，重新排序cardlist
     *
     */
    fun reorderCardList(cardList: ArrayList<Card>) {
        ruleLog("1,start $cardList")
        if (cardList.size == 0) {
            return ruleLoge("reorderCardList end cardList.size == 0")
        }

        val file = File(Configuration.RULE_FILE_DIR, Configuration.RULE_FILE_NAME)
        val ruleList = parseRuleItem(FileIOUtils.readFile2String(file))
        ruleList ?: return ruleLoge("reorderCardList end has no ruleList")
        ruleLog("2,ruleList ${Arrays.toString(ruleList)}")
        //获取当前已有信号的mcc
        val netMcc = ServiceManager.phyCardWatcher.nwMonitor.getLastNetMcc()
        if (netMcc.size == 0) {
            //如果没有plmn值，从系统中读一个之前的值
            val _netMcc = SharedPreferencesUtils.getString("NET_PLMN_BACK_UP")
            if (TextUtils.isEmpty(_netMcc)) {
                return ruleLoge("reorderCardList fail cause:has no _netMcc")
            } else {
                netMcc.add(_netMcc)
            }
        } else {
            //备份MCC一下
            SharedPreferencesUtils.putString("NET_PLMN_BACK_UP", netMcc.elementAt(0))
        }
        ruleLog("3,current mcc $netMcc")
        //备份list一下，排序后如果没有剩下的，就还原
        val backupList = cardList.clone() as ArrayList<Card>

        reorderCardList(cardList, ruleList, netMcc)

        if (cardList.size == 0) {
            ruleLoge("cardList.size == 0")
            cardList.addAll(backupList)
        }
        ruleLog("finish return $cardList")

    }

    private fun parseRuleItem(ruleString: String?): Array<ruleItem?>? {
        ruleString ?: return null
        val list = ruleString.split(RULE_ITEM_SPLIT)
        if (list.isEmpty()) {
            return null
        }
        val ruleArray = Array(list.size, {
            val values = list[it].split(RULE_ITEM_VALUE_SPLIT)
            if (values.size < 3) {
                return@Array null
            }
            try {
                val useMode = values[2].toInt()
                return@Array ruleItem(values[0], values[1], useMode)
            } catch (e: Exception) {
                loge("useMode Exception ${e.cause}(${e.message})")
                return@Array null
            }
        })
        return ruleArray
    }

    /**
     * 先根据国家过滤使用规则，再处理禁用的卡
     */
    private fun reorderCardList(cardList: ArrayList<Card>, ruleList: Array<ruleItem?>, netMcc: HashSet<String>) {
        // 过滤不符合规则的条件
        if (netMcc.size == 0) {
            ruleLoge("netMcc.size == 0")
            return
        }
        // TODO：多国家场景不好处理，目前直接只取第一个
        val currentMcc = netMcc.elementAt(0)
        if (currentMcc.isEmpty()) {
            ruleLoge("currentMcc is empty")
            return
        }
        if (currentMcc.length < 3) {
            ruleLoge("currentMcc.length<3")
            return
        }

        val handledModes = arrayOf(RULE_USE_MODE_PHY_ONLY, RULE_USE_MODE_SOFT_ONLY, RULE_USE_MODE_PHY_FIRST, RULE_USE_MODE_SOFT_FIRST)
        var ruleForAll: ruleItem? = null
        var handleMode = -1
        ruleList.forEach { ruleItem ->
            if (handleMode == -1 && ruleItem != null && ruleItem.mccList.isNotEmpty()) {
                val mccList = ruleItem.mccList
                val isRuleAll = mccList.contains("all", true)
                ruleLog("mccList:$mccList currentMcc:$currentMcc ${mccList.contains(currentMcc)}")
                // 如果当前的Mcc在所过滤规则的mcc列表中，则进行规则过滤
                // 规则包括：1：usermode；2：plmn
                if (mccList.contains(currentMcc) || isSameType(currentMcc, mccList)) {
                    ruleLog("4,use '$currentMcc' rule $ruleItem")
                    when (ruleItem.usermode) {
                        RULE_USE_MODE_FORBID -> {//删除禁用的
                            removeCardByPlmn(ruleItem, cardList)
                        }
                        in handledModes -> { //记录处理方案
                            handleMode = ruleItem.usermode
                        }
                    }
                } else if (isRuleAll) {
                    ruleForAll = ruleItem
                }
            }
        }

        ruleForAll?.let {
            if (handleMode == -1) {
                // 无指定plmn使用all规则
                ruleLog("4,use 'all' rule $it")
                when (it.usermode) {
                    RULE_USE_MODE_FORBID -> {//删除禁用的
                        removeCardByPlmn(it, cardList)
                    }
                    in handledModes -> { //记录处理方案
                        handleMode = it.usermode
                    }
                }
            }
        }

        if (handleMode != -1) {
            reorderCardListByMode(cardList, handleMode)
        } else {
            ruleLoge("no usable rule")
        }
    }

    // TODO：31746 plmn过滤，但是目前来看这里是在用fplmn过滤
    private fun removeCardByPlmn(ruleItem: ruleItem, cardList: ArrayList<Card>) {
        val log = StringBuilder("[removeCardByPlmn] dele:")
        val forbidPlmns = ruleItem.plmns.split(",")
        val waitDeleCard = ArrayList<Card>()
        cardList.forEach { card ->
            forbidPlmns.forEach { plmn ->
                if (card.imsi.startsWith(plmn)) {
                    waitDeleCard.add(card)
                    log.append(card.imsi).append(",")
                }
            }
        }
        cardList.removeAll(waitDeleCard)

        ruleLog(log.toString())
    }

    private fun reorderCardListByMode(cardList: ArrayList<Card>, handleMode: Int) {
        when (handleMode) {
            RULE_USE_MODE_PHY_ONLY -> {
                removeCardByType(CardType.SOFTSIM, cardList)
            }
            RULE_USE_MODE_SOFT_ONLY -> {
                removeCardByType(CardType.PHYSICALSIM, cardList)
            }
            RULE_USE_MODE_PHY_FIRST -> {
                reorderCardListByType(CardType.PHYSICALSIM, cardList)
            }
            RULE_USE_MODE_SOFT_FIRST -> {
                reorderCardListByType(CardType.SOFTSIM, cardList)
            }
        }
    }

    //把指定的card 类型的card 放到前面
    private fun reorderCardListByType(headType: CardType, cardList: ArrayList<Card>) {
        ruleLog("reorderCardListByType put:$headType to head")
        if (cardList.size <= 1) {
            return
        }
        val headList = ArrayList<Card>()
        cardList.forEach {
            if (it.cardType == headType) {
                headList.add(it)
            }
        }
        cardList.removeAll(headList)
        cardList.addAll(0, headList)
    }

    private fun removeCardByType(cardType: CardType, cardList: ArrayList<Card>) {
        ruleLog("removeCardByType :$cardType")
        val deleList = ArrayList<Card>()
        cardList.forEach {
            if (it.cardType == cardType) {
                deleList.add(it)
            }
        }
        cardList.removeAll(deleList)
    }


    //判断是否为相同类型
    private fun isSameType(currentMcc: String, mccList: String): Boolean {
        val ruleMcc = mccList.split(",")
        ruleMcc.forEach {
            if (MccTypeMap[it] == MccTypeMap[currentMcc]) {
                return true
            }
        }
        return false
    }

}

data class CardSave(var logicSlot: Int = 0, var realSlot: Int = 0, var isOn: Boolean = false, var imsi: String? = null ,var roam: Boolean =false)

/**
 * usermode 选卡规则
 */
const val RULE_USE_MODE_UNKNOWN = 0
const val RULE_USE_MODE_PHY_ONLY = 1
const val RULE_USE_MODE_SOFT_ONLY = 2
const val RULE_USE_MODE_PHY_FIRST = 3
const val RULE_USE_MODE_SOFT_FIRST = 4
const val RULE_USE_MODE_UN_HANDLE_1 = 5
const val RULE_USE_MODE_UN_HANDLE_2 = 6
const val RULE_USE_MODE_FORBID = 7

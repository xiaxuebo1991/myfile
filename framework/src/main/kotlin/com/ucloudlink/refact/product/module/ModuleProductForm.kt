package com.ucloudlink.refact.product.module

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.HandlerThread
import android.os.RemoteException
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.android.internal.telephony.ISub
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.framework.util.APN_TYPE_DEFAULT
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.StateMessageId
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.access.restore.ServiceRestore
import com.ucloudlink.refact.access.struct.LoginInfo
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.INetRestrictOperator
import com.ucloudlink.refact.business.login.Session
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.softsim.CardRepository
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.plmnselect.PlmnFee
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.model.m2.ATMsgDecode
import com.ucloudlink.refact.model.m2.M2MqttClient
import com.ucloudlink.refact.platform.sprd.flow.netlimit.SprdNetRestrictOperator
import com.ucloudlink.refact.product.ProductFormBase
import com.ucloudlink.refact.product.mifi.ExtSoftsimDB
import com.ucloudlink.refact.product.mifi.PhyCardApn.PhyCardApnSetting
import com.ucloudlink.refact.product.mifi.cardselect.*
import com.ucloudlink.refact.product.mifi.connect.MifiMsgClient
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimDataBackup
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum
import com.ucloudlink.refact.utils.*
import okio.Okio
import rx.Subscription
import java.io.*
import java.security.MessageDigest
import java.util.*

/**
 * Created by shiqianhua on 2018/3/23.
 */

/**
 * todo:模块暂时使用Mifi的流程，后续根据业务分开
 */
class ModuleProductForm(val context :Context): ProductFormBase(context) {
    lateinit var session: Session
    lateinit var extSoftsimDB: ExtSoftsimDB
    var uploadSoftsimSub: Subscription? = null
    var extSoftsimReqSub: Subscription? = null
    var extSoftsimBinReqSub: Subscription? = null
    var extSoftsimStateSub: Subscription? = null
    var downLoadRuleSub: Subscription? = null
    lateinit var softsimList: List<SoftsimInfo>//下载的软卡列表
     var m2mqttclient: M2MqttClient
    lateinit var mifiMsgClient: MifiMsgClient
    lateinit var phyCardApnSetting: PhyCardApnSetting
    lateinit var info: LoginInfo
    lateinit var phyCardSelect: CardSelect
    val file = File("/productinfo/info.obj")
    val file2 = File("/productinfo/info2.obj")
    var file_temp = file
    var inforead: LoginInfo = LoginInfo("", "")
    var loopThread = HandlerThread("ModuleProductForm")
    val WIFIAP_STATUS ="wifiapstatus"
    val netRestrictOperator by lazy { SprdNetRestrictOperator() }
    init {
        loopThread.start()
        m2mqttclient = M2MqttClient(context)
    }
    override fun dataRecover() {
        super.dataRecover()
        SoftSimDataBackup.recover()
    }

    override fun init() {
        super.init()
        when(Configuration.LOGIN_TYPE){
            0->file_temp = file
            1->file_temp = file2
        }
        if (!file_temp.exists()) {
            file_temp.createNewFile()
            try {//写入一个默认对象
                val out = ObjectOutputStream(FileOutputStream(file_temp))
                out.writeObject(LoginInfo("", ""))
                out.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        setWifiApStatus()
        session = Session()
        extSoftsimDB = ExtSoftsimDB(context)

        mifiMsgClient = MifiMsgClient(context)
        phyCardSelect = CardSelect(context, mifiMsgClient.mqttClient, loopThread.looper)
        phyCardApnSetting = PhyCardApnSetting(context)
        JLog.logd("isExceptionStart:" + ServiceRestore.isExceptionStart(context))
        loginReq()
    }

    /**
     **设置关机之前的热点状态
     * */
    fun setWifiApStatus(){
            var ap = SharedPreferencesUtils.getInt(context,WIFIAP_STATUS)
            JLog.logd("ap == $ap")
            if (ap == 0) {
                WifiApUtil.setWifiApStatus(null,false)
            }else if(ap == 1){
                WifiApUtil.setWifiApStatus(null,true)
            }
    }

    override fun processOrderInfo(orderId: String, serviceEnable: Boolean, haveEnableBefore: Boolean, cardList: ArrayList<Card>): Int {
        if (serviceEnable && !haveEnableBefore && !isPhyCardOn()) {
            JLog.logd("phy card is not on!")
        } else {
            cardList.add(Card(slot = Configuration.seedSimSlot, cardType = CardType.PHYSICALSIM, roamenable = true))
        }
        //查询一下软卡数据库，将软卡加入cardlist
        val list: List<SoftsimInfo> = extSoftsimDB.allExtSoftsim
        if (!list.isEmpty() && list.isNotEmpty()) {
            for (siminfo in list) {
                val card = Card()
                card.ki = siminfo.ki
                card.opc = siminfo.opc
                card.cardType = CardType.SOFTSIM
                card.iccId = siminfo.iccid
                card.imsi = siminfo.imsi.toString()
                card.msisdn = siminfo.msisdn
                card.numeric = siminfo.msisdn
                cardList.add(Card(slot = Configuration.seedSimSlot, cardType = CardType.SOFTSIM, ki = siminfo.ki, opc = siminfo.opc,
                        imsi = siminfo.imsi.toString(), rat = siminfo.rat, roamenable = siminfo.roamEanble,
                        apn = decodeApns(siminfo.apn, siminfo.imsi.toString().subSequence(0, 5).toString())))
            }
        }
        reorderCardList(cardList)
        return if (cardList.isEmpty()) ErrorCode.LOCAL_NO_SEED_CARD else 0
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
        ruleLog("2,ruleList $ruleList")
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
    /**
     * 先根据国家过滤使用规则，再处理禁用的卡
     */
    fun reorderCardList(cardList: ArrayList<Card>, ruleList: Array<ruleItem?>, netMcc: HashSet<String>) {
        if (netMcc.size == 0) {
            ruleLoge("netMcc.size==0")
            return
        }
        //多国家场景不好处理，目前直接只取第一个
        val currentMcc = netMcc.elementAt(0)
        if (TextUtils.isEmpty(currentMcc)) {
            ruleLoge("currentMcc is empty")
            return
        }

        if (currentMcc.length < 3) {
            ruleLoge("currentMcc.length<3")
            return
        }

        val _handledMode = arrayOf(RULE_USE_MODE_PHY_ONLY, RULE_USE_MODE_SOFT_ONLY, RULE_USE_MODE_PHY_FIRST, RULE_USE_MODE_SOFT_FIRST)
        var ruleForAll: ruleItem? = null
        var handleMode = -1
        ruleList.forEach { ruleItem ->
            if (handleMode == -1 && ruleItem != null && !TextUtils.isEmpty(ruleItem.mccList)) {
                val mccList = ruleItem.mccList
                val isRule_all = mccList.contains("all", true)
                JLog.logd("mccList:$mccList currentMcc:$currentMcc ${mccList.contains(currentMcc)}")
                if (mccList.contains(currentMcc) || isSameType(currentMcc, mccList)) {
                    ruleLog("4,use '$currentMcc' rule $ruleItem")
                    when (ruleItem.usermode) {
                        RULE_USE_MODE_FORBID -> {//删除禁用的
                            removeCardByPlmn(ruleItem, cardList)
                        }
                        in _handledMode -> { //记录处理方案
                            handleMode = ruleItem.usermode
                        }
                    }
                } else if (isRule_all) {
                    ruleForAll = ruleItem
                }
            }
        }

        val _ruleForAll = ruleForAll
        if (handleMode == -1 && _ruleForAll != null) {
            //无指定plmn使用all规则
            ruleLog("4,use 'all' rule $_ruleForAll")
            when (_ruleForAll.usermode) {
                RULE_USE_MODE_FORBID -> {//删除禁用的
                    removeCardByPlmn(_ruleForAll, cardList)
                }
                in _handledMode -> { //记录处理方案
                    handleMode = _ruleForAll.usermode
                }
            }
        }

        if (handleMode != -1) {
            reorderCardListByMode(cardList, handleMode)
        } else {
            ruleLoge("no usable rule")
        }
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

    private fun parseRuleItem(ruleString: String?): Array<ruleItem?>? {
        ruleString ?: return null
        val list = ruleString.split(Configuration.RULE_ITEM_SPLIT)
        if (list.isEmpty()) {
            return null
        }
        val ruleArray = Array(list.size, {
            val values = list[it].split(Configuration.RULE_ITEM_VALUE_SPLIT)
            if (values.size < 3) {
                return@Array null
            }
            try {
                val useMode = values[2].toInt()
                return@Array ruleItem(values[0], values[1], useMode)
            } catch (e: Exception) {
                JLog.loge("useMode Exception ${e.cause}(${e.message})")
                return@Array null
            }
        })
        return ruleArray
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

    fun ruleLoge(log: String) {
        JLog.loge("[reorderByRule]", log)
    }


    fun ruleLog(log: String) {
        JLog.logd("[reorderByRule]", log)
    }

    override fun getLoginType(): Int {
        return Configuration.LOGIN_TYPE
    }

    override fun getProductType(): ProductTypeEnum {
        return ProductTypeEnum.MODULE
    }

    override fun setLoginInfo(loginInfo: LoginInfo, loginResp: LoginResp): LoginInfo {
        if (loginResp.errorCode == ErrorCode.RPC_RET_OK) {
            JLog.logk("username = " + loginResp.usercode)
            JLog.logk("password = " + loginResp.password)
            //mifi,第一次保存服务器返回的用户名密码
            if (loginResp.usercode != null && loginResp.usercode != "" && loginResp.password != null && loginResp.password != "") {
                RunningStates.saveUserName(loginResp.usercode)
                loginInfo.username = loginResp.usercode
                Configuration.username = loginResp.usercode
                RunningStates.savePassWord(loginResp.password)
                Configuration.username = loginResp.usercode
                loginInfo.passwd = loginResp.password
                JLog.logk("save user info")
                if (!file.exists()) {
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                }
                try {
                    val out = ObjectOutputStream(FileOutputStream(file))
                    out.writeObject(loginInfo)
                    out.close()
                    JLog.logk("save success")
                } catch (e: IOException) {
                    e.printStackTrace()
                    JLog.logk("save fail :$e")
                }
            }
        }
        return loginInfo
    }

    override fun startDownLoadSoftsim() {
        startUploadSoftsimList()
        startDownloadSoftsim(RunningStates.getUserName(), java.lang.Long.valueOf(OperatorNetworkInfo.imei), 0)
    }

    override fun ifNeedCheckBeforeLogin(): Boolean {
        return false
    }

    override fun pinVerify() {
    }

    override fun serviceStart() {

    }

    override fun serviceExit() {

    }

    override fun restoreCheck(): Int {
        return 0
    }

    override fun needRecovery(): Boolean {
        return false
    }

    override fun setGpsConfig(hardEnable: Boolean, netGps: Boolean): Int {
//        val lock = Object()
//        var ret = 0
//        MsgEncode.setNetworkGpsSwitch(m2mqttclient, hardEnable, netGps, 3).subscribe(
//                {
//                    JLog.logd("recv msg! $it")
//                    synchronized(lock) {
//                        lock.notifyAll()
//                    }
//                },
//                {
//                    JLog.logd("set failed!")
//                    ret = 0
//                }
//        )
//        synchronized(lock) {
//            lock.wait(3 * 1000)
//        }
//        JLog.logd("setGpsConfig $hardEnable $netGps $ret ")
        return 0
    }

    override fun reloginAfterMccChange(mcc: String): Boolean {//没有选卡功能，MccChange的时候，直接返回true，重新登录
        return true
    }

// 暂时屏蔽软卡功能
//  override fun getSeedUpdateTask(): IBusinessTask? {
//        return SeedUpdateTask()
//    }

    override fun isSeedAlwaysOn(): Boolean {
        return true
    }

    override fun getSeedSimFplmnRefByImsi(imsi: String, cardType: CardType): Array<String>? {
        val extSoftsimDB = ExtSoftsimDB(ServiceManager.appContext)
        val softsimInfo = extSoftsimDB.getSimInfoByImsi(imsi)
        val binRef = softsimInfo?.fplmnRef
        if (TextUtils.isEmpty(binRef)) {
            JLog.loge("[getSeedSimFplmnRefByImsi] binRef is empty")
            return null
        }

        val file = File(Configuration.RULE_FILE_DIR, binRef)

        if (!file.exists()) {
            JLog.logd("[getSeedSimFplmnRefByImsi] file is not exists ")
            return null
        }

        val list = FileIOUtils.readFile2List(file)
        if (list != null && list.size > 0) {
            return Array(list.size, { list[it].replace(";", "") })
        }

        return null
    }

    override fun getSeedSimPlmnFeeListByImsi(imsi: String, cardType: CardType): Array<PlmnFee>? {
        val extSoftsimDB = ExtSoftsimDB(ServiceManager.appContext)
        val softsimInfo = extSoftsimDB.getSimInfoByImsi(imsi)
        val binRef = softsimInfo?.feeBinRef
        if (TextUtils.isEmpty(binRef)) {
            JLog.loge("[getSeedSimPlmnFeeListByImsi] binRef is empty")
            return null
        }

        val file = File(Configuration.RULE_FILE_DIR, binRef)
        if (!file.exists()) {
            JLog.logd("[getSeedSimPlmnFeeListByImsi] file is not exists ")
            return null
        }
        val reader = BufferedReader(InputStreamReader(FileInputStream(file)))

        val list = ArrayList<PlmnFee>()

        var line: String? = reader.readLine()
        while (line != null) {

            try {
                if (line.isNotEmpty()) {
                    val strList = line.replace(";", "").split(",")
                    if (strList.size == 3) {
                        list.add(PlmnFee(strList[0], Integer.parseInt(strList[1]), strList[2].toFloat()))
                    } else {
                        JLog.loge("[getSeedSimPlmnFeeListByImsi] wrong format $line ")
                    }
                }

            }catch (e:Exception){
                JLog.loge("catch Exception ${e.message}")
            }
            line = reader.readLine()
        }


        return Array(list.size, { list[it] })
    }

    override fun getErrorCodeList(): ArrayList<ErrorCode.ErrCodeInfo> {
        val errcodeList = arrayListOf(
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_VSIM_AVAILABLE, true, ErrorCode.ErrActType.ACT_RETRY, 12, "no available sim"), // 没有分到合适的卡
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_GET_USERINFO_FAIL, true, ErrorCode.ErrActType.ACT_RETRY, 50, "get msg from bss failed"), // 从BSS获取用户信息失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_EMPTY_PLMN_FROM_TER, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "terminal send plmn is null"), // 终端上报的网络，对应的网络集为空
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_EMPTY_MCC_FROM_TER, true, ErrorCode.ErrActType.ACT_NONE, 27, "terminal send mcc is null"), // 终端上报的网络，对应国家为空
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_PRODUCT_BY_ID, true, ErrorCode.ErrActType.ACT_NONE, 28, "can not find product by product ID"), // 根据产品ID获取不到产品

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_POLICY_BY_ID, true, ErrorCode.ErrActType.ACT_NONE, 29, "can not find policy by policy ID"), // 根据策略Id找不到策略
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_CARD_POOL_BY_POLICY, true, ErrorCode.ErrActType.ACT_NONE, 30, "can not find card pool by policy ID"), // 根据策略ID找不到卡池
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_CARD_BY_IMSI, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "can not find card by imsi"), // 根据imsi找不到卡对象
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_ONLINE_USER_BY_USERCODE, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "can not find online user by usercode"), // 根据userCode 找不到用户卡在线对象
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_IMSI_NOT_EXIST, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "card not exist when user online"), // 卡在线对象,卡号不存在

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_GROUP_BY_IMSI, true, ErrorCode.ErrActType.ACT_NONE, 31, "can not find group by imsi"), // 根据Imsi找不到 Group对象
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CARD_RELEASE_FAIL, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "release card fail"), // 释放卡失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_LAST_IMSI_ERROR, true, ErrorCode.ErrActType.ACT_NONE, 0, "last used imsi error"), // 上次使用的Imsi号错误
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_RAT_ERROR, true, ErrorCode.ErrActType.ACT_NONE, 51, "rat error"), // 网络制式错误
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_UPDATE_USER_ONLINE_ERR, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "can not find policy by policy ID"), // 更新用户在线状态错误

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ADD_DISPATCH_CARD_LOG_ERR, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "add dispatch card log error"), // 添加分卡日志错误
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_NETWORK_AVAILABLE, true, ErrorCode.ErrActType.ACT_NONE, 65, "no available network"), // 当前设备所在位置网络信号太差， 没有可用网络
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_VSIM_BIN_GET_FAIL, true, ErrorCode.ErrActType.ACT_NONE, ErrorCode.RPC_VSIM_BIN_GET_FAIL, "vsim bin get fail"), // 当前设备所在位置网络信号太差， 没有可用网络

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_AVAILABLE_SOFTSIM, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_NO_AVAILABLE_SOFTSIM, "no available softsim"), // 无合适软卡
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_SOFTSIM_PARAM_ERROR, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_SOFTSIM_PARAM_ERROR, "softsim param error"), // 参数错误
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ORDER_NOT_EXIST, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_ORDER_NOT_EXIST, "order not exist"), // 订购关系不存在
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_BSS_SERVER_FAIL, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_CALL_BSS_SERVER_FAIL, "call bss fail"), // 调用BSS服务失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ORDER_COUNTRY_IS_NULL, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_ORDER_COUNTRY_IS_NULL, "order country is null"), // 订单国家信息为空

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ORDER_GOODS_NOT_EXIST, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_ORDER_GOODS_NOT_EXIST, "order goods not exist"), // 订单销售品不存在
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ORDER_GOODS_IS_NULL, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_ORDER_GOODS_IS_NULL, "order goods is null"), // 订单销售品为空
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ODER_NO_NEED_SOFTSIM, true, ErrorCode.ErrActType.ACT_TER, ErrorCode.RPC_ODER_NO_NEED_SOFTSIM, "order no need softsim"), // 订单销售品不需要下发软卡

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_USER_NOT_EXIST, true, ErrorCode.ErrActType.ACT_EXIT, 0, "username is not exist"), // 用户不存在
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_PASSWD_CHECK_FAIL, true, ErrorCode.ErrActType.ACT_EXIT, 0, "password check failed"), // 密码验证失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_IMEI_OR_USERNAME_NULL, true, ErrorCode.ErrActType.ACT_EXIT, 0, "imei or username is null"), // IMEI不能为空/用户名为空

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_IMEI_BIND_NOT_EXIST, true, ErrorCode.ErrActType.ACT_EXIT, 6, "imei binding is not exist"), // 不存在IMEI的绑定关系
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_USER_OR_PASSWD_NULL_OR_NOT_IN_BIND, true, ErrorCode.ErrActType.ACT_EXIT, 24, "username or password is null, and no binding"), // 用户帐号或密码为空，且绑定状态不为绑定中
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NO_ACTIVATE_AFTER_FREE_USE, true, ErrorCode.ErrActType.ACT_EXIT, 0, "free timeout, but user not activate"), // 免费试用过期，但用户仍未激活
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_BOTH_HAVE_DAILY_MONTHLY_PACKAGE, true, ErrorCode.ErrActType.ACT_EXIT, 25, "user have both daily and monthly package"), // 用户同时存在包天包月
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_MONTHLY_USERS_FULL, true, ErrorCode.ErrActType.ACT_EXIT, 26, "monthly package over max count"), // 包月超过最大用户数

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_FEE_NOT_ENOUGH_FOR_DAILY_PACKAGE, true, ErrorCode.ErrActType.ACT_EXIT, 66, "user fee not enough for daily package"), // 帐户余额不足包天费
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_NOT_IN_FAVOR_COUNTRY, true, ErrorCode.ErrActType.ACT_EXIT, 18, "out of the favor country"), // 不在优惠国家范围内
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_GET_USER_INFO_FAIL, true, ErrorCode.ErrActType.ACT_EXIT, 52, "get user fail"), // 获取用户失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_IMEI_NOT_EXIST, true, ErrorCode.ErrActType.ACT_EXIT, 5, "imei not exist"), // IMEI不存在
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_IMEI_ALREADY_DELETED, true, ErrorCode.ErrActType.ACT_EXIT, 5, "imei is deleted"), // IMEI已被删除

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_GET_ACCESS_TOKEN_ERR, true, ErrorCode.ErrActType.ACT_RETRY, 53, "get access token error"), // 获取AccessToken异常
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_USER_ACCOUNT_ERR, true, ErrorCode.ErrActType.ACT_RETRY, 54, "account data error"), // 帐户数据异常
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_BSS_UNKNOWN_ERR, true, ErrorCode.ErrActType.ACT_NONE, 55, "bss unknown error"), // BSS未知异常
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ACCOUNT_IS_DISALBE, true, ErrorCode.ErrActType.ACT_EXIT, 14, "account is disable"), // 账号被停用
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ACCOUNT_IS_DISABLE, false, ErrorCode.ErrActType.ACT_EXIT, 14, "account is disable"), // s2c账号被停用
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_BSS_FAIL, true, ErrorCode.ErrActType.ACT_RETRY, 56, "call bss fail"), // BSS调用失败

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_CSS_FAIL, true, ErrorCode.ErrActType.ACT_RETRY, 57, "call css fail"), // CSS调用失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_OSS_FAIL, true, ErrorCode.ErrActType.ACT_RETRY, 58, "call oss fail"), // OSS调用失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_BAM_FAIL, true, ErrorCode.ErrActType.ACT_RETRY, 59, "call bam fail"), // BAM调用失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_INVALID_SESSION, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "invalid sessionid"), // 无效的sessionid
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_APDU_DEAL_ERR, true, ErrorCode.ErrActType.ACT_NONE, 60, "process apdu error"), // APDU处理异常

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_DUBBO_FAIL, true, ErrorCode.ErrActType.ACT_NONE, 61, "call dubbo error"), // 调用dubbo服务异常
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_SYSTEM_SERVICE_FAIL, true, ErrorCode.ErrActType.ACT_NONE, 62, "call system service error"), // 调用系统服务异常
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_TER_SID_EMPTY, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "termianl sid is empty"), // 终端登录请求sid is empty!
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_TER_IMEI_EMPTY, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "terminal imei is empty"), // 终端登录请求imei is empty!
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_LOGIN_FAIL_RET_NULL, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "login fail, return null"), // 终端登录失败,登录返回结果为null

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_GET_SERVICE_LIST_FAIL, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "query service list fail"), // 查询服务列表失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_DISPATCH_CARD_FAIL, true, ErrorCode.ErrActType.ACT_RETRY, 63, "user dispatch card fail"), // 用户分卡失败
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_CALL_LOGIN_AUTH, true, ErrorCode.ErrActType.ACT_RETRY, 64, "call auth server error"), // 调用auth服务登录接口出错
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_FEE_NOT_ENOUGH, true, ErrorCode.ErrActType.ACT_EXIT, 7, "fee not enough, refuse login"), // 用户余额不足,拒绝登录
                ErrorCode.ErrCodeInfo(ErrorCode.RPC_PLMN_LIST_EMPTY, true, ErrorCode.ErrActType.ACT_RELOGIN, 0, "switch param plmnlist is null"), // 换卡接口plmnList参数为空值

                ErrorCode.ErrCodeInfo(ErrorCode.RPC_ASS_UNKNOWN_ERR, true, ErrorCode.ErrActType.ACT_NONE, 0, "ass unknown error"), // ASS未知错误

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_PHY_CARD_NOT_EXIST, false, ErrorCode.ErrActType.ACT_EXIT, 1001, "phy card is not exist"), // 物理卡不存在
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SOFT_CARD_NOT_EXIST, false, ErrorCode.ErrActType.ACT_RETRY, 1002, "softsim is not exist"), //软卡不存在
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_PHONE_DATA_DISABLED, false, ErrorCode.ErrActType.ACT_EXIT, 1003, "phone data disable"), //数据连接没有打开
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_PHY_NETWORK_UNAVAILABLE, false, ErrorCode.ErrActType.ACT_RETRY, 1004, "phy card network unavailable"), //物理卡网络不可用
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_PHONE_CALLING, false, ErrorCode.ErrActType.ACT_NONE, 1005, "phone is in calling"), //正在打电话

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_PHY_CARD_DISABLE, false, ErrorCode.ErrActType.ACT_EXIT, 1006, "phy card disable"), // 物理卡被禁用
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_AIR_MODE_ENABLED, false, ErrorCode.ErrActType.ACT_EXIT, 1007, "air mode enabled"), // 飞行模式
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_APP_IN_BLACKLIST, false, ErrorCode.ErrActType.ACT_EXIT, 1008, "app in blacklist"), //app在黑名单中
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_APP_RECOVERY_TIMEOUT, false, ErrorCode.ErrActType.ACT_RETRY, 1009, "app recovery timeout"), // app恢复超时
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_LOGIN_OTHER_PLACE, false, ErrorCode.ErrActType.ACT_EXIT, 1010, "user login at other place"), //用户在另外一个地方登陆

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_BIND_CHANGE, false, ErrorCode.ErrActType.ACT_EXIT, 1011, "terminal bind change"), //终端换绑
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_FLOW_CTRL_EXIT, false, ErrorCode.ErrActType.ACT_EXIT, 1012, "flow ctrl fail"), // 限速失败
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_FORCE_LOGOUT_MANUAL, false, ErrorCode.ErrActType.ACT_EXIT, 1013, "logout manual by server"), // 服务器手动退出
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_FORCE_LOGOUT_FEE_NOT_ENOUGH, false, ErrorCode.ErrActType.ACT_EXIT, 1014, "fee not enough, force logout!"), // 余额不足，不能上网
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ROAM_NOT_ENABLED, false, ErrorCode.ErrActType.ACT_EXIT, 1015, "roam not enabled!"), //漫游开关未打开，不能上网

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ORDER_IS_NULL, false, ErrorCode.ErrActType.ACT_EXIT, 1016, "order is null!"), // 订购关系为空
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ORDER_INFO_IS_NULL, false, ErrorCode.ErrActType.ACT_EXIT, 1017, "order info is null!"), // 订购关系信息为空
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ORDER_SOFTSIM_NULL, false, ErrorCode.ErrActType.ACT_EXIT, 1018, "softsim is null!"), // 软卡为空为空
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_TIMEOUT, false, ErrorCode.ErrActType.ACT_TIMEOUT, ErrorCode.LOCAL_TIMEOUT, "local timeout!"), // 超时
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ORDER_INACTIVATE, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_ORDER_INACTIVATE, "user order inactivate!"), // 用户套餐未激活

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_ORDER_OUT_OF_DATE, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_ORDER_OUT_OF_DATE, "user order out of date!"), // 用户套餐过期
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USERNAME_INVALID, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_USERNAME_INVALID, "user name invalid!"), // 用户名非法
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SERVICE_RUNNING, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_SERVICE_RUNNING, "service is running!"), // 服务已经启动
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SECURITY_FAIL, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_SECURITY_FAIL, "sercurity check fail!"), // 安全校验错误
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SECURITY_TIMEOUT, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.LOCAL_SECURITY_TIMEOUT, "sercurity check timeout!"), // 安全校验超时

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_BIND_NETWORK_FAIL, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_BIND_NETWORK_FAIL, "bind dun network fail!"), // dun绑定失败
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_FORCE_LOGOUT_UNKNOWN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_FORCE_LOGOUT_UNKNOWN, "force logout, but do not known reason"),
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_AIR_MODE_ENABLE, false, ErrorCode.ErrActType.ACT_NONE, 1051, "air mode enabled"), //飞行模式打开
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_AIR_MODE_DISABLE, false, ErrorCode.ErrActType.ACT_NONE, 1052, "air mode disable"), // 飞行模式关闭

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_PHONE_DATA_DISABLE, false, ErrorCode.ErrActType.ACT_NONE, 1053, "phone data disabled"), // 数据连接关闭
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_PHONE_DATA_ENABLE, false, ErrorCode.ErrActType.ACT_NONE, 1054, "phone data enabled"), // 数据连接打开

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_CHANGE_DDS, false, ErrorCode.ErrActType.ACT_NONE, 1055, "user change dds"), // 用户切换dds
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_DDS_CHANGE_BACK, false, ErrorCode.ErrActType.ACT_NONE, 1056, "user change dds back"), // dds切换回来

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_PHONE_CALL_START, false, ErrorCode.ErrActType.ACT_NONE, 1057, "phone call start"), // 电话接入
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_PHONE_CALL_STOP, false, ErrorCode.ErrActType.ACT_NONE, 1058, "phone call stop"), // 电话关闭

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_SEED_SIM_DISABLE, false, ErrorCode.ErrActType.ACT_NONE, 1059, "seed sim disabled"), // 种子卡禁用
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_SEED_SIM_ENABLE, false, ErrorCode.ErrActType.ACT_NONE, 1060, "seed sim enabled"), // 种子卡使用

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_WIFI_CONNECTED, false, ErrorCode.ErrActType.ACT_NONE, 1061, "wifi connected"), // wifi连接网络
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_WIFI_DISCONNECTED, false, ErrorCode.ErrActType.ACT_NONE, 1062, "wifi disconnected"), // wifi关闭网络

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_APP_TO_BLACKLIST, false, ErrorCode.ErrActType.ACT_NONE, 1063, "set app to blacklist"), // app加入黑名单
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_APP_OUT_BLACKLIST, false, ErrorCode.ErrActType.ACT_NONE, 1064, "set app out of blacklist"), // app移除黑名单

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_CLOUD_SIM_DISABLE, false, ErrorCode.ErrActType.ACT_NONE, 1065, "cloudsim disable"), // 云卡禁用
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_CLOUD_SIM_ENABLE, false, ErrorCode.ErrActType.ACT_NONE, 1066, "cloudsim enable"), // 云卡使用

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SERVER_UNKNOWN_ERR, true, ErrorCode.ErrActType.ACT_RETRY, 1067, "server unknown error"), // 服务器未知错误
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SERVER_PACKAGE_PARSE_ERR, true, ErrorCode.ErrActType.ACT_RETRY, 1068, "server package parse error"), // 服务器包解析错误
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_LOGIN_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, 1069, "login timeout"),  // 登陆超时
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_UNKNOWN_ERROR, false, ErrorCode.ErrActType.ACT_NONE, 1070, "unknown error"),  // 未知错误

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_INVALID_VSIM_APN, false, ErrorCode.ErrActType.ACT_NONE, 1071, "invalid vsim apn"),  // 虚拟卡非法apn
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_INVALID_VSIM_IMSI, false, ErrorCode.ErrActType.ACT_NONE, 1072, "invalid vsim imsi"),  // 虚拟卡非法imsi
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_INVALID_VSIM_VIRT_IMEI, false, ErrorCode.ErrActType.ACT_NONE, 1073, "invalid vsim virtual imei"),  // 虚拟卡非法虚拟imei
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_INVALID_SOFT_SIM_IMSI, false, ErrorCode.ErrActType.ACT_NONE, 1074, "invalid softsim imsi"),  // 软卡非法imsi
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_INVALID_SOFT_SIM_VIRT_IMEI, false, ErrorCode.ErrActType.ACT_NONE, 1075, "invalid softsim virtual imei"),  // 软卡非法虚拟imei

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_INVALID_SOFT_SIM_APN, false, ErrorCode.ErrActType.ACT_NONE, 1076, "invalid softsim apn"),  // 软卡非法apn
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_GET_ROUTE_TABLE_FAIL, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.LOCAL_GET_ROUTE_TABLE_FAIL, "get route table failed"),  // 获取路由表失败
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_AIR_MODE_OVER_10MIN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_AIR_MODE_OVER_10MIN, "air mode over 10min"),  // 飞行模式超过10min
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SEED_CARD_DISABLE_OVER_10MIN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_SEED_CARD_DISABLE_OVER_10MIN, "seedcard disable over 10min"),  // 种子卡关闭超过10min
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_CLOUD_CARD_DISABLE_OVER_10MIN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_CLOUD_CARD_DISABLE_OVER_10MIN, "cloudcard disable over 10min"),  // 云卡关闭超过10min

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_PHONE_DATA_DISABLE_OVER_10MIN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_PHONE_DATA_DISABLE_OVER_10MIN, "phone data disable over 10min"),  // 关闭数据连接超过10min
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_APP_IN_BLACKLIST_OVER_10MIN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_APP_IN_BLACKLIST_OVER_10MIN, "app in blacklist over 10min"),  // app在黑名单超过1omin
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_DDS_EXCEPTION_OVER_10MIN, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_DDS_EXCEPTION_OVER_10MIN, "dds invalid over 10min"),  // dds异常超过10min
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_DDS_IN_EXCEPTION, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_DDS_IN_EXCEPTION, "dds invalid change"),  // dds异常切换
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_DDS_SET_TO_NORMAL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_DDS_IN_EXCEPTION, "dds change to normal"),  // dds切换到正常

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SEND_FAILED_SINCE_NOT_READY, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_SEND_FAILED_SINCE_NOT_READY, "send failed since secure link not ready"),  // dds切换到正常
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_NO_SEED_CARD, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_NO_SEED_CARD, "have no seed card"),
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_CONFIG_ERROR, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_CONFIG_ERROR, "local config error"), // 内部配置错误
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_PHY_SUBID_INVALID, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_USER_PHY_SUBID_INVALID, "user phy card subid invalid"), // 物理卡subid异常
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_USER_PHY_APN_INVALID, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_USER_PHY_APN_INVALID, "user phy card apn invalid"), // 物理卡apn异常

                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_SYSTEM_ERROR, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_SYSTEM_ERROR, "system error"), // 系統錯誤
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_MCC_CHANGE, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.LOCAL_MCC_CHANGE, "mcc change, maybe logout!"), // mifi场景下，可能会退出

                /* softsim errcode start */
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_PARAM_ERR, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_PARAM_ERR, "download param error"),  // 下载参数错误
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_LOGIN_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_LOGIN_TIMEOUT, "login timeout"),  // 登陆超时
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_DISPATCH_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_DISPATCH_TIMEOUT, "dispatch softsim timeout"),  // 分配软卡超时
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_GET_SOFTSIM_INFO_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_GET_SOFTSIM_INFO_TIMEOUT, "get softsim info timeout"),  // 获取软卡信息超时
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_GET_BIN_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_GET_BIN_TIMEOUT, "get bin file timeout"),  // 获取bin文件超时

                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_USER_CANCEL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_USER_CANCEL, "user cancel"),  // 用户取消
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_SOCKET_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_SOCKET_TIMEOUT, "socket connect timeout"),  // socket超时
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_CHANGE_NEW_USER, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_CHANGE_NEW_USER, "change to new user"),  // 切换新用户
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_NO_ORDER, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_NO_ORDER, "no order in rsp"),  // 无法找到订购关系
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_NO_SOFTSIM, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_NO_SOFTSIM, "no softsim in order"),  // 订购关系中没有软卡

                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_BIN_FILE_NULL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_BIN_FILE_NULL, "no bin in softsim info"),  // 软卡信息中没有携带bin文件
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_NETWORK_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_NETWORK_TIMEOUT, "nework timeout"),  // 检测网络超时
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_SEED_NETWORK_FAIL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_SEED_NETWORK_FAIL, "seed network enable fail"),  // 启动种子卡网络失败
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_UP_SESSION_INVALID, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_UP_SESSION_INVALID, "softsim update sessionid invalid"),  // session异常
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_UP_USER_CANCEL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_UP_USER_CANCEL, "softsim update cancel"),  // 手动取消软卡更新

                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_UP_DL_START, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_UP_DL_START, "softsim download start"),  // 开始更新软卡
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DOWNLOAD_ADDCARD_FAIL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DOWNLOAD_ADDCARD_FAIL, "softsim download success but addCard fail"),  // 下载或更新软卡，添加到数据库失败
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_DL_RSP_INVALID, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_DL_RSP_INVALID, "softsim download/update response invalid"),  // 下载或更新软卡时，服务器响应错误
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_INVALID_KI, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_INVALID_KI, "softsim ki invalid"),  // 软卡ki非法
                ErrorCode.ErrCodeInfo(ErrorCode.SOFTSIM_INVALID_OPC, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.SOFTSIM_INVALID_OPC, "softsim opc invalid"),  //软卡opc非法

                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEP_CARD_PARAMETER_WRONG, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_EXCEP_CARD_PARAMETER_WRONG, "card except card param error"), // 卡参数错误
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEP_PHY_CARD_IS_NULL, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.CARD_EXCEP_PHY_CARD_IS_NULL, "card except phy card is null"), // 物理卡不存在
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEP_PHY_CARD_DEFAULT_LOST, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.CARD_EXCEP_PHY_CARD_DEFAULT_LOST, "card except card default lost"), // ？？
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_INSERT_SOFT_SIM_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_INSERT_SOFT_SIM_TIMEOUT, "insert softsim timeout"), // 插入软卡超时
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_ADD_SOFT_SIM_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_ADD_SOFT_SIM_TIMEOUT, "add softsim timeout"), // 加入软卡超时

                ErrorCode.ErrCodeInfo(ErrorCode.CARD_READY_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_READY_TIMEOUT, "card ready timeout"), // 卡ready超时
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_INSERVICE_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_INSERVICE_TIMEOUT, "card inservice timeout"), // 卡注册超时
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_CONNECT_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_CONNECT_TIMEOUT, "card connect timeout"), // 卡拨号超时
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_DATA_ENABLE_CLOSED, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_DATA_ENABLE_CLOSED, "card data enable closed"), // 数据连接被关闭
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_ROAM_DATA_ENABLE_CLOSED, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_ROAM_DATA_ENABLE_CLOSED, "roam data enable closed"), // 漫游开关被关闭

                ErrorCode.ErrCodeInfo(ErrorCode.CARD_CLOSE_CARD_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_CLOSE_CARD_TIMEOUT, "close card timeout"), // 关卡超时
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_SIM_CRASH, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_SIM_CRASH, "card sim crash"), // 卡crash
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEPT_NO_AVAILABLE_SOFTSIM, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_EXCEPT_NO_AVAILABLE_SOFTSIM, "card except no available softsim"), // 没有可用的软卡(被拒)
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEPTION_FAIL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_EXCEPTION_FAIL, "enable card fail"), // 启动卡失败
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEPTION_ENABLE_TIMEOUT, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.CARD_EXCEPTION_ENABLE_TIMEOUT, "enable card timeout"), // 启动卡超时

                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEPT_SOFTSIM_UNUSABLE, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_EXCEPT_SOFTSIM_UNUSABLE, "softsim unusable at this time"), // 软卡当前不可用
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEPT_REG_DENIED, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_EXCEPT_REG_DENIED, "card reg denied"), // 卡注册被拒
                ErrorCode.ErrCodeInfo(ErrorCode.EXCEPTION_REG_DENIED_NOT_DISABLE, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.EXCEPTION_REG_DENIED_NOT_DISABLE, "card reg denied but not disable"), // 卡注册被拒，会继续尝试
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_EXCEPT_NET_FAIL, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_EXCEPT_NET_FAIL, "network not ok!"), // 卡网络不可用
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_PHY_ROAM_DISABLE, false, ErrorCode.ErrActType.ACT_RETRY, ErrorCode.CARD_PHY_ROAM_DISABLE, "phy card roam disabled"), // 物理卡漫游被关闭

                ErrorCode.ErrCodeInfo(ErrorCode.SEED_CARD_CANNOT_BE_CDMA, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.SEED_CARD_CANNOT_BE_CDMA, "cdma card cannot be seedcard"), // 使用电信卡做种子卡
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_NO_AVAILABLE_SEEDCARD, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.CARD_NO_AVAILABLE_SEEDCARD, "no available seed sim, retry next time!"), //当前的种子卡列表不可用，下次重试
                ErrorCode.ErrCodeInfo(ErrorCode.NO_AVAILABLE_NETWORK_HERE, false, ErrorCode.ErrActType.ACT_REPORT, ErrorCode.NO_AVAILABLE_NETWORK_HERE, "no available network in this place"), //当前无可用网络，退出，让用户稍后重试
                ErrorCode.ErrCodeInfo(ErrorCode.SEED_CARD_DEPTH_OPT_CLOSE, false, ErrorCode.ErrActType.ACT_REPORT, ErrorCode.SEED_CARD_DEPTH_OPT_CLOSE, "seed localcard deep opt close"), //物理卡不可用，而且本国深度优化关闭，需要提示用户开启。
                ErrorCode.ErrCodeInfo(ErrorCode.CARD_PHY_ROAM_UI_CONFIG_DISABLE, false, ErrorCode.ErrActType.ACT_REPORT, ErrorCode.CARD_PHY_ROAM_UI_CONFIG_DISABLE, "phy roam ui config disable"), //物理卡不可用，而且本国深度优化关闭，需要提示用户开启。

                // 以下是内部使用错误码
                ErrorCode.ErrCodeInfo(ErrorCode.INNER_USER_CANCEL, false, ErrorCode.ErrActType.ACT_NONE, ErrorCode.INNER_USER_CANCEL, "user cancel") // 用户取消
        )

        return errcodeList
    }

    override fun getNetRestrictOperater(): INetRestrictOperator {
        return netRestrictOperator
    }

    private fun loginReq() {
        ServiceManager.systemApi.setDefaultDataSlotId(ServiceManager.accessSeedCard.getCard().slot)
        try {
            //从productNV分区读取用户信息
            val inn = ObjectInputStream(FileInputStream(file_temp))
            inforead = inn.readObject() as LoginInfo
            Configuration.username = inforead.username
            inn.close()
        } catch (e: IOException) {
            e.printStackTrace()
            inforead = LoginInfo("", "")
            JLog.logd("读取用户信息失败 ：$e")
        }
        JLog.logd("inforead == " + inforead.username + " " + inforead.passwd)
        RunningStates.saveUserName(inforead.username)
        ServiceManager.accessEntry.accessState.sendMessage(StateMessageId.USER_LOGIN_REQ_CMD, inforead)
    }

    //上报种子卡列表
    private fun startUploadSoftsimList() {
        val softlist = ArrayList<ExtSoftsimItem>()
        val mccmnc = OperatorNetworkInfo.mccmnc
        val mcc = mccmnc.substring(0, 3)
        val mnc = mccmnc.substring(3)
        val imei = OperatorNetworkInfo.imei
        val extSoftSimItem = ExtSoftsimItem(java.lang.Long.valueOf(imei), java.lang.Long.valueOf(0L), 1, 0, true, mcc, mnc, false, false, false, APN_TYPE_DEFAULT, null, null, null, null)
        softlist.add(extSoftSimItem)
        if (uploadSoftsimSub != null) {
            if (!uploadSoftsimSub!!.isUnsubscribed) {
                uploadSoftsimSub!!.unsubscribe()
            }
        }
        uploadSoftsimSub = session.UploadExtSoftsimListReq(softlist, RunningStates.getUserName(), imei, false, 0, 35)
                .subscribe({ uploadResp ->
                    JLog.logd("uploadSoftsimList rsp over")
                    if (uploadResp.errorCode === ErrorCode.RPC_RET_OK) {
                        JLog.logk("upload success")
                    } else {
                        JLog.logk("upload fail :" + uploadResp.errorCode)
                    }
                }, { t ->
                    t.printStackTrace()
                    JLog.loge("login fail:" + t.message)
                })
    }

    //下载软卡
    private fun startDownloadSoftsim(name: String, imei: Long, reason: Int) {
        if (extSoftsimReqSub != null) {
            if (!extSoftsimReqSub!!.isUnsubscribed) {
                extSoftsimReqSub!!.unsubscribe()
            }
        }
        extSoftsimReqSub = session.downLoadExtSoftsimReq(name, imei, 0, 35)
                .subscribe({ dispatchExtSoftsimRsp ->
                    JLog.loge("download softsim rsp over")
                    if (dispatchExtSoftsimRsp.errorCode == ErrorCode.RPC_RET_OK) {
                        JLog.logk("download softsim success")
                        softsimList = dispatchExtSoftsimRsp.softsims
                        //                                           for (SoftsimInfo softsimInfo:softsimList){//()代码结构要改
                        //                                                ServiceManager.accessEntry.softsimEntry.softsimManager.extSoftsimDB.updataExtSoftsimDb(softsimInfo);
                        //                                           }
                        startDownloadExtSoftsimBin(softsimList)//下载bin文件
                    } else {
                        JLog.logk("download softsim fail :" + dispatchExtSoftsimRsp.errorCode)
                    }
                }, { t -> JLog.logk("download softsim fail :" + t.message) }
                )
    }

    //下载软卡bin文件（包括漫游列表、资费列表、forbidden列表）
    private fun startDownloadExtSoftsimBin(softsimList: List<SoftsimInfo>) {
        val reqInfoList = ArrayList<SoftsimBinReqInfo>()
        for (list in softsimList) {
            if (list.plmnBinRef != null || list.plmnBinRef!!.length != 0) {
                val temp = list.plmnBinRef.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val typeStr = temp[1]
                var type = SoftsimBinType.PLMN_LIST_BIN
                if (typeStr.equals("1")) {//漫游文件
                    type = SoftsimBinType.PLMN_LIST_BIN
                } else if (typeStr.equals("2")) {//资费列表
                    type = SoftsimBinType.FEE_BIN
                } else if (typeStr.equals("3")) {//forbidden列表
                    type = SoftsimBinType.FPLMN_BIN
                }
                val info = SoftsimBinReqInfo(type, list.plmnBinRef)
                reqInfoList.add(info)
            }
        }
        if (extSoftsimBinReqSub != null) {
            if (!extSoftsimBinReqSub!!.isUnsubscribed) {
                extSoftsimBinReqSub!!.unsubscribe()
            }
        }
        extSoftsimBinReqSub = session.downLoadExtSoftsimBinReq(reqInfoList, 35)
                .subscribe({ softsimBinRsp ->
                    JLog.loge("getsoftsimbin softsim rsp over")
                    if (softsimBinRsp.errorCode == ErrorCode.RPC_RET_OK) {
                        JLog.logk("getsoftsimbin success")
                        for (info in softsimList) {
                            val binname = info.plmnBinRef
                            val fileName = "00001" + binname.substring(binname.length - 12, binname.length)
                            // store fin bin
                            if (softsimBinRsp.bins != null && softsimBinRsp.bins.size != 0) {
                                for (binFile in softsimBinRsp.bins) {
                                    if (binFile.binref == info.plmnBinRef) {
                                        val dirName = Configuration.simDataDir + fileName + ".bin"
                                        JLog.logd("write softsim bin to file:$dirName")
                                        val file = File(dirName)
                                        try {
                                            val writer = Okio.buffer(Okio.sink(file))
                                            writer.write(binFile.data.toByteArray())
                                            writer.flush()
                                            writer.close()
                                        } catch (e: IOException) {
                                            e.printStackTrace()
                                        }

                                        break
                                    }
                                }
                            }
                            val sCard = Card()
                            sCard.cardType = CardType.SOFTSIM
                            sCard.imsi = info.imsi.toString()
                            sCard.ki = info.ki
                            sCard.opc = info.opc
                            sCard.imageId = fileName
                            sCard.iccId = info.iccid
                            sCard.msisdn = info.msisdn
                            JLog.loge("onSucc: fetch softcard:$sCard")
                            val ret = CardRepository.fetchSoftCard(sCard)
                            if (ret) {
//                                  info.ki=getMd5Digest(info.ki);
//                                  info.setOpc(getMd5Digest(info.getOpc()));
                                extSoftsimDB.updataExtSoftsimDb(info)
                                JLog.logd("download softsim succ")
                            } else {
                                // failInfo.add(info.getImsi());
                            }
                        }
                        if (softsimBinRsp.bins != null && softsimBinRsp.bins.size != 0) {
                            for (binFile in softsimBinRsp.bins) {
                                extSoftsimDB.updateExtSoftsimBinData(binFile.binref, binFile.type.value, binFile.data.toByteArray())
                            }
                        }
                        val listItem = ArrayList<ExtSoftsimUpdateItem>()
                        for (info in softsimList) {
                            val item = ExtSoftsimUpdateItem(info.imsi, 0)
                            listItem.add(item)
                        }
                        startUploadExtSoftsimState(OperatorNetworkInfo.imei, SoftsimBinType.PLMN_LIST_BIN.value, listItem)
                    } else {
                        JLog.logk("getsoftsimbin fail :" + softsimBinRsp.errorCode)
                    }
                }, { t -> JLog.logk("getsoftsimbin fail :" + t.message) }
                )
    }

    private fun downloadRuleList(imei: String) {
        if (downLoadRuleSub != null) {
            if (!downLoadRuleSub!!.isUnsubscribed) {
                downLoadRuleSub!!.unsubscribe()
            }
        }
        downLoadRuleSub = session.downLoadRuleList(imei, 35).subscribe(
                { uploadResp ->
                    JLog.logd("req softsim rulelist rsp over")
                    if (uploadResp.errorCode === ErrorCode.RPC_RET_OK) {
                        JLog.logk("req softsim rulelist success")
                    } else {
                        JLog.logk("req softsim rulelist fail :" + uploadResp.errorCode)
                    }
                },
                { t ->
                    t.printStackTrace()
                    JLog.loge("req softsim rulelist fail:" + t.message)
                }
        )
    }

    private fun getMd5Digest(str: String): String {
        var newstr = "null"
        try {
            val md5 = MessageDigest.getInstance("MD5")
            newstr = HexUtil.encodeHexStr(md5.digest(str.toByteArray(charset("utf-8"))))
            JLog.logd("getMd5Digest: $str -> $newstr")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            return newstr
        }
    }

    //上报ExtSoftsim状态
    private fun startUploadExtSoftsimState(imei: String, type: Int, list: List<ExtSoftsimUpdateItem>) {
        if (extSoftsimStateSub != null) {
            if (!extSoftsimStateSub!!.isUnsubscribed) {
                extSoftsimStateSub!!.unsubscribe()
            }
        }
        extSoftsimStateSub = session.upLoadExtSoftsimState(imei, type, list, 35).subscribe({ extSoftsimUpdateRsp ->
            JLog.loge("download softsim rsp over")
            if (extSoftsimUpdateRsp.errorCode === ErrorCode.RPC_RET_OK) {
            } else {
                JLog.logk("download softsim fail :" + extSoftsimUpdateRsp.errorCode)
            }
        }, { t ->
            JLog.logk("download softsim fail :" + t.message)
        })
    }

    private fun isPhyCardOn(): Boolean {
        var simState = TelephonyManager.SIM_STATE_UNKNOWN
        try {
            val iSub = ISub.Stub.asInterface(android.os.ServiceManager.getService("isub"))
            if (iSub != null) {
                simState = iSub.getSimStateForSlotIdx(Configuration.seedSimSlot)
            }
        } catch (ex: RemoteException) {
        }
        JLog.logd("sim state  $simState")
        return simState in arrayListOf(TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                TelephonyManager.SIM_STATE_READY)
    }
}
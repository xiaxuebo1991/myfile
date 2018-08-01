package com.ucloudlink.refact.product.mifi

/**
 * Created by zhifeng.gao on 2018/1/14.
 */
import android.content.Context
import android.os.HandlerThread
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import android.text.TextUtils
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
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.platform.sprd.flow.netlimit.SprdNetRestrictOperator
import com.ucloudlink.refact.product.ProductFormBase
import com.ucloudlink.refact.product.mifi.PhyCardApn.PhyCardApnSetting
import com.ucloudlink.refact.product.mifi.cardselect.CardSelect
import com.ucloudlink.refact.product.mifi.connect.MifiMsgClient
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgEncode
import com.ucloudlink.refact.product.mifi.seedUpdate.SeedUpdateTask
import com.ucloudlink.refact.product.mifi.seedUpdate.intf.IBusinessTask
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimDataBackup
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum
import com.ucloudlink.refact.utils.FileIOUtils
import com.ucloudlink.refact.utils.HexUtil
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.decodeApns
import okio.Okio
import rx.Subscription
import java.io.*
import java.security.MessageDigest
import java.util.*

open class MifiProductForm(val context: Context) : ProductFormBase(context) {
    lateinit var session: Session
    lateinit var extSoftsimDB: ExtSoftsimDB
    var uploadSoftsimSub: Subscription? = null
    var extSoftsimReqSub: Subscription? = null
    var extSoftsimBinReqSub: Subscription? = null
    var extSoftsimStateSub: Subscription? = null
    var downLoadRuleSub: Subscription? = null
    lateinit var softsimList: List<SoftsimInfo>//下载的软卡列表
    lateinit var mifiMsgClient: MifiMsgClient
    lateinit var phyCardApnSetting: PhyCardApnSetting
    lateinit var info: LoginInfo
    lateinit var phyCardSelect: CardSelect
    val file = File("/productinfo/info.obj")
    var inforead: LoginInfo = LoginInfo("", "")
    var loopThread = HandlerThread("MifiProductForm")
    val netRestrictOperator by lazy { SprdNetRestrictOperator() }
    var toGetSubidRunnable : Runnable? =null
    var cardListener : CardStateMonitor.CardStateListen? =null
    var mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var phoneListener =PhoneListener()

    private inner class PhoneListener : PhoneStateListener() {
        override fun onServiceStateChanged(serviceState: ServiceState?) {
            super.onServiceStateChanged(serviceState)
            logd("PhoneListener onServiceStateChanged! $serviceState")
            if(serviceState!=null&&!TextUtils.isEmpty(serviceState.getOperatorAlphaLong())){
                var seedOperator=serviceState.getOperatorNumeric()
                if (!TextUtils.isEmpty(seedOperator)
                        && seedOperator.length > 3 && !seedOperator.equals("00000") && !seedOperator.equals("000000")
                        ||ServiceManager.phyCardWatcher.isCardRoam(Configuration.cloudSimSlot)) {
                    logd("PhoneListener onServiceStateChanged! all info ok")
                    mPhone.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
                    if (!TextUtils.isEmpty(seedOperator)
                            && seedOperator.length > 3) {
                        phyCardSelect.mcc = seedOperator!!.substring(0, 3)
                    }
                    loginReqFromCheck()
                }
            }

        }
    }

    init {
        loopThread.start()
    }

    override fun startDownLoadSoftsim() {
        startUploadSoftsimList()
        startDownloadSoftsim(RunningStates.getUserName(), java.lang.Long.valueOf(OperatorNetworkInfo.imei), 0)
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

    override fun getLoginType(): Int {
        return 0
    }

    override fun getProductType(): ProductTypeEnum {
        return ProductTypeEnum.MIFI
    }

    override fun ifNeedCheckBeforeLogin(): Boolean {
        return false
    }

    override fun init() {
        super.init()
        if (!file.exists()) {
            file.createNewFile()
            try {//写入一个默认对象
                val out = ObjectOutputStream(FileOutputStream(file))
                out.writeObject(LoginInfo("", ""))
                out.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        pinVerify()
        session = Session()
        extSoftsimDB = ExtSoftsimDB(context)
        mifiMsgClient = MifiMsgClient(context)
        phyCardApnSetting = PhyCardApnSetting(context)
        phyCardSelect = CardSelect(context, mifiMsgClient.mqttClient, loopThread.looper)
        JLog.logd("ServiceRestore.getExceptionStartFlag():${ServiceRestore.getExceptionStartFlag()}  ServiceRestore.isReBoot():${ServiceRestore.isReBoot()} phycardExit:${ServiceManager.systemApi.isCloudPhyCardExist()}")
//        if(SystemProperties.get("ucloud.oem.conf.local_sim") == "true") {
            if (ServiceRestore.getExceptionStartFlag() && !ServiceRestore.isReBoot() && !ServiceManager.systemApi.isCloudPhyCardExist()) {
                loginReq()
            } else {
                checkCardSelect()
            }
//        }else{
//            loginReq()
//        }
    }

    /**
     * 由于还是存在未识别物理卡/漫游情况，现添加如下方案
     * 1.如果此时未检测到实体卡则直接进行选卡
     * 2.如果检测到，判断是否已经load和subid，可用则开始检查mcc
     * 3.没有load或者subid则监听卡状态更新，并且10s后自动取消监听
     * 4.监听触发后或者10S后会再次判断load和subid，可用则开始检查mcc，若10s后还是不可用则选卡
     * 5.检查mcc先判断是否漫游，漫游则进行选卡
     * 6.未漫游则判断种子卡是否存在，不存在则选卡
     * 7.若存在则获取物理卡状态及种子卡运营商信息，卡状态inservice或者运营商信息可用则选卡
     * 8.运营商信息和卡状态都不可用则监听phonelistener的LISTEN_SERVICE_STATE
     * 9.phonelistenr中若serverstate信息可用，则选卡
     *
     * 影响情况，插入实体卡时启动到可用时间
     * 部分漫游卡驻网时间极长
     */
    private fun checkCardSelect(){
        logd("checkCardSelect! ")
        if(ServiceManager.systemApi.isCloudPhyCardExist()){
            logd("checkCardSelect! card present")
            if(isPhyCardOK()){
                logd("checkCardSelect! card ok")
                checkHadMcc()
            }else{
                logd("checkCardSelect! card not ok")
                startListener()
            }
        }else{
            logd("checkCardSelect! card not present so checkcard")
            loginReqFromCheck()
        }
    }

    private fun checkHadMcc(){
        logd("checkHadMcc!")
        if (ServiceManager.phyCardWatcher.isCardRoam(Configuration.cloudSimSlot)) {
            logd("checkHadMcc! card roam")
            loginReqFromCheck()
        } else {
            logd("checkHadMcc! card not roam")
            waitRoamRegAndCheckCard()
        }
    }

    private fun loginReqFromCheck(){
        //因为在checkccardselect中要保存卡槽状态，所以还是得进去
        if (phyCardSelect.checkCardSelect() == 0) {
            loginReq()
        }
    }

    private fun waitRoamRegAndCheckCard(){
        logd("waitRoamRegAndCheckCard!")
        if(!ServiceManager.systemApi.isPhySeedExist()){
            logd("waitRoamRegAndCheckCard! seed card not exit so checkcard")
            loginReqFromCheck()
            return
        }
        var subId= ServiceManager.systemApi.getSubIdBySlotId(Configuration.cloudSimSlot)
        val serviceState = ServiceManager.systemApi.getServiceStateForSubscriber(subId)
        val seedOperator = mPhone.getNetworkOperatorForPhone(Configuration.seedSimSlot)
        logd("waitRoamRegAndCheckCard! serviceState:$serviceState seedOperator:$seedOperator")
        if(serviceState != null && serviceState.state== ServiceState.STATE_IN_SERVICE
                ||!TextUtils.isEmpty(seedOperator)&&seedOperator.length>3&&!seedOperator.equals("00000")&&!seedOperator.equals("000000")){
            logd("waitRoamRegAndCheckCard! state is in_service,so checkcard")
            if (!TextUtils.isEmpty(seedOperator)
                    && seedOperator.length > 3) {
                phyCardSelect.mcc = seedOperator!!.substring(0, 3)
            }
            loginReqFromCheck()
        }else{
            logd("waitRoamRegAndCheckCard! state is not in_service,so listen")
            mPhone.listen(phoneListener, PhoneStateListener.LISTEN_SERVICE_STATE )
        }


    }

    private fun isPhyCardOK(): Boolean{
        if(ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].state== CardStateMonitor.SIM_STATE_LOAD
                && ServiceManager.systemApi.getSubIdBySlotId(Configuration.seedSimSlot)>0)
            return true
        return false
    }

    private fun startListener() {
        logd("startListener! wait phy load")
        cardListener = CardStateMonitor.CardStateListen { slotId, subId, state ->
            logd("startListener! new phy state slotId:$slotId subId:$subId state:$state")
            if (slotId == Configuration.seedSimSlot && subId > 0 && state == CardStateMonitor.SIM_STATE_LOAD) {
                removeListener()
                checkHadMcc()
            }
        }
        ServiceManager.simMonitor.addCardStateListen(cardListener)
        if (toGetSubidRunnable == null) {
            toGetSubidRunnable = Runnable {
                logd("startListener! wait load had 10s")
                removeListener()
                if (isPhyCardOK()) {
                    logd("startListener! wait load had 10s ok")
                    checkHadMcc()
                } else {
                    logd("startListener! cant load card in 10s ,so checkcard!")
                    loginReqFromCheck()
                }
            }
        }
        ServiceManager.handler.postDelayed(toGetSubidRunnable, 10000)
    }

    private fun removeListener() {
        logd("removeListener!")
        if (toGetSubidRunnable != null) {
            logd("removeListener! remove toGetSubidRunnable")
            ServiceManager.handler.removeCallbacks(toGetSubidRunnable)
        }
        if (cardListener == null)
            return
        var _cardListener = cardListener
        cardListener = null
        Thread{
            logd("removeListener! remove cardListener")
            ServiceManager.simMonitor.removeStatuListen(_cardListener)
        }.start()
    }

    override fun pinVerify() {
        // 解PIN已转由底层执行
//        val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//        val simState = mPhone.getSimState(Configuration.seedSimSlot)
//        JLog.logd("pinVerify simState:" + simState)
//        try {
//            if (simState == TelephonyManager.SIM_STATE_PIN_REQUIRED || simState == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
//                val pin: ByteArray = ByteArray(4)
//                val subMgr = SubscriptionManager.from(context)
//                val subInfo = subMgr.getActiveSubscriptionInfoForSimSlotIndex(Configuration.seedSimSlot)
//                val iccid: String = subInfo.getIccId()
//                val ret: Int = SoftSimNative.calcPin(iccid, pin)
//                val sPin: String = kotlin.String(pin, Charset.forName("UTF-8"));
//                JLog.logd("pinVerify calcPin:ret=" + ret + ",iccid=" + iccid + ",pin=" + sPin);
//
//                val service = ITelephony.Stub.asInterface(android.os.ServiceManager.getService(Context.TELEPHONY_SERVICE))
//                if (simState == TelephonyManager.SIM_STATE_PIN_REQUIRED) {
//                    val supplyRet = service.supplyPinForSubscriber(ServiceManager.systemApi.getSubIdBySlotId(Configuration.seedSimSlot), sPin);
//                    //val supplyRet = mPhone.supplyPin(sPin);
//                    JLog.logd("pinVerify supplyPin:" + supplyRet + " sub:" + ServiceManager.systemApi.getSubIdBySlotId(Configuration.seedSimSlot));
//                } else if (simState == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
//                    val supplyRet = service.supplyPukForSubscriber(ServiceManager.systemApi.getSubIdBySlotId(Configuration.seedSimSlot), "00000000", sPin);
//                    //val supplyRet = mPhone.supplyPuk("00000000",sPin);
//                    JLog.logd("pinVerify supplyPuk:" + supplyRet);
//                }
//                //执行解pin后休眠一秒，临时方案，张宪说会去掉，把解pin由底层执行
//                Thread.sleep(1000)
//            }
//        } catch (e: Exception) {
//            JLog.logd("pinVerify Exception:$e")
//        }
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
        phyCardSelect.reorderCardList(cardList)
        return if (cardList.isEmpty()) ErrorCode.LOCAL_NO_SEED_CARD else 0
    }

    private fun loginReq() {
        ServiceManager.systemApi.setDefaultDataSlotId(Configuration.seedSimSlot)
        try {
            //从productNV分区读取用户信息
            val inn = ObjectInputStream(FileInputStream(file))
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

    //note:mifi 目前只有7.0 如果8.0 会报找不到“getSimStateForSlotIdx”
    private fun isPhyCardOn(): Boolean {
        if (ServiceManager.systemApi.isPhySeedExist()){
            return true
        }
        val simState = ServiceManager.systemApi.getSimState(Configuration.seedSimSlot)
        JLog.logd("sim state  $simState")
        return simState in arrayListOf(TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                TelephonyManager.SIM_STATE_READY)
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
        val lock = Object()
        var ret = 0
        MsgEncode.setNetworkGpsSwitch(mifiMsgClient.mqttClient, hardEnable, netGps, 3).subscribe(
                {
                    logd("recv msg! $it")
                    synchronized(lock) {
                        lock.notifyAll()
                    }
                },
                {
                    logd("set failed!")
                    ret = 0
                }
        )
        synchronized(lock) {
            lock.wait(3 * 1000)
        }
        logd("setGpsConfig $hardEnable $netGps $ret ")
        return 0
    }

    override fun reloginAfterMccChange(mcc: String): Boolean {
        return phyCardSelect.mccChange(mcc)
    }

    override fun getSeedUpdateTask(): IBusinessTask = SeedUpdateTask()

    override fun isSeedAlwaysOn(): Boolean = true

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
            logd("[getSeedSimFplmnRefByImsi] file is not exists ")
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
            logd("[getSeedSimPlmnFeeListByImsi] file is not exists ")
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
                        loge("[getSeedSimPlmnFeeListByImsi] wrong format $line ")
                    }
                }

            }catch (e:Exception){
                loge("catch Exception ${e.message}")
            }
            line = reader.readLine()
        }


        return Array(list.size, { list[it] })

    }

    override fun dataRecover() {
        super.dataRecover()
        SoftSimDataBackup.recover()
    }

    override fun getNetRestrictOperater(): INetRestrictOperator {
        return netRestrictOperator
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
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_DEVICE_IS_DISABLE, false, ErrorCode.ErrActType.ACT_EXIT, 118, "device is disable"), // imei设备被停用
                ErrorCode.ErrCodeInfo(ErrorCode.LOCAL_HOST_LOGIN_SLEEP, false, ErrorCode.ErrActType.ACT_EXIT, ErrorCode.LOCAL_HOST_LOGIN_SLEEP, "be grabbed by others"), // 休眠抢卡
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
}
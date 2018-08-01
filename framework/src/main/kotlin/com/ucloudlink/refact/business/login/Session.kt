package com.ucloudlink.refact.business.login

import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.framework.tasks.UploadFlowTask
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.LoginRequestInfo
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.netcheck.NetInfo
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.softsim.CardRepository
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logk
import com.ucloudlink.refact.utils.decodeApns
import rx.Observable
import rx.Single
import rx.Subscription
import java.util.*

/**
 * 会话：
 * 一次登录会话
 * 实现登录，退登录，请求分配云sim操作
 */
class Session {
//    var accountName:String? = null
//    var sessionId:String? = null
//    var imsi:String by Delegates.notNull() //wlmark imsi defined in here // notNull what's meaning?
//    var virtImei:String ? = null
    val loginType:Int = ServiceManager.productApi.getLoginType() //0:租赁用户，通过绑定关系登录   1： 用户名密码
//    var eplmnlist: List<EquivalentPlmnInfo> ? = null
//    var netInfo: NetInfo? = null
//    var apnValid: Boolean = false

    /**
     * @param name 用户名
     * @param pwd 密码
     * @return Observable<Any> 监听器如果有数据则login成功，否则失败。可通过onError判断login失败原因
     */
    fun login(name: String?, pwd: String?, timeout: Int, relogin: Boolean, reason: String?,isBUSINESS_RESTART:Boolean): Single<LoginResp> {
        var loginReason = 1 //正常登陆

        if (isBUSINESS_RESTART){
            //大循环导致重登陆
            loginReason = 3
        }else if (reason!=null && reason.indexOf("RPC:2160001")!=-1){
            //session超时重登录
            loginReason = 2
        }else if(reason!=null && reason.equals("EVENT_S2CCMD_RELOGIN")){
            //s2c命令重登陆
            loginReason = 4
        }else if(reason!=null && reason.equals("S2c_redirect_route")){
            //路由重定向重登录
            loginReason = 5
        }else if(reason !=null && reason.equals("modem_reset")){
            loginReason = 7
        }
        logd("login reason = $reason ,reason code = $loginReason，isBUSINESS_RESTART = $isBUSINESS_RESTART")

        if(relogin) {
            logd("Action:Logout," + "seed flow:" + UploadFlowTask.getSeedTxFlow() + "," + UploadFlowTask.getSeedRxFlow())
        }

        var loginSub: Subscription? = null

        return Single.create<LoginResp> { sub ->
            loginSub = Requestor.setSessionId(null).requestLogin({
                val loginInfo: NetInfo = OperatorNetworkInfo.getNetworkInfoBySlot("seed")
                val loginRequestInfo = LoginRequestInfo(name, pwd, loginType, loginInfo, loginReason)
                logk("login request : $loginRequestInfo")

                return@requestLogin loginRequestInfo
            }, timeout).subscribe(
                    {
                        if(it is LoginResp){
                            sub.onSuccess(it)
                        }else{
                            sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                        }
                    },
                    {
                        sub.onError(it)
                    }
            )
        }.doOnUnsubscribe {
            if (loginSub != null) {
                if (!(loginSub as Subscription).isUnsubscribed) (loginSub as Subscription).unsubscribe()
            }
        }
    }


    fun setCloudSimApn(apn: CharSequence?,imsi:String): Boolean {
        val numeric = getNumeric(imsi)
        Configuration.cloudSimApns = decodeApns(apn.toString(), numeric)
        return (Configuration.cloudSimApns != null && !Configuration.cloudSimApns!!.isEmpty())
    }
    private fun getNumeric(imsi: String): String {
        return imsi.subSequence(0, 5).toString()
    }

    fun release(): Observable<Any> {
        logk("release session!!!!")
        Requestor.mHandler.obtainMessage(Requestor.EVENT_STOP_RELEASE_CHANNEL).sendToTarget()
        Requestor.mHandler.obtainMessage(Requestor.EVENT_STOP_RELEASE_APDU).sendToTarget()
        Requestor.setSessionId(null).resetUserRequestor()
        Requestor.setSessionId(null).resetSeedRequestor()
        return Observable.empty()
    }

    fun resetAllChannel(){
        logk("release session!!!! do not set session to null!!!")
        Requestor.mHandler.obtainMessage(Requestor.EVENT_STOP_RELEASE_CHANNEL).sendToTarget()
        Requestor.mHandler.obtainMessage(Requestor.EVENT_STOP_RELEASE_APDU).sendToTarget()
        Requestor.resetUserRequestor()
        Requestor.resetSeedRequestor()
        Requestor.resetApduStatus()
    }

    /**
     * 退出登录
     * @return Observable<Any>
     */
    fun logoutReq(timeout: Int): Single<Any> {
        logk("logout req")
//        stopHeartBeat()
        var subRequestLogout: Subscription? = null
        return Single.create<Any> { sub ->
            subRequestLogout = Requestor.requestLogout(timeout).subscribe({
                logk("logout succ :" + it)
                Requestor.setSessionId(null)
                sub.onSuccess(true);
            },{
                logk("logout error:" + it)
                it.printStackTrace()
                Requestor.setSessionId(null)
                sub.onError(it)
            })
        }.doOnUnsubscribe {
            if (subRequestLogout != null) {
                if (!(subRequestLogout as Subscription).isUnsubscribed) (subRequestLogout as Subscription).unsubscribe()
            }
        }
    }

    /**
     * 请求分配云Sim卡
     * @return Observable<Card> Card包含当前分配的卡信息如imsi，apn等，并不包含镜像文件
     */
    fun requestCloudCard(imsi: String, eplmnlist:List<EquivalentPlmnInfo>, apnStr:String, timeout: Int): Single<Card> {
        logd("debug requestCloudCard")
        var subRequestDownloadCloudCard: Subscription? = null
        val numeric = getNumeric(imsi)
        return Single.create<Card> { sub ->
            val cloudCard = Card(imsi = imsi, slot = Configuration.cloudSimSlot,cardType = CardType.VSIM, eplmnlist = eplmnlist,
                    apn= decodeApns(apnStr, numeric))
            val ret = SoftSimNative.queryCard(imsi)
            if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
                sub.onSuccess(cloudCard)
            } else {
                subRequestDownloadCloudCard = Requestor.requestDownloadCloudCard(timeout).subscribe ({
                    val buf = it as ByteArray
                    CardRepository.storeCloudCard(imsi, buf).subscribe {//wlmark storeCard?
                        sub.onSuccess(cloudCard)
                    }
                }, {
                    sub.onError(it)
                })
            }
        }.doOnUnsubscribe {
            if (subRequestDownloadCloudCard != null) {
                if (!(subRequestDownloadCloudCard as Subscription).isUnsubscribed) (subRequestDownloadCloudCard as Subscription).unsubscribe()
            }
        }
    }
    
    fun requestSwitchVsim(reason: Int,subReason : Int,timeout: Int): Single<SwitchVsimResp> {
        logd("switch vsim request:" + NetworkManager.plmnList)
        logd("switch vsim request OperatorNetworkInfo :" + OperatorNetworkInfo.seedPlmnList)
        //return Requestor.requestSwitchCloudSim(reason, "reserve", NetworkManager.plmnList, timeout).flatMap {
        var switchVsimSub: Subscription? = null
        return Single.create<SwitchVsimResp> { sub ->
            switchVsimSub = Requestor.requestSwitchCloudSim(reason, subReason,"reserve", OperatorNetworkInfo.seedPlmnList, timeout).subscribe(
                    {
                        if( it is SwitchVsimResp){
                            sub.onSuccess(it)
                        }else{
                            sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                        }
                    },
                    {
                        sub.onError(it)
                    }
            )
        }.doOnUnsubscribe {
            if(switchVsimSub != null){
                if (!(switchVsimSub as Subscription).isUnsubscribed) (switchVsimSub as Subscription).unsubscribe()
            }
        }
    }

    fun requestHeartBeat(timeout: Int): Single<Any> {
        logd("requestHeartBeat start!")
        var subRequestHeartBeat: Subscription? = null
        return Single.create<Any> { sub ->
            subRequestHeartBeat = Requestor.requestHeartBeat(timeout).subscribe(
                    {
                        if(it is HeartBeatResp) {
                            sub.onSuccess(it)
                        }else{
                            sub.onError(Exception(ErrorCode.PARSE_HEADER_STR + it.toString()))
                        }
                    },
                    {
                        sub.onError(it)
                    }
            )
        }.doOnUnsubscribe {
            if (subRequestHeartBeat != null) {
                if (!(subRequestHeartBeat as Subscription).isUnsubscribed) (subRequestHeartBeat as Subscription).unsubscribe()
            }
        }
    }

    /**
     * 分卡
     */
    fun dispatchVsim(timeout: Int): Single<DispatchVsimResp> {
        logk("dispatch vsim start!!!")
        var dispatchVsimSub: Subscription? = null
        return Single.create<DispatchVsimResp> { sub ->
            dispatchVsimSub = Requestor.requestDispatchVsimReq(timeout).subscribe({
                logd("debug getVsimInfo rsp:$it")
                if (it is DispatchVsimResp){
                    sub.onSuccess(it)
                } else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            },
            {
                sub.onError(it)
            })
        }.doOnUnsubscribe {
            if (dispatchVsimSub != null) {
                if (!(dispatchVsimSub as Subscription).isUnsubscribed) (dispatchVsimSub as Subscription).unsubscribe()
            }
        }
    }

    /**
     * 获取卡信息
     */
    fun getVsimInfo(timeout: Int): Single<GetVsimInfoResp> {
        var getVsimInfoSub: Subscription? = null
        return Single.create<GetVsimInfoResp> { sub ->
            getVsimInfoSub = Requestor.requestVsimInfo(timeout).subscribe({
                logd("debug getVsimInfo rsp:$it")
                if (it is GetVsimInfoResp) {
                    sub.onSuccess(it)
                } else {
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            },
            {
                sub.onError(it)
            })
        }.doOnUnsubscribe {
            if (getVsimInfoSub != null) {
                if (!(getVsimInfoSub as Subscription).isUnsubscribed) (getVsimInfoSub as Subscription).unsubscribe()
            }
        }
    }

    /*下软卡时候登录*/
    fun startSimpleLogin(loginType:Int,username:String,passwd:String,devideType:String,version:String,imei:Long,timeout: Long): Single<SimpleLoginRsp> {
        var subSimpleLogin: Subscription? = null
        return Single.create<SimpleLoginRsp>{ sub->
            subSimpleLogin = Requestor.startSimpleLogin(loginType,username,passwd,devideType,version,imei,timeout).subscribe({
                if(it is SimpleLoginRsp){
                    sub.onSuccess(it)
                }else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            },
                    {
                        sub.onError(it)
                    })
        }.doOnUnsubscribe {
            if (subSimpleLogin != null) {
                if (!(subSimpleLogin as Subscription).isUnsubscribed) (subSimpleLogin as Subscription).unsubscribe()
            }
        }
    }

    /*
    上报种子卡列表
    * */
    fun UploadExtSoftsimListReq(softsimList: ArrayList<ExtSoftsimItem>, user:String, imei:String, ruleExist:Boolean, reason:Int, timeout: Long): Single<UploadExtSoftsimListRsp> {
        var subUploadExtSoftsimListReq: Subscription? = null
        return Single.create<UploadExtSoftsimListRsp>{ sub->
            subUploadExtSoftsimListReq = Requestor.uploadExtSoftsimList(softsimList,user,imei,ruleExist,reason,timeout).subscribe({
                if(it is UploadExtSoftsimListRsp){
                    sub.onSuccess(it)
                }else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            },
                    {
                        sub.onError(it)
                    })
        }.doOnUnsubscribe {
            if (subUploadExtSoftsimListReq != null) {
                if (!(subUploadExtSoftsimListReq as Subscription).isUnsubscribed) (subUploadExtSoftsimListReq as Subscription).unsubscribe()
            }
        }
    }

    /**
     * 请求规则文件
     * **/
    fun ExtSoftsimRuleReq(imei:String,timeout: Long): Single<ExtSoftsimRuleRsp> {
        var softsimRuleSub: Subscription? = null
        return Single.create <ExtSoftsimRuleRsp>{ sub->
            softsimRuleSub = Requestor.softsimRuleReq(imei,timeout).subscribe(
                    {
                        if(it is ExtSoftsimRuleRsp){
                            sub.onSuccess(it)
                        }else{
                            sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                        }
                    },
                    {
                        sub.onError(it)
                    })
        }.doOnUnsubscribe {
            if (softsimRuleSub != null) {
                if (!(softsimRuleSub as Subscription).isUnsubscribed) (softsimRuleSub as Subscription).unsubscribe()
            }
        }
    }


    /**
     * 请求分配种子卡
     * **/
    fun downLoadExtSoftsimReq(name: String,imei:Long,reason: Int,timeout: Long): Single<DispatchExtSoftsimRsp> {
        var subRequestDownloadSoftsim: Subscription? = null
        return Single.create<DispatchExtSoftsimRsp> { sub ->
            subRequestDownloadSoftsim = Requestor.requestDownloadSoftsim(name, imei, reason, timeout).subscribe ({
                if(it is DispatchExtSoftsimRsp){
                    sub.onSuccess(it)
                }else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            }, {
                sub.onError(it)
            })
        }.doOnUnsubscribe {
            if (subRequestDownloadSoftsim != null) {
                if (!(subRequestDownloadSoftsim as Subscription).isUnsubscribed) (subRequestDownloadSoftsim as Subscription).unsubscribe()
            }
        }
    }

    /**
     * 下载种子卡bin文件
     * **/
    fun downLoadExtSoftsimBinReq(reqInfoList:List<SoftsimBinReqInfo>, timeout: Long): Single<GetSoftsimBinRsp> {
        var subReqSoftsimbin: Subscription? = null
        return Single.create<GetSoftsimBinRsp> { sub ->
            subReqSoftsimbin = Requestor.getExtSoftsimBin(reqInfoList,timeout).subscribe ({
                if(it is GetSoftsimBinRsp){
                    sub.onSuccess(it)
                }else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            }, {
                sub.onError(it)
            })
        }.doOnUnsubscribe {
            if (subReqSoftsimbin != null) {
                if (!(subReqSoftsimbin as Subscription).isUnsubscribed) (subReqSoftsimbin as Subscription).unsubscribe()
            }
        }
    }

    /*
    上报软卡状态
    * */
    fun upLoadExtSoftsimState(imei:String, type: Int, list:List<ExtSoftsimUpdateItem>, timeout: Long): Single<ExtSoftsimUpdateRsp> {
        var subReqUploadSoftsimState: Subscription? = null
        return Single.create<ExtSoftsimUpdateRsp> { sub ->
            subReqUploadSoftsimState = Requestor.uploadExtSoftsimState(imei,type,list,timeout).subscribe ({
                if(it is ExtSoftsimUpdateRsp){
                    sub.onSuccess(it)
                }else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            }, {
                sub.onError(it)
            })
        }.doOnUnsubscribe {
            if (subReqUploadSoftsimState != null) {
                if (!(subReqUploadSoftsimState as Subscription).isUnsubscribed) (subReqUploadSoftsimState as Subscription).unsubscribe()
            }
        }
    }
    /*下载软卡规则文件*/
    fun downLoadRuleList(imei:String,timeout: Long):Single<ExtSoftsimRuleRsp>{
        var subReqSoftsimRule:Subscription? = null
        return Single.create<ExtSoftsimRuleRsp>{
            sub->
            subReqSoftsimRule = Requestor.downloadRuleList(imei,timeout).subscribe ({
                if(it is ExtSoftsimRuleRsp){
                    sub.onSuccess(it)
                }else{
                    sub.onError(Throwable(ErrorCode.PARSE_HEADER_STR + it.toString()))
                }
            }, {
            sub.onError(it)
        })
        }.doOnUnsubscribe {
            if (subReqSoftsimRule != null) {
                if (!(subReqSoftsimRule as Subscription).isUnsubscribed) (subReqSoftsimRule as Subscription).unsubscribe()
            }
        }
    }
}
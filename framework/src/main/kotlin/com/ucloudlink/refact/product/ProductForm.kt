package com.ucloudlink.refact.product

import android.os.Message
import com.ucloudlink.framework.protocol.protobuf.LoginResp
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.struct.LoginInfo
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.INetRestrictOperator
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.plmnselect.PlmnFee
import com.ucloudlink.refact.product.mifi.PhyCardApn.PhyCardApnSetting
import com.ucloudlink.refact.product.mifi.seedUpdate.intf.IBusinessTask
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum
import java.util.*

/**
 * Created by shiqianhua on 2018/1/13.
 */


interface ProductForm {
    fun dataRecover()//数据恢复。

    fun init()  // 初始化，
    fun processOrderInfo(orderId:String,serviceEnable:Boolean,haveEnableBefore:Boolean,cardList:ArrayList<Card>):Int // 获取软卡列表
    fun getLoginType():Int//获取登录类型
    fun getProductType():ProductTypeEnum//获取设备类型
    fun setLoginInfo(loginInfo: LoginInfo, loginResp: LoginResp):LoginInfo//保存用户名密码
    fun startDownLoadSoftsim()//开始下载软卡
    fun ifNeedCheckBeforeLogin():Boolean//登录之前是否需要检查登录信息
    fun pinVerify()//解沃达丰物理卡pin码

    fun serviceStart()
    fun serviceExit()
    fun restoreCheck():Int
    fun needRecovery():Boolean
    fun setGpsConfig(hardEnable:Boolean, netGps:Boolean):Int
    fun reloginAfterMccChange(mcc:String):Boolean

    fun getSeedUpdateTask(): IBusinessTask? // 种子软卡更新
    fun isSeedAlwaysOn():Boolean

    fun getSeedSimFplmnRefByImsi(imsi: String, cardType: CardType): Array<String>?
    fun getSeedSimPlmnFeeListByImsi(imsi: String, cardType: CardType): Array<PlmnFee>?

    fun getErrorCodeList():ArrayList<ErrorCode.ErrCodeInfo>
    fun getNetRestrictOperater(): INetRestrictOperator

    /**
     * 是否单独处理
     */
    fun dealThisEvent(eventId : Int ,message : Message): Boolean
}
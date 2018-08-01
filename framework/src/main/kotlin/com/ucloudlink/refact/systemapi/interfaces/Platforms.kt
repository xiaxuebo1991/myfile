package com.ucloudlink.refact.systemapi.interfaces

import android.content.Context
import android.os.Looper
import com.ucloudlink.framework.flow.ISeedCardNet
import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.refact.business.flow.ICloudFlowCtrl
import com.ucloudlink.refact.business.flow.IFlow
import com.ucloudlink.refact.business.flow.ISeedFlowCtrl
import com.ucloudlink.refact.business.flow.netlimit.IExtraNetwork
import com.ucloudlink.refact.business.flow.netlimit.uidnet.IUidSeedCardNet
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl
import com.ucloudlink.refact.business.flow.speedlimit.INetSpeed
import com.ucloudlink.refact.business.s2ccmd.logexecutor.ILogExecutor
import com.ucloudlink.refact.channel.enabler.simcard.CloudSimEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.SeedEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.CardController
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.IPlatformTransfer
import com.ucloudlink.refact.product.mifi.PhyCardApn.ApnNetworkErrCB

/**
 * Created by jiaming.liang on 2018/1/13.
 */
interface Platforms {
    fun initPlatfrom(context: Context)
    fun getSeedEnabler(context: Context, looper: Looper): SeedEnabler2
    fun getCloudSimEnabler(context: Context, looper: Looper): CloudSimEnabler2
    fun getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP: Boolean
                            , isUiSupportSeedNetLimitByUidAndIP: Boolean): ISeedCardNetCtrl

    fun getCardController(): CardController

    fun getPlatformTransfer(): IPlatformTransfer

    fun getICloudFlowCtrl(): ICloudFlowCtrl

    fun getCloudIFlow(): IFlow

    fun getSeedIFlow(): IFlow

    fun getINetSpeed(): INetSpeed

    fun getIExtraNetwork(): IExtraNetwork

    fun getIUidSeedCardNet(): IUidSeedCardNet

    fun getISeedCardNet(): ISeedCardNet

    fun getICloudFlowProtectionCtrl(): ICloudFlowProtectionCtrl

    fun getISeedFlowCtrl(): ISeedFlowCtrl

    fun getCloudCardNetPreIfName(): String?

    fun getILogExecutor(): ILogExecutor

    fun startModemLog(arg1:Int, arg2:Int, obj:Any?)
    fun stopModemLog(arg1: Int, arg2: Int, obj: Any?)
    fun clearModemLog(arg1: Int, arg2: Int, obj: Any?)
    fun uploadLog(obj: Any?)

    fun isPhySeedExist():Boolean // 标识物理种子卡是否在位过
    fun isCloudPhyCardExist():Boolean // 标识物理卡云卡是否在位过

    fun registerNetworkErrCB(cb: ApnNetworkErrCB)
    fun deregsiterNetworkErrCB(cb: ApnNetworkErrCB)
    fun updateNetworkErrCode(phoneId: Int, errcode: Int)
}

interface PlatformsConfig {
    /**
     * 云卡在线时，是否支持dds切换
     */
    fun isSupportSwitchDDS():Boolean

    /**
     * 种子卡切换动作类型
     * @return 0：不关闭云卡
     * @return 1：软硬卡切换才关闭云卡
     * @return 2：任意卡切换就关闭云卡
     */
    fun switchSeedMode():Int

    fun isSupportSmartRecovery():Boolean
}
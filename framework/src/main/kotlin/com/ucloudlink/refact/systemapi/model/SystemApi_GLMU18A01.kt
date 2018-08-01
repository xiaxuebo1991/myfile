package com.ucloudlink.refact.systemapi.model

import android.content.Context
import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.refact.business.flow.ICloudFlowCtrl
import com.ucloudlink.refact.business.flow.IFlow
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl
import com.ucloudlink.refact.business.flow.speedlimit.INetSpeed
import com.ucloudlink.refact.model.glmu18a01.ModelGLMU18A01
import com.ucloudlink.refact.platform.sprd.flow.*
import com.ucloudlink.refact.product.mifi.flow.protection.MifiCloudFlowProtectionCtrlImpl
import com.ucloudlink.refact.product.mifi.flow.protection.MifiCloudFlowProtectionCtrlImpl_v2
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.systemapi.vendor.UcloudlinkSprdSystemApiBase

/**
 * Created by shiqianhua on 2018/1/6.
 */
open class SystemApi_GLMU18A01(context: Context, modelInfo: ModelInfo, sdkInt: Int) : UcloudlinkSprdSystemApiBase(context, modelInfo, sdkInt) {

    // 2018-04-11  iptables优化, 使用链与规则解耦, 即：只要将规则挂载到链上就可以生效，去除频繁增加\删除规则
    private val isIptables_v2 = true

    /**
     * GLMU18A01 使用Native API
     */
    override fun isUseKeyNativeApi(): Boolean = true

    override fun getSSLVersion(): Int {
        return 0x21022001
    }

    override fun getICloudFlowCtrl(): ICloudFlowCtrl {
        return SprdU3CCloudFlowCtrl()
    }

    override fun getCloudIFlow(): IFlow {
        return SprdU3CCloudFlowImpl()
    }

    override fun getSeedIFlow(): IFlow {
        return SprdU3CSeedFlowImpl()
    }

    override fun getINetSpeed(): INetSpeed {
        return SprdU3CNetSpeedImpl()
    }

    override fun getICloudFlowProtectionCtrl(): ICloudFlowProtectionCtrl {
        if(isIptables_v2){
            return MifiCloudFlowProtectionCtrlImpl_v2()
        }
        return MifiCloudFlowProtectionCtrlImpl()
    }

    override fun getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP: Boolean, isUiSupportSeedNetLimitByUidAndIP: Boolean): ISeedCardNetCtrl {
        if(isIptables_v2){
             return SprdU3CSeedNetCtrlImpl_v2()
        }
        return SprdU3CSeedNetCtrlImpl()
    }

    override fun getCloudCardNetPreIfName(): String? {
        return "seth_lte0"
    }

    override fun getModelIf(context: Context): ModelIf {
        return ModelGLMU18A01(context)
    }
}
package com.ucloudlink.refact.systemapi.model

import android.content.Context
import com.ucloudlink.refact.business.flow.ICloudFlowCtrl
import com.ucloudlink.refact.business.flow.IFlow
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl
import com.ucloudlink.refact.business.flow.speedlimit.INetSpeed
import com.ucloudlink.refact.model.p3.ModelP3
import com.ucloudlink.refact.platform.sprd.flow.SprdP2NetSpeedImpl
import com.ucloudlink.refact.platform.sprd.flow.SprdU3CCloudFlowCtrl
import com.ucloudlink.refact.platform.sprd.flow.SprdU3CCloudFlowImpl
import com.ucloudlink.refact.product.mifi.flow.protection.MifiCloudFlowProtectionCtrlImpl
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.systemapi.vendor.UcloudlinkSprdSystemApiBase

/**
 * P2
 * Created by shiqianhua on 2018/1/6.
 */
class SystemApi_S3P18A04(context: Context, modelInfo: ModelInfo, sdkInt:Int) : UcloudlinkSprdSystemApiBase(context, modelInfo, sdkInt){
    override fun isUnderDevelopMode(): Boolean = true

    /**
     * P2 不使用Native API，使用VSimService提供的API
     */
    override fun isUseKeyNativeApi(): Boolean = false

    override fun getSSLVersion(): Int {
        return 0x21022001
    }

    override fun getICloudFlowCtrl(): ICloudFlowCtrl {
        return SprdU3CCloudFlowCtrl()
    }

    override fun getCloudIFlow(): IFlow {
        return SprdU3CCloudFlowImpl()
    }

    override fun getINetSpeed(): INetSpeed {
        return SprdP2NetSpeedImpl()
    }

    override fun getICloudFlowProtectionCtrl(): ICloudFlowProtectionCtrl {
        return MifiCloudFlowProtectionCtrlImpl()
    }

    override fun getCloudCardNetPreIfName(): String? {
        return "seth_lte0"
    }

    override fun isSupportSmartRecovery(): Boolean {
        return true
    }

    override fun getModelIf(context: Context): ModelIf {
        return ModelP3(context)
    }
}
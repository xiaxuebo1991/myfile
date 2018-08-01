package com.ucloudlink.refact.systemapi.model

import android.content.Context
import com.ucloudlink.refact.business.flow.IFlow
import com.ucloudlink.refact.model.gp17.ModelGp17
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.systemapi.vendor.UcloudlinkQcomSystempApiBase

/**
 * Created by shiqianhua on 2018/1/8.
 */
class SystemApi_C106_9(context: Context, modelInfo: ModelInfo, sdkInt:Int): UcloudlinkQcomSystempApiBase(context, modelInfo, sdkInt) {

    override fun getSeedIFlow(): IFlow {
//        if(sdk > Build.VERSION_CODES.M){// 高通平台还没有确定要上
//            return QcSeedFlowImpl()
//        }
        return super.getSeedIFlow()
    }

    override fun getModelIf(context: Context): ModelIf {
        return ModelGp17(context)
    }
}
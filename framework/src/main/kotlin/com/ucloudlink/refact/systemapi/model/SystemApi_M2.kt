package com.ucloudlink.refact.systemapi.model

import android.content.Context
import com.ucloudlink.refact.model.m2.ModelM2
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.struct.ModelInfo

/**
 * Created by shiqianhua on 2018/3/23.
 */

/**
 * TODO:M2模块化的产品，暂时使用U3C的接口，后续需要重构
 */

class SystemApi_M2(context: Context, modelInfo: ModelInfo, sdkInt: Int) : SystemApi_GLMU18A01(context, modelInfo, sdkInt) {
    override fun getModelIf(context: Context): ModelIf {
        return ModelM2(context)
    }
}
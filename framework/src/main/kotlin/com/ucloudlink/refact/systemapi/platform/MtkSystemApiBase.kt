package com.ucloudlink.refact.systemapi.platform

import android.content.Context
import com.ucloudlink.refact.systemapi.ModelConfig
import com.ucloudlink.refact.systemapi.SystemApiBase
import com.ucloudlink.refact.systemapi.struct.ModelInfo

/**
 * Created by shiqianhua on 2018/1/8.
 */
open class MtkSystemApiBase(context: Context, modelInfo: ModelInfo, sdkInt:Int) : SystemApiBase(context, modelInfo, sdkInt){
    override fun initPlatfrom(context: Context) {

    }

    override fun startModemLog(arg1: Int, arg2: Int, obj: Any?) {
        super.startModemLog(arg1, arg2, obj)
    }

    override fun stopModemLog(arg1: Int, arg2: Int, obj: Any?) {

    }

    override fun clearModemLog(arg1: Int, arg2: Int, obj: Any?) {

    }

    override fun uploadLog(obj: Any?) {

    }
}
package com.ucloudlink.refact.model.m2

import android.content.Context
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.model.BaseModel

/**
 * Created by shiqianhua on 2018/3/23.
 */
class ModelM2(context: Context) : BaseModel(context) {
    override fun initModel(): Int {
//        var m2mqtt = M2MqttClient(ServiceManager.appContext)
        return super.initModel()
    }

    override fun exitModel(): Int {
        return super.exitModel()
    }

    override fun getDefaultSeedSlot(): Int {
        return 0
    }

    override fun getDeviceName(): String {
        return "M2"
    }
}
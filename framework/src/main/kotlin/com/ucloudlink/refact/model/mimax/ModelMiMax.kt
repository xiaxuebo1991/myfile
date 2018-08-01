package com.ucloudlink.refact.model.mimax

import android.content.Context
import com.ucloudlink.refact.model.BaseModel

class ModelMiMax(context: Context) : BaseModel(context) {
    override fun initModel(): Int {
        return super.initModel()
    }

    override fun exitModel(): Int {
        return super.exitModel()
    }

    override fun getDefaultSeedSlot(): Int {
        return 0
    }

    override fun getDeviceName(): String {
        return "MIMAX"
    }
}
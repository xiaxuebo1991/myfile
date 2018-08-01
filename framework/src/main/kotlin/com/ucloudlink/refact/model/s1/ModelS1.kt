package com.ucloudlink.refact.model.s1

import android.content.Context
import com.ucloudlink.refact.model.BaseModel

class ModelS1(context: Context) : BaseModel(context) {
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
        return "S1"
    }
}
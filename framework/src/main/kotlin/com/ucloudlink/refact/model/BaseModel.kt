package com.ucloudlink.refact.model

import android.content.Context
import com.ucloudlink.refact.systemapi.interfaces.ModelIf

/**
 * Created by shiqianhua on 2018/3/10.
 */
open class BaseModel(context: Context) :ModelIf {
    override fun initModel(): Int {
        return 0
    }

    override fun exitModel(): Int {
        return 0
    }

    override fun getDefaultSeedSlot(): Int {
        return 0
    }

    override fun getDeviceName(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
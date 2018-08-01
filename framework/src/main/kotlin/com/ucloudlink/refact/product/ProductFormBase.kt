package com.ucloudlink.refact.product

import android.content.Context
import android.os.Message
import com.ucloudlink.refact.product.mifi.seedUpdate.intf.IBusinessTask

/**
 * Created by shiqianhua on 2018/1/13.
 */
abstract class ProductFormBase(context: Context):ProductForm {
    override fun init() {
    }

    override fun pinVerify() {
    }

    override fun getSeedUpdateTask(): IBusinessTask? {
        return null
    }

    override fun isSeedAlwaysOn(): Boolean {
        return false
    }

    override fun dataRecover() {
        
    }

    override fun dealThisEvent(eventId: Int,message : Message): Boolean {
        return true
    }
}
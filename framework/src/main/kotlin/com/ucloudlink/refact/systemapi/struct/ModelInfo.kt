package com.ucloudlink.refact.systemapi.struct

import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum

/**
 * Created by shiqianhua on 2018/1/15.
 */
data class ModelInfo(val modelName:String, val chip:Int, val product:ProductTypeEnum){
    override fun toString(): String {
        return "ModelInfo(modelName=$modelName, chip=$chip, product=$product)"
    }
}
package com.ucloudlink.refact.product.mifi.seedUpdate.bean

import com.ucloudlink.framework.protocol.protobuf.softsimError
import java.util.*

/**
 * 错误信息
 */
data class ErrorInfo(val reason: Int = 0) {
    val errorList = ArrayList<softsimError>()

    fun add(imsi: Long, reason: Int) {
        errorList.add(softsimError(imsi, reason))
    }
}
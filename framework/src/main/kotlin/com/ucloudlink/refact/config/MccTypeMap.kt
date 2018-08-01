package com.ucloudlink.refact.config

import java.util.*

/**
 * Created by jiaming.liang on 2017/12/15.
 *
 */

const val MCC_TYPE_AE = 1
const val MCC_TYPE_CN = 2
const val MCC_TYPE_GB = 3
const val MCC_TYPE_IN = 4
const val MCC_TYPE_JP = 5
const val MCC_TYPE_US = 6


var MccTypeMap: HashMap<String,Int> = HashMap<String,Int>()
    get() {
        if (field.isEmpty()) {
            field.put("424", MCC_TYPE_AE)
            field.put("431", MCC_TYPE_AE)
            field.put("430", MCC_TYPE_AE)
            field.put("460", MCC_TYPE_CN)
            field.put("461", MCC_TYPE_CN)
            field.put("234", MCC_TYPE_GB)
            field.put("235", MCC_TYPE_GB)
            field.put("406", MCC_TYPE_IN)
            field.put("404", MCC_TYPE_IN)
            field.put("405", MCC_TYPE_IN)
            field.put("441", MCC_TYPE_JP)
            field.put("440", MCC_TYPE_JP)
            field.put("316", MCC_TYPE_US)
            field.put("311", MCC_TYPE_US)
            field.put("314", MCC_TYPE_US)
            field.put("310", MCC_TYPE_US)
            field.put("315", MCC_TYPE_US)
            field.put("312", MCC_TYPE_US)
            field.put("313", MCC_TYPE_US)
        }
        return field
    }
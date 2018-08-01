package com.ucloudlink.refact.platform.sprd.struct

import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.APDU


/**
 * Created by shiqianhua on 2018/1/12.
 */
data class SprdApduParam(val lock:Object, var startLock:Boolean, var rsp: ByteArray? = null){
}
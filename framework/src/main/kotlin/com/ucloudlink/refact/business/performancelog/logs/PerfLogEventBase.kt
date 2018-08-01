package com.ucloudlink.refact.business.performancelog.logs

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfLog.GLOCAL_ENBLE_KEY
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.SharedPreferencesUtils

/**
 * Created by shiqianhua on 2018/3/24.
 */
abstract class PerfLogEventBase {
    open val MSG_CREATE_ID = 1

    var mHandler: Handler? = null
    var devicesType: ProductTypeEnum = ProductTypeEnum.PHONE
    var  glocal_enble = 1 //性能日志全局开关: 0-close 1-open

    open fun init(looper: Looper) {
        mHandler = InnerHandler(looper)
        devicesType = ServiceManager.productApi.getProductType()
        glocal_enble = SharedPreferencesUtils.getInt(ServiceManager.appContext,GLOCAL_ENBLE_KEY,1)
    }

    fun create(arg1: Int, arg2: Int, any: Any) {

        if (glocal_enble == 0){
            logd("glocal_enble = 0 !!! DO NOT SAVE!!!")
            return
        }
        if (devicesType.equals("U3C")) {
            logd("[$arg1]-------------U3C DO NOT SAVE!!!-----------") // TODO:?????????U3C 不支持？
            return
        }
        mHandler?.obtainMessage(MSG_CREATE_ID, arg1, arg2, any)?.sendToTarget()
    }

    inner class InnerHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message?) {
            when (msg!!.what) {
                MSG_CREATE_ID -> {

                    createMsg(msg.arg1, msg.arg2, msg.obj)
                }
            }
        }
    }

    abstract fun createMsg(arg1: Int, arg2: Int, any: Any)
}
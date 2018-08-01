package com.ucloudlink.refact.business.flow.netlimit

import android.content.Context

/**
 * Created by jianguo.he on 2018/1/9.
 * miui_v8 系统具有的接口
 */
interface IExtraNetwork {

    fun setMobileRestrict(context: Context, apkName: String, bSet: Boolean): Boolean

    fun isMobileRestrict(context: Context, apkName: String?): Boolean
}
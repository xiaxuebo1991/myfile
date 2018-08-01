package com.ucloudlink.refact.systemapi.interfaces

import android.content.Context

/**
 * Created by shiqianhua on 2018/3/10.
 */
interface ModelIf {
    /**
     * model模块独立业务的初始化
     * @return 0 succ other fail
     */
    fun initModel():Int

    /**
     * model独立模块业务推出
     * @param context
     * @return 0 succ
     */
    fun exitModel():Int

    fun getDefaultSeedSlot():Int

    fun getDeviceName():String
}
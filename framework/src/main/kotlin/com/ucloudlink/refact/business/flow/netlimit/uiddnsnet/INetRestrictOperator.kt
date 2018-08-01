package com.ucloudlink.refact.business.flow.netlimit.uiddnsnet

/**
 * Created by junsheng.zhang on 2018/5/15.
 */
interface INetRestrictOperator {
    fun init()
    fun setRestrict(tag: String)
    fun resetRestrict(tag: String)
}
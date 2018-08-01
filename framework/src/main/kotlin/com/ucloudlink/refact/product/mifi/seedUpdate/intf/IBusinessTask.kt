package com.ucloudlink.refact.product.mifi.seedUpdate.intf

interface IBusinessTask {
    /**
     * 开始任务
     */
    fun serviceStart()

    /**
     * 停止任务
     */
    fun serviceEnd()

    /**
     * 处理服务器s2c命令
     */
    fun notifyS2C(s2c: Any?)
}
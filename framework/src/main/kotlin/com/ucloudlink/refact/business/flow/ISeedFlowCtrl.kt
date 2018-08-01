package com.ucloudlink.refact.business.flow

/**
 * Created by jianguo.he on 2018/2/7.
 */
interface ISeedFlowCtrl {

    fun start(curSeedIfName: String?, username: String?, imsi: String?, mcc: String?, cardType: String?)

    fun stop()

    fun uploadFlow(enfore: Boolean)

    fun checkWhenIfNameChange(ifName: String?)
}
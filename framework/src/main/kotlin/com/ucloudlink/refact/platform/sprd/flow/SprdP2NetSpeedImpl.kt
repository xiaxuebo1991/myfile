package com.ucloudlink.refact.platform.sprd.flow

import com.ucloudlink.refact.business.flow.speedlimit.INetSpeed

class SprdP2NetSpeedImpl : INetSpeed {
    override fun getIfaceThrottleStates(ifName: String?): Int {
        return 0
    }

    override fun getIfaceSpeed(ifName: String?): Array<String?> {
        return arrayOf()
    }

    override fun getCloudInterfaceName(): String? {
        return ""
    }

    override fun setInterfaceThrottle(ifName: String?, rxKbps: Long, txKbps: Long): Int {
        return 0
    }

    override fun resetInterfaceThrottle(ifName: String?, rxKbps: Long, txKbps: Long): Int {
        return 0
    }

    override fun setFlowPermitByPassIpstr(ip: String?): Int {
        return 0
    }

    override fun clearFlowPermitByPassIpstr(ip: String?): Int {
        return 0
    }

    override fun setFlowPermitByPassUid(uid: Int): Int {
        return 0
    }

    override fun clearFlowPermitByPassUid(uid: Int): Int {
        return 0
    }
}
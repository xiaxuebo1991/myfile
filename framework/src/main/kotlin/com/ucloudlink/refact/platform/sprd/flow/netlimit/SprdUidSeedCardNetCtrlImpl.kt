package com.ucloudlink.refact.platform.sprd.flow.netlimit

import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.SeedCardNetRemote
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2018/1/15.
 */
class SprdUidSeedCardNetCtrlImpl : ISeedCardNetCtrl {

    override fun initState(seedSimEnable: IDataEnabler, cloudSimEnabler: IDataEnabler) {

    }

    override fun setRestrictAllNet(tag: String?) {
        JLog.logd("SeedCardNetLog, SprdUidSeedNetLimit setRetrict tag= $tag");
        SeedCardNetRemote.setSeedNetworkLimit();
        FlowBandWidthControl.getInstance().getSeedCardNetManager().setLocalPackagesRestrict();// 设置后uid > 10000的都不能上网
    }

    override fun clearRestrictAllRuleNet(tag: String?) {
        JLog.logd("SeedCardNetLog, SprdUidSeedNetLimit resetRetrict tag= $tag");
        SeedCardNetRemote.clearSeedNetworkLimit();
    }

    override fun configDnsOrIp(info: SeedCardNetInfo) {

    }
}
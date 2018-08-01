package com.ucloudlink.refact.business.flow.netlimit.qualcomm

import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.SeedCardNetRemote
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.utils.JLog
import java.util.*

/**
 * Created by jianguo.he on 2018/1/11.
 * 旧版本，通过uid限制种子卡网络
 */
class QCUidSeedCardNetCtrlImpl : ISeedCardNetCtrl {

    override fun initState(seedSimEnable: IDataEnabler, cloudSimEnabler: IDataEnabler) {

    }

    override fun setRestrictAllNet(tag: String?) {
        JLog.logd("SeedCardNetLog, UidSeedNetLimit setRetrict tag= $tag");
        SeedCardNetRemote.setSeedNetworkLimit();
        FlowBandWidthControl.getInstance().getSeedCardNetManager().setLocalPackagesRestrict();// 设置后uid > 10000的都不能上网
    }

    override fun clearRestrictAllRuleNet(tag: String?) {
        JLog.logd("SeedCardNetLog, UidSeedNetLimit resetRetrict tag= $tag");
        SeedCardNetRemote.clearSeedNetworkLimit();
        if (FlowBandWidthControl.getInstance().seedCardNetManager.bwccRestrict) {
            FlowBandWidthControl.getInstance().seedCardNetManager.restoreLocalPackagesRestrict()
        } else {
            FlowBandWidthControl.getInstance().seedCardNetManager.clearUserRestrict()
        }
    }

    override fun configDnsOrIp(info: SeedCardNetInfo) {
    }

}
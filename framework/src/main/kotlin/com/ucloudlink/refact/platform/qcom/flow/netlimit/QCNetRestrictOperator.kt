package com.ucloudlink.refact.platform.qcom.flow.netlimit

import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.INetRestrictOperator
import com.ucloudlink.refact.utils.JLog

/**
 * Created by junsheng.zhang on 2018/5/15.
 */
class QCNetRestrictOperator : INetRestrictOperator{
    override fun init() {

    }

    override fun setRestrict(tag: String) {
        ServiceManager.appThreadPool.execute {
            JLog.logd("SeedCardNetLog setRetrict tag=$tag")
            SeedCardNetLimitHolder.getInstance().setRetrict(tag)
        }
    }

    override fun resetRestrict(tag: String) {
        ServiceManager.appThreadPool.execute({
            JLog.logd("SeedCardNetLog resetRetrict tag=$tag")
            SeedCardNetLimitHolder.getInstance().resetRetrict(tag)
        })
    }
}
package com.ucloudlink.refact.platform.sprd.flow.netlimit

import android.telephony.SubscriptionManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.INetRestrictOperator
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog

/**
 * Created by junsheng.zhang on 2018/5/15.
 */
class SprdNetRestrictOperator : INetRestrictOperator {

    override fun init() {
        ServiceManager.monitor.ddsObser.subscribe({
            val subManager = SubscriptionManager.from(ServiceManager.appContext)
            val ddsid = subManager.defaultDataPhoneId
            JLog.logd("SprdNetRestrictOperator：ddsid=$ddsid, Configuration.seedSimSlot = " + Configuration.seedSimSlot)

            if (ddsid == Configuration.seedSimSlot) {
                setRestrictInner("dds change to seedCard")
            } else {
                resetRestrictInner("dds change to other")
            }
        })
    }

    private fun setRestrictInner(tag: String) {
        ServiceManager.appThreadPool.execute {
            JLog.logd("SeedCardNetLog setRetrict tag=$tag")
            SeedCardNetLimitHolder.getInstance().setRetrict(tag)
        }
    }

    private fun resetRestrictInner(tag: String) {
        ServiceManager.appThreadPool.execute({
            JLog.logd("SeedCardNetLog resetRetrict tag=$tag")
            SeedCardNetLimitHolder.getInstance().resetRetrict(tag)
        })
    }

    override fun setRestrict(tag: String) {
//        setRestrictInner(tag)
    }

    override fun resetRestrict(tag: String) {
//        resetRestrictInner(tag)
    }
}
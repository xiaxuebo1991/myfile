package com.ucloudlink.refact.platform.sprd.flow.netlimit

import com.ucloudlink.framework.flow.ISeedCardNetCtrl


/**
 * Created by shiqianhua on 2018/1/15.
 */
class SprdSeedCardNetFactory {
    companion object{
        fun getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP: Boolean, isUiSupportSeedNetLimitByUidAndIP: Boolean): ISeedCardNetCtrl {
            return if (isFrameworkSupportSeedNetLimitByUidAndIP && isUiSupportSeedNetLimitByUidAndIP) {
                SprdSeedCardNetCtrlImpl()
            } else {
                SprdUidSeedCardNetCtrlImpl()
            }
        }
    }

}
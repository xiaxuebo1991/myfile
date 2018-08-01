package com.ucloudlink.refact.platform.sprd.flow

import com.ucloudlink.refact.platform.sprd.flow.netlimit.SprdSeedCardNetCtrlImpl
import com.ucloudlink.refact.utils.JLog
import java.util.*

/**
 * Created by junsheng.zhang on 2018/3/2.
 */
class SprdU3CSeedNetCtrlImpl : SprdSeedCardNetCtrlImpl() {

    /**
     * 获取ifaceName 接口列表, List<String>{rmnet_data0, rmnet_data1}
     */
    override fun getPreIfNameList(): ArrayList<String?> {
        val ret = ArrayList<String?>()
        ret.add("seth_lte0")
        ret.add("seth_lte1")
        JLog.logd("getPreIfNameList:[ seth_lte0, seth_lte1 ]")
        return ret
    }
}
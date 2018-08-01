package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_screen_event
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.PerfUntil.getRandomNum
import com.ucloudlink.refact.utils.JLog.loge

/**
 * Created by haiping.liu on 2018/3/31.
 *  亮屏熄屏事件：不需要
 */
object PerfLogScreenEvent : PerfLogEventBase() {
    var last_num: Int = 10000000 //上一次的8位递增数
    var last_id = ""             //上一次的id
    var first_isScreenOn = false

    //arg1 1- open 2 -close
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        //------亮灭屏事件不需要--------
        return

        //只在云卡运行期间上报
        if (PerfLog.getCurrentPersent() == 0 || PerfLog.getCurrentPersent() == 100) {
            loge("PerfLogScreenEvent not running return")
            return
        }

        var isScreenOn = false
        var id = ""
        if (arg1 == 1) {
            isScreenOn = true
        }
        //第一次
        if (last_num == 10000000) {
            if (isScreenOn) {
                first_isScreenOn = true
                last_id = getRandomNum() + (PerfLogDataEvent.last_num++).toString()
            }
        }

        if (isScreenOn != first_isScreenOn) {
            id = last_id
        } else {
            id = getRandomNum() + (last_num++).toString()
        }

        val screen_event = Ter_screen_event.Builder()
                .head(PerfUntil.getCommnoHead())
                .occur_time((System.currentTimeMillis() / 1000).toInt())
                .isScreenOn(isScreenOn)
                .id(id)
                .build()
        last_id = id
        PerfUntil.saveFreqEventToList(screen_event)
    }

}
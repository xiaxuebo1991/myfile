package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_data_event
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.PerfUntil.getRandomNum
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by haiping.liu on 2018/3/31.
 * 数据业务开关事件:只在云卡运行期间上报
 */
object PerfLogDataEvent:PerfLogEventBase(){
    var last_num:Int = 10000000 //上一次的8位递增数
    var last_id =""             //上一次的id
    var first_is_data_statu = false

    //arg1 1 - open 2 -close
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        if(PerfLog.getCurrentPersent() == 0){
            logd("CurrentPersent == 0 ,return")
            return
        }

        var isDataOn = false
        var id = ""
        if (arg1 == 1){
            isDataOn = true
        }
        //第一次
        if (last_num == 10000000){
           if (isDataOn){
               first_is_data_statu = true
               last_id = getRandomNum()+(last_num++).toString()
           }
        }

        if (isDataOn != first_is_data_statu){
            id = last_id
        }else {
            id = getRandomNum()+(last_num++).toString()
        }

        val dataevent = Ter_data_event.Builder()
                .head(PerfUntil.getCommnoHead())
                .occur_time((System.currentTimeMillis()/1000).toInt())
                .isDataOn(isDataOn)
                .id(id)
                .build()
        last_id = id

        PerfUntil.saveFreqEventToList(dataevent)
    }
}
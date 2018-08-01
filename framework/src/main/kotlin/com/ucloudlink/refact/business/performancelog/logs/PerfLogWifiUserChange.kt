package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Wifi_user_change
import com.ucloudlink.refact.business.performancelog.PerfUntil

/**
 * Created by haiping.liu on 2018/3/28.
 * 描述：记录终端wifi连接数的信息 就是excel中的WIFI板事件测量（Wifi_MR）
 * todo s1 无法获取
 */

object PerfLogWifiUserChange:PerfLogEventBase(){
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
       val wifi_user_change = Wifi_user_change.Builder()
               .head(PerfUntil.getCommnoHead())
               .occur_time((System.currentTimeMillis()/1000).toInt())
               .last_user_cnt(-1) //todo  变化前的连接数 未取到赋初始值 -1
               .user_cnt(-1)//todo  当前连接数
//               .ter_info(List<Wifi_usr_info>())//todo 终端信息
               .wifi_channel(-1)//todo  无线信道
               .build()
        PerfUntil.saveFreqEventToList(wifi_user_change)
    }
}
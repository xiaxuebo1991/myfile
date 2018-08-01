package com.ucloudlink.refact.business.performancelog.logs

import android.content.Context
import com.ucloudlink.framework.protocol.protobuf.preflog.Mobile_net_pos
import com.ucloudlink.framework.protocol.protobuf.preflog.Sys_start_type
import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_power_off
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfLog.getCurrentPersent
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.PerfUntil.getBootTimeToNow
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.business.performancelog.data.RebootCheck
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by haiping.liu on 2018/3/29.
 * 描述：记录终端关机消息
 *
 * 关机类型之能识别正常和低电量关机
 */

object PerfLogPownOff : PerfLogEventBase() {
    val ID_SOCKET_CONNECT = 0
    val ID_SOCKET_DISCONNECT = 1
    val ID_POWER_OFF = 2

    var socket_connect_time = -1L
    var socket_disconnect_time = -1L

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogPownOff createMsg id=$arg1")
        when (arg1) {
            ID_SOCKET_CONNECT -> {
                socket_connect_time = System.currentTimeMillis()
                socket_disconnect_time = -1
            }
            ID_SOCKET_DISCONNECT -> {
                socket_disconnect_time = System.currentTimeMillis()
            }
            ID_POWER_OFF -> {
                try {
                    val persent = getCurrentPersent()
                    val dataInfo = ServiceManager.seedCardEnabler.getDataEnableInfo()
                    var net = PerfUntil.getMobileNetInfo(true, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))
                    if (net.net_pos.mcc.equals("000")||net.net_pos.mnc.equals("00")){
                        val cloudmccmnc = OperatorNetworkInfo.mccmncCloudSim
                        logd("PerfLogPownOff cloudmccmnc=$cloudmccmnc")
                        val mcc = if(cloudmccmnc.length >= 5) cloudmccmnc.substring(0,3) else "000"
                        val mnc = if(cloudmccmnc.length >= 5) cloudmccmnc.substring(3,5) else "00"
                        val netPos = Mobile_net_pos.Builder().mcc(mcc).mnc(mnc).build()
                        net = net.newBuilder().net_pos(netPos).build()
                    }
                    var duration = -1
                    if(persent == 100){
                        socket_disconnect_time = System.currentTimeMillis()
                    }
                    if (socket_connect_time != -1L && socket_disconnect_time != -1L) {
                        duration = (socket_disconnect_time - socket_connect_time).toInt()
                    }
                    logd("PerfLogPownOff persent=$persent ,socket_connect_time =$socket_connect_time , socket_disconnect_time=$socket_disconnect_time")

                    var powerOffType = Sys_start_type.SYS_START_NORMAL
                    if (RebootCheck.saveLowBattery){
                        powerOffType = Sys_start_type.SYS_START_LOW_POWER
                    }

                    val ter_power_off = Ter_power_off.Builder()
                            .head(PerfUntil.getCommnoHead())
                            .occur_time((System.currentTimeMillis() / 1000).toInt())
                            .online_duration(duration/1000)
                            .poweroff_type(powerOffType)
                            .power_left(PerfUntil.getBatteryLevel(ServiceManager.appContext))
                            .powerup_duration(getBootTimeToNow())
                            .net(net)
                            .build()
                    logd("PerfLogPownOff Save to file:$ter_power_off")
                    //关机时无法保存到数据库，先保存到文件
                    val fileOutputStream = ServiceManager.appContext.openFileOutput("ter_power_off", Context.MODE_PRIVATE)
                    ter_power_off.encode(fileOutputStream)
                    fileOutputStream?.close()
                    logd("PerfLogPownOff Save Over!!!")
                    // PerfUntil.saveEventToList(ter_power_off,Perf_log_id_e.PERF_LOG_TER_POWER_OFF)
                }catch (e:Exception){
                    logd("Exception:$e")
                }

            }
        }
    }
}
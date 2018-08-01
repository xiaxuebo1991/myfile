package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_access_abnormalA
import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_access_abnormalB
import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_access_normal
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfLog.ACESS_AB_TIMEOUT_KEY
import com.ucloudlink.refact.business.performancelog.PerfLogTimeTask
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.business.statebar.NoticeStatusBarServiceStatus.isWifiConnected
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.SharedPreferencesUtils

/**
 * Created by haiping.liu on 2018/3/26.
 * 终端接入异常事件A（Terminal_EstAbnormalA）
 * 终端接入异常事件B（Terminal_EstAbnormalB）
 * 终端接入正常事件（Terminal_EstNormal）
 */

object PerfLogTerAccess : PerfLogEventBase() {

     val perfLogTimeTask: PerfLogTimeTask = PerfLogTimeTask(ServiceManager.appContext) //终端接入事件定时器

    val ID_CLOUD_START = 1    //云卡流程开始
    val ID_FLOW_UPLOAD = 2   //流量上报
    val ID_TIME_END = 3      //定时器时间到
    val ID_CLOUD_STOP = 4    //云卡流程停止
    val ID_LAST_ERR = 6;    // 最近一次错误信息
    val ID_LAC_CELL_CHANGE = 7;  // 副板所在网络LAC CELL变更次数 未取到赋初始值 -1 int32
    val ID_LOGIN = 9;        // 是否发起登录

    val EVENT_A = 11
    val EVENT_B = 12
    val EVENT_NORMAL = 13

    private var old_lac = -1
    private var old_cellid = -1
    private var lac_change_count = 0
    private var cellid_change_count = 0
    private var is_login = false

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogTerAccess id=$arg1 any=$any")
        when (arg1) {
            ID_CLOUD_START -> {
                val time = SharedPreferencesUtils.getInt(ServiceManager.appContext,ACESS_AB_TIMEOUT_KEY,6)
                perfLogTimeTask.start(time * 60)//默认6分钟
            }
            ID_FLOW_UPLOAD -> {
                if (perfLogTimeTask.isRunning) {
                    perfLogTimeTask.stop()
                    //触发:正常事件: 在规定时间内上报了流量
                    endEvent(EVENT_NORMAL, "")
                }
            }
            ID_TIME_END -> {
                //触发:异常事件A: 时间到未上报流量
                endEvent(EVENT_A, "")
            }
            ID_CLOUD_STOP -> {
                if (perfLogTimeTask.isRunning) {
                    perfLogTimeTask.stop()
                    //触发:异常事件B ： 时间没有到，用户停止，并且没有上报流量
                    endEvent(EVENT_B, "")
                }
            }
            ID_LAST_ERR -> {
            }
            ID_LAC_CELL_CHANGE -> {
                if (perfLogTimeTask.isRunning) {
                    any as TerAccessData
                    if (any.lac != old_lac) {
                        lac_change_count++
                        old_lac = any.lac
                    }
                    if (any.cellied != old_cellid) {
                        cellid_change_count++
                        old_cellid = any.cellied
                    }
                }
            }
            ID_LOGIN -> {
                if (perfLogTimeTask.isRunning) {
                    is_login = true
                }
            }
        }
    }

    fun endEvent(arg1: Int, any: Any) {
        logd("PerfLogTerAccess endEvent id=$arg1")
        when (arg1) {
            EVENT_A -> {
                val dataInfo = ServiceManager.seedCardEnabler.getDataEnableInfo()
                val net = PerfUntil.getMobileNetInfo(true, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))

                val ter_access_abnormalA = Ter_access_abnormalA.Builder()
                        .head(PerfUntil.getCommnoHead())
                        .occur_time((System.currentTimeMillis() / 1000).toInt())
                        .wifi_on(isWifiConnected(ServiceManager.appContext))
                        .wifi_client_count(-1) //todo
                        .net(net)
//                        .last_err() //todo need 最近一次错误信息
                        .ssim_lac_change_times(lac_change_count)
                        .ssim_cell_change_times(cellid_change_count)
                        .is_login(is_login)
                        .build()
                PerfUntil.saveEventToList(ter_access_abnormalA)
                clean()

            }
            EVENT_B -> {
                val dataInfo = ServiceManager.seedCardEnabler.getDataEnableInfo()
                val net = PerfUntil.getMobileNetInfo(true, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))
                val ter_access_abnormalB = Ter_access_abnormalB.Builder()
                        .head(PerfUntil.getCommnoHead())
                        .occur_time((System.currentTimeMillis() / 1000).toInt())
                        .wifi_on(isWifiConnected(ServiceManager.appContext))
                        .wifi_client_count(-1) //todo
                        .net(net)
//                        .last_err() //todo need 最近一次错误信息
                        .ssim_lac_change_times(lac_change_count)
                        .ssim_cell_change_times(cellid_change_count)
                        .is_login(is_login)
                        .build()
                PerfUntil.saveEventToList(ter_access_abnormalB)
                clean()
            }
            EVENT_NORMAL -> {
                val dataInfo = ServiceManager.cloudSimEnabler.getDataEnableInfo()
                val net = PerfUntil.getMobileNetInfo(false, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))
                val ter_access_normal = Ter_access_normal.Builder()
                        .head(PerfUntil.getCommnoHead())
                        .occur_time((System.currentTimeMillis() / 1000).toInt())
//                        .wifi_client_count(-1) //todo
                        .net(net)
                        .build()
                PerfUntil.saveEventToList(ter_access_normal)
                clean()
            }
        }
    }

    fun clean() {
        old_cellid = -1
        old_lac = -1
        lac_change_count = 0
        cellid_change_count = 0
    }
}

data class TerAccessData(val lac: Int, val cellied: Int, val errorCode:Int, val errorType: Int)
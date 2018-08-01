package com.ucloudlink.refact.business.s2ccmd

import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.frequentauth.FrequentAuth
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfLog.ACESS_AB_TIMEOUT_KEY
import com.ucloudlink.refact.business.performancelog.PerfLog.GLOCAL_ENBLE_KEY
import com.ucloudlink.refact.business.performancelog.PerfLog.PERI_MR_INTVL_KEY
import com.ucloudlink.refact.business.routetable.RouteTableManager.ifRouteTableNeetUpdate
import com.ucloudlink.refact.business.routetable.RouteTableManager.saveRouteTable
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.business.s2ccmd.logcmd.createLogCommandInvoker
import com.ucloudlink.refact.business.smartcloud.SmartCloudController
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils

/**
 * Created by yongbin.qin on 2016/12/27.
 */

object ServerCmdHandle {

    private val packetUtil = ProtoPacketUtil.getInstance()

    /**
     *处理服务器主动下发的命令（注意：此方法在netty的线程中调用，会阻塞netty上报消息，耗时的操作新开线程处理）
     */
    fun handlerServerCmd(packet: ProtoPacket) {
        JLog.logd("handlerServerCmd packet :  ${packet}")

        val s2c = packetUtil.decodeServerCmd(packet)
        JLog.logd("handlerServerCmd s2c :  ${s2c}")
        if (s2c == null) {
            return
        }

        if (s2c is S2c_upload_log_file) {  //LOG文件操作
            Requestor.s2cCmdResp(0, packet)
            if (s2c.control != null) {
                createLogCommandInvoker(s2c)?.Invoke()
            }
        } else if (s2c is S2c_set_vsim_visit_mode) {  // 设置访问vsim模式
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_reset_host_and_relogin) {  // 重启主板并重新登陆
            Requestor.s2cCmdResp(0, packet)
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_RELOGIN)
        } else if (s2c is S2c_rtt_work_mode_set) {  //RTT工作模式设置
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_limit_up_down_speed) {  // 限速
            JLog.logi("NetSpeedCtrlLog receive NetSpeed cmd -> s2c.ctrl = " + s2c.ctrl)
            Requestor.s2cCmdResp(0, packet)
            if (s2c.ctrl == Limit_speed_ctrl.LIMIT_SPEED_CTRL_GET_QOS) {
                FlowBandWidthControl.getInstance().queryBwProcess(s2c, packet)
            } else if (s2c.ctrl == Limit_speed_ctrl.LIMIT_SPEED_CTRL_SET_QOS) {
                FlowBandWidthControl.getInstance().setBwProcess(s2c, packet)
                SmartCloudController.getInstance().setSpeedLimited()
            } else if (s2c.ctrl == Limit_speed_ctrl.LIMIT_SPEED_CTRL_CLEAR_QOS) {
                FlowBandWidthControl.getInstance().clearBwProcess(s2c, packet)
                SmartCloudController.getInstance().clearSpeedLimited()
            }
        } else if (s2c is S2c_waring_flow) {   // 流量预警
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_speed_detection) {   // 启动3G速率检测
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_search_network) {   // 请求终端搜集网络信息
            Requestor.s2cCmdResp(1, packet)
        }  else if (s2c is S2c_rtu_control) {  //开启/关闭RTU
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_redirect_route) {  // 路由重定向
            Requestor.s2cCmdResp(0, packet)
            logd("tRoute ----------------S2c_redirect_route----------------routeTable=${s2c}")
            if (ifRouteTableNeetUpdate(s2c, ServerRouter.current_mode)) {
                saveRouteTable(s2c, ServerRouter.current_mode)
                ServerRouter.initIpByCurrentMode()
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_RELOGIN, TERMINAL_DO_QUIT, 5, TERMINAL_NOTICE_QUIT)
            } else {
                loge("tRoute ----------------S2c_redirect_route----------------:routeTable no need update")
            }
        } else if (s2c is S2c_PushSpeedDetectionUrl) {  //推送测速网址到终端
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_UpdatePlmnListRequest) {  //开启/关闭优选3G请求
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_System_call) {  // 下发系统命令（ADB）
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_perf_log_cfg) {  // 性能日志配置
            logd("s2c-perflog  = ${s2c}")
            Requestor.s2cCmdResp(1, packet)
            if (s2c.glocal_enble != SharedPreferencesUtils.getInt(ServiceManager.appContext, GLOCAL_ENBLE_KEY, 1)) {
                SharedPreferencesUtils.putInt(ServiceManager.appContext, GLOCAL_ENBLE_KEY, s2c.glocal_enble)
                PerfLog.glocalEnableChange(s2c.glocal_enble)
            }
            if (s2c.peri_mr_intvl > 0 && s2c.peri_mr_intvl != SharedPreferencesUtils.getInt(ServiceManager.appContext, PERI_MR_INTVL_KEY)) {
                SharedPreferencesUtils.putInt(ServiceManager.appContext, PERI_MR_INTVL_KEY, s2c.host_fail_timeout)
            }
            if (s2c.acess_ab_timeout > 0 && s2c.acess_ab_timeout != SharedPreferencesUtils.getInt(ServiceManager.appContext, ACESS_AB_TIMEOUT_KEY)) {
                SharedPreferencesUtils.putInt(ServiceManager.appContext, ACESS_AB_TIMEOUT_KEY, s2c.acess_ab_timeout)
            }

        } else if (s2c is S2c_Local_opt_switch) {  //副板优化功能开关控制
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_RTU_Phone_Call) {  //请求终端打电话
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_Send_SMS) {  //请求终端发短信
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_send_remote_at) {  //远程AT
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_auto_switch_vsim) { //自动换卡设置
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_lac_change_report_interval_type) {  // lac上报时间间隔
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_softsim_recycle) {  // 软卡回收
            Requestor.s2cCmdResp(1, packet)
        } else if (s2c is S2c_quit_force) {
            Requestor.s2cCmdResp(0, packet)
            when (s2c.force_type) {
                1 -> {  // 1 帐户在其它地方登录，终端要执行退出登陆操作
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DO_QUIT, 0, TERMINAL_NOTICE_QUIT)
                }
                2 -> { //2 帐户在其它地方登录，终端无需执行退出登录操作，服务器端立即释放相关资源
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_RELOGIN)
                }
                3 -> {  //3 余额不足T下线，终端无需执行退出登录操作
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_NO_FEE)
                }
                4 -> {  //4 用户换绑T下线，终端无需执行退出登录操作
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_USER_BIND_CHANGE)
                }
                5 -> {  //5 限速失败T下线，终端无需执行退出登录操作
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_SPEED_LIMIT_FAIL)
                }
                6 -> { //6 手动下发T下线，终端无需执行退出登录操作
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_MANUAL)
                }
                7 -> { //7 imei被停用，终端需执行退出登录操作
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_DEVICE_STOP)
                }
                8 -> {
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_ACCOUNT_STOP)
                }
                0 -> { // 云卡长时间没有用户流量，服务器卡资源紧缺，被踢掉了 #34901
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_RESOURCE_SHORT)
                }
                else -> {
                    JLog.logd("force_type : " + s2c.force_type)
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DONOT_QUIT, 0, TERMINAL_FORCE_QUIT_ACCOUNT_STOP)
                }
            }
        } else if (s2c is S2c_switch_vsim) {
            logd("switch vsim request reason ${s2c.reason}  ${s2c.subReason}")
            Requestor.s2cCmdResp(0, packet)
            if (s2c.reason == Ter_switch_vsim_reason.TER_SWITCH_VSIM_SERVER_REQ) {
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_SWAPCLOUDSIM, SWITCH_VSIM_CMD_FROM_WWS, 0, s2c.subReason)
            } else {
                loge("reason fail ${s2c.reason}")
            }
        } else if (s2c is s2c_ext_softsim_req) {//下载软卡，服务器s2c 27
            Requestor.s2cCmdResp(0, packet);
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_DOWMLOAD_EXT_SOFTSIM_REQ, s2c)
        } else if (s2c is s2c_ext_softsim_update_req) {//更新软卡，服务器s2c 28
            Requestor.s2cCmdResp(0, packet);
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_UPDATE_EXT_SOFTSIM_REQ, s2c)
        } else if (s2c is S2c_gps_func_ctrl) {
            Requestor.s2cCmdResp(0, packet)
            if (s2c.ctrl_type == Gps_ctrl_type.SET_GPS_SWITCH) {
                val ret = ServiceManager.productApi.setGpsConfig((s2c.hard_gps_switch == Gps_switch_state.SWITCH_ON), (s2c.network_gps_switch == Gps_switch_state.SWITCH_ON))
                if (ret == 0) {
                    Configuration.HARD_GPS_CFG = (s2c.hard_gps_switch == Gps_switch_state.SWITCH_ON)
                    Configuration.NETWORK_GPS_CFG = (s2c.network_gps_switch == Gps_switch_state.SWITCH_ON)
                }
            } else if (s2c.ctrl_type == Gps_ctrl_type.QUERY_GPS_STATUS) {
                logd("QUERY_GPS_STATUS: ${Configuration.HARD_GPS_CFG} ${Configuration.NETWORK_GPS_CFG}")
                Requestor.uploadGspCfg(Configuration.HARD_GPS_CFG, Configuration.NETWORK_GPS_CFG, 35).subscribe({
                    logd("upload gps succ!!!")
                })
            }
        } else if (s2c is S2c_frequent_auth_detection_param) {
            logd("FrequentAuth  s2c rev detection_param = $s2c")
            //保存频繁鉴权配置表
            Requestor.s2cCmdResp(0, packet)
            FrequentAuth.revFrequentAuthParam(s2c)
        } else if (s2c is S2c_frequent_auth_action) {
            logd("FrequentAuth  s2c rev auth_action = $s2c")
            //保存频繁鉴权操作（ping）
            Requestor.s2cCmdResp(0, packet)
            FrequentAuth.revFrequentAuthAction(s2c)
        }else if (s2c is S2c_UpdatePlmnListRequest){//云卡智能优选之3G优选开关
            Requestor.s2cCmdResp(0,packet)
            loge("handlerServerCmd 3g rat priority enable:${s2c.name}")
            SmartCloudController.getInstance().enableRatPriority(s2c)
        }else if (s2c is S2c_weak_signal_ctrl){//云卡智能优选之弱信号检测开关
            Requestor.s2cCmdResp(0,packet)
            loge("handlerServerCmd S2c_weak_signal_ctrl enable:${s2c.ctrl}")
            SmartCloudController.getInstance().enableWeakSignalTest(s2c)
        }else if (s2c is S2c_speed_detection){//云卡云卡智能优选之测速开关
            Requestor.s2cCmdResp(0,packet)
            loge("handlerServerCmd S2c_speed_detection enable:${s2c.detect_ctrl}")
            SmartCloudController.getInstance().enableNetSpeedTest(s2c)
        }else if (s2c is S2cSwitchCardResult){//云卡智能优选服务器下发是否有卡可换
            Requestor.s2cCmdResp(0,packet)
            loge("handlerServerCmd S2cSwitchCardResult ret:${s2c.reason}")
            SmartCloudController.getInstance().sendSwitchCardResult(s2c)
        } else {
            Requestor.s2cCmdResp(0, packet)
        }

    }

}
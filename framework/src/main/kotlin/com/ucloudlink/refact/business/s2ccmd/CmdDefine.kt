package com.ucloudlink.refact.business.s2ccmd

import com.ucloudlink.refact.config.Configuration

/**
 * Created by chunjiao.li on 2016/9/9.
 */
//Method定义
const val S2CMethod: String = "s2c_cmd"
const val UpdatePlmnListMethod: String = "update_plmn_list_request"

//s2c_cmd 指令名称
const val S2C_LOG_TO_SERVERS_72: Int = 72
const val S2C_LIMIT_UP_DOWN_SPEED_74: Int = 74
const val S2C_QUIT_FORCE_130: Int = 130
const val S2C_SWITCH_VSIM_131: Int = 131
const val S2C_QXDM_LOG_132: Int = 132

//s2c_cmd ,整型参数
const val UPLOAD_G2_UCLOG: Int = 11
const val UPLOAD_LOG_LIMIT: Int = 800 * 1024 * 1024

const val UP_DOWN_QOS: Int = 0
const val UP8K_DOWN8K: Int = 1
const val UP32K_DOWN32K: Int = 2
const val UP64K_DOWN64K: Int = 3
const val UP128K_DOWN128K: Int = 4
const val UP256K_DOWN256K: Int = 5
const val UP384K_DOWN384K: Int = 6
const val UPNO_DOWN64K: Int = 7
const val UPNO_DOWN128K: Int = 8
const val UPNO_DOWN256K: Int = 9
const val UP384K_DOWN512K: Int = 10
const val UP512K_DOWN1024K: Int = 11
const val UP1024K_DOWN2048K: Int = 12
const val UP2048K_DOWN3840K: Int = 13
const val UP3840K_DOWN7372K: Int = 14
const val UPNO_DOWNNO: Int = 15 //关闭限速开关

const val TERMINAL_DO_QUIT: Int = 1 //帐户在其它地方登陆，终端要执行退出登陆操作
const val TERMINAL_DONOT_QUIT: Int = 2 //帐户在其它地方登陆，终端无需执行退出登陆操作，服务器端立即释放相关资源


const val TERMINAL_NOTICE_QUIT: Int = 1
const val TERMINAL_FORCE_QUIT_RELOGIN : Int = 2
const val TERMINAL_FORCE_QUIT_NO_FEE: Int = 3
const val TERMINAL_FORCE_QUIT_USER_BIND_CHANGE : Int = 4
const val TERMINAL_FORCE_QUIT_SPEED_LIMIT_FAIL : Int = 5
const val TERMINAL_FORCE_QUIT_MANUAL :Int = 6
const val TERMINAL_FORCE_QUIT_DEVICE_STOP :Int = 7 //imei被停用
const val TERMINAL_FORCE_QUIT_ACCOUNT_STOP :Int = 8
const val TERMINAL_FORCE_QUIT_RESOURCE_SHORT:Int = 0 //云卡长时间没有用户流量，服务器卡资源紧缺，被踢掉了
const val UNKNOW : Int = 10

const val SERVER_ASK_SWITCH_CLOUDSIM: Int = 0

//响应整型参数
const val S2C_ACCEPT: Int = 0   //接受并将尝试执行指令
const val S2C_REFUSE_NOT_SUPPORT: Int = 1   //拒绝执行指令，原因是目标终端不支持该指令
const val S2C_REFUSE_LACK_OF_CONDITION: Int = 2  //因为条件不满足而拒绝执行指令或者指令执行失败

const val DATE_FORMAT_DOWNNO: Int = 19 //关闭限速开关

data class UpLogArgs(
        var StartTime: String = "1970-01-01 00:00:00",
        var EndTime: String = "3000-01-01 00:00:00",
        var Tag: String = "",
        var Level: String = "",
        var Model: String = "",
        var FtpDN: String = Configuration.FTP_DN,
        var FtpUserName: String = "gdcl",
        var FtpUserPwd: String = "Gdcl@Ucloud=2014",
        var SaveLogPath: String = "/dsdslog"
)

data class UpQxLogArgs(
        var StartTime: String = "1970-01-01 00:00:00",
        var EndTime: String = "3000-01-01 00:00:00",
        var FtpDN: String = Configuration.FTP_DN,
        var FtpUserName: String = "gdcl",
        var FtpUserPwd: String = "Gdcl@Ucloud=2014",
        var SaveLogPath: String = "/dsdsqxlog"
)
//换卡原因
/**
值	换卡原因
0	服务器要求换卡
1	pdp连续被拒绝5次
2	ATTACH被拒绝
3	注册不到网络
4	主板建立socket不成功，重拨5次均失败
5	注册到其它网络，没有数据业务
6	非法虚拟imei号
7	Vsim bin文件太长
8	Vsimbin文件下载失败
9	Vsimbin文件数据错误
10	服务器BAM错误，APDU返回FF
 */
const val SWITCH_VSIM_MIN: Int = -1
const val SWITCH_VSIM_CMD_FROM_WWS: Int = 0
const val SWITCH_VSIM_PDP_REJECTED_5TIMES: Int = 1
const val SWITCH_VSIM_ATTACH_REJECTED: Int = 2
const val SWITCH_VSIM_REG_NETWORK_FAILED: Int = 3
const val SWITCH_VSIM_HOST_CREAT_SOCKET_FAILED: Int = 4
const val SWITCH_VSIM_REG_OTHER_NET_AND_NO_PS: Int = 5
const val SWITCH_VSIM_ILLEGALITY_VIRTUAL_IMEI: Int = 6
const val SWITCH_VSIM_VSIM_BIN_PACK_TOO_LONG: Int = 7
const val SWITCH_VSIM_VSIM_BIN_PACK_DOWNLOAD_ERROR: Int = 8
const val SWITCH_VSIM_VSIM_BIN_PACK_DATA_ERROR: Int = 9
const val SWITCH_VSIM_BAM_APDU_ERR: Int = 10


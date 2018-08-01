package com.ucloudlink.refact.business.performancelog.logs

import android.os.SystemClock
import com.ucloudlink.framework.protocol.protobuf.preflog.AuthApduInfo
import com.ucloudlink.framework.protocol.protobuf.preflog.Vsim_DelayStat
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.utils.JLog.logd
import java.util.*

/**
 * Created by haiping.liu on 2018/3/24.
 */
object PerfLogVsimDelayStat : PerfLogEventBase() {
    val START = 1               //终端从上电开机 0%
    val SSIM_REG_START = 2     //副板搜网开始  10%
    val SSIM_REG_END_SOCKET_START = 3 //副板注册网络成功  副板与服务器之间SOCKET连接开始 20%
    val SSIM_SOCKET_END = 4     //副板与服务器之间SOCKET连接建立成功 35%
    val VSIM_DOWNLOAD_VSIM = 5  //主板取到VSIM 65%
    val VSIM_POWER_ON = 6       //主板搜网开始
    val VSIM_REG_START = 7      // 发起网络注册 75%
    val VSIM_INSERVICE = 8      //主板注册上网络
    val VSIN_DATA_CALL = 9      //主板发起拨号
    val EVENT_CLOUD_SOCKETOK = 10  //终端主板SOCKET连接建立成功
    val UPLOAD_FIRST_FLOWPACK = 11 // 终端上报第一个流量包  停止
    val END = 12                      //云卡停止

    val LOGINRTT_START = 21       //针对一次接入过程中每个登陆包进行统计 35%
    val LOGINRTT_END = 22
    val AUTHAPDU_START = 31        //针对一次接入过程中每个鉴权APDU包进行统计
    val AUTHAPDU_END = 32

    var mSTART = -1L
    var mSSIM_REG_START = -1L
    var mSSIM_REG_END_SOCKET_START = -1L
    var mSSIM_SOCKET_END = -1L
    var mVSIM_DOWNLOAD_VSIM = -1L
    var mVSIM_REG_START = -1L
    var mVSIM_POWER_ON = -1L
    var mVSIM_INSERVICE = -1L
    var mVSIN_DATA_CALL = -1L
    var mEVENT_CLOUD_SOCKETOK = -1L
    var mUPLOAD_FIRST_FLOWPACK = -1L
    var mEND = -1L

    var mLOGINRTT_START = -1L
    var mLOGINRTT_END = -1L
    var mAUTHAPDU_START = -1L
    var mAUTHAPDU_END = -1L

    var ifStart = false //开始标记

    var loginRtt: ArrayList<Int> = ArrayList<Int>()
    var authApdu: ArrayList<AuthApduInfo> = ArrayList<AuthApduInfo>()
    var sendApduSN = -1

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogVsimDelayStat createMsg ID = $arg1")
        when (arg1) {
            START -> {
                ifStart = true
                if (!ifStart) {
                    return
                }
                if (mSTART == -1L) {
                    mSTART = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            SSIM_REG_START -> {
                if (!ifStart) {
                    return
                }
                if (mSSIM_REG_START == -1L) {
                    mSSIM_REG_START = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            SSIM_REG_END_SOCKET_START -> {
                if (!ifStart) {
                    return
                }
                if (mSSIM_REG_END_SOCKET_START == -1L) {
                    mSSIM_REG_END_SOCKET_START = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            SSIM_SOCKET_END -> {
                if (!ifStart) {
                    return
                }
                if (mSSIM_SOCKET_END == -1L) {
                    mSSIM_SOCKET_END = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            VSIM_DOWNLOAD_VSIM -> {
                if (!ifStart) {
                    return
                }
                if (mVSIM_DOWNLOAD_VSIM == -1L) {
                    mVSIM_DOWNLOAD_VSIM = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            VSIM_REG_START -> {
                if (!ifStart) {
                    return
                }
                if (mVSIM_REG_START == -1L) {
                    mVSIM_REG_START = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            VSIM_POWER_ON -> {
                if (!ifStart) {
                    return
                }
                if (mVSIM_POWER_ON == -1L) {
                    mVSIM_POWER_ON = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            VSIM_INSERVICE -> {
                if (!ifStart) {
                    return
                }
                if (mVSIM_INSERVICE == -1L) {
                    mVSIM_INSERVICE = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            VSIN_DATA_CALL -> {
                if (!ifStart) {
                    return
                }
                if (mVSIN_DATA_CALL == -1L) {
                    mVSIN_DATA_CALL = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            EVENT_CLOUD_SOCKETOK -> {
                if (!ifStart) {
                    return
                }
                if (mEVENT_CLOUD_SOCKETOK == -1L) {
                    mEVENT_CLOUD_SOCKETOK = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            UPLOAD_FIRST_FLOWPACK -> {
                if (!ifStart) {
                    return
                }
                if (mUPLOAD_FIRST_FLOWPACK == -1L) {
                    mUPLOAD_FIRST_FLOWPACK = SystemClock.elapsedRealtimeNanos() / 1000000
                }
                ifStart = false
                endEvent(0, 0, "")
            }
            END -> {
                if (!ifStart) {
                    return
                }
                if (mEND == -1L) {
                    mEND = SystemClock.elapsedRealtimeNanos() / 1000000
                }
                ifStart = false
                if (mEVENT_CLOUD_SOCKETOK != -1L) {
                    //云卡启动成功过才统计
                    endEvent(0, 0, "")
                } else {
                    clear()
                }
            }

            LOGINRTT_START -> {
                if (!ifStart) {
                    return
                }
                if (mLOGINRTT_START == -1L) {
                    mLOGINRTT_START = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            LOGINRTT_END -> {
                if (!ifStart) {
                    return
                }
                if (mLOGINRTT_END == -1L) {
                    mLOGINRTT_END = SystemClock.elapsedRealtimeNanos() / 1000000

                    if (mLOGINRTT_START != -1L && loginRtt.size < 4) {
                        loginRtt.add((mLOGINRTT_END - mLOGINRTT_START).toInt())

                    }
                    mLOGINRTT_END = -1L
                    mLOGINRTT_START = -1L

                }
            }
            AUTHAPDU_START -> {
                if (!ifStart) {
                    return
                }
                if (mAUTHAPDU_START == -1L) {
                    sendApduSN = arg2
                    mAUTHAPDU_START = SystemClock.elapsedRealtimeNanos() / 1000000
                }
            }
            AUTHAPDU_END -> {
                if (!ifStart) {
                    return
                }
                if (mAUTHAPDU_END == -1L) {
                    mAUTHAPDU_END = SystemClock.elapsedRealtimeNanos() / 1000000
                }

                if (mAUTHAPDU_START != -1L && authApdu.size < 4) {
                    val apduauth = AuthApduInfo.Builder()
                            .authApduSN(sendApduSN)
                            .authApduRtt((mAUTHAPDU_END - mAUTHAPDU_START).toInt())
                            .build()
                    authApdu.add(apduauth)

                }
                sendApduSN = -1
                mLOGINRTT_END = -1L
                mLOGINRTT_START = -1L

            }
        }
    }


    private fun endEvent(arg1: Int, arg2: Int, any: Any) {
        val head = PerfUntil.getCommnoHead()

        //副板从搜网开始到注册网络成功的时长 10-20
        var ssimRegDelay = -1
        if (mSSIM_REG_END_SOCKET_START != -1L && mSSIM_REG_START != -1L) {
            ssimRegDelay = (mSSIM_REG_END_SOCKET_START - mSSIM_REG_START).toInt()
        }

        //副板与服务器之间SOCKET连接建立的时长 20-35
        var ssimSocketDelay = -1
        if (mSSIM_SOCKET_END != -1L && mSSIM_REG_END_SOCKET_START != -1L) {
            ssimSocketDelay = (mSSIM_SOCKET_END - mSSIM_REG_END_SOCKET_START).toInt()
        }

        //主板从搜网开始到注册网络成功的时长  POWER_ON -INSERVICE
        var vsimRegDelay = -1
        if (mVSIM_INSERVICE != -1L && mVSIM_POWER_ON != -1L) {
            vsimRegDelay = (mVSIM_INSERVICE - mVSIM_POWER_ON).toInt()
        }

        //主板与服务器之间SOCKET连接建立的时长 75-100
        var vsimSocketDelay = -1
        if (mEVENT_CLOUD_SOCKETOK != -1L && mVSIM_REG_START != -1L) {
            vsimSocketDelay = (mEVENT_CLOUD_SOCKETOK - mVSIM_REG_START).toInt()
        }

        //主板取到VSIM到发起网络注册的时长 65 - 75
        var vsimStartRegDelay = -1
        if (mVSIM_REG_START != -1L && mVSIM_DOWNLOAD_VSIM != -1L) {
            vsimStartRegDelay = (mVSIM_REG_START - mVSIM_DOWNLOAD_VSIM).toInt()
        }

        //主板注册上网络到发起拨号的时长
        var vsimStartDialDelay = -1
        if (mVSIN_DATA_CALL != -1L && mVSIM_INSERVICE != -1L) {
            vsimStartDialDelay = (mVSIN_DATA_CALL - mVSIM_INSERVICE).toInt()
        }

        //终端从上电开机到主板SOCKET连接建立成功的总时长
        var totalConnDelay = -1
        if (mEVENT_CLOUD_SOCKETOK != -1L && mSTART != -1L) {
            totalConnDelay = (mEVENT_CLOUD_SOCKETOK - mSTART).toInt()
        }

        //终端从上电开机到上报第一个流量包的总时长
        var firstFlowDataDelay = -1
        if (mUPLOAD_FIRST_FLOWPACK != -1L && mSTART != -1L) {
            firstFlowDataDelay = (mUPLOAD_FIRST_FLOWPACK - mSTART).toInt()
        }

        val vsim_DelayStat = Vsim_DelayStat.Builder()
                .head(head)
                .ssimRegDelay(ssimRegDelay)
                .ssimSocketDelay(ssimSocketDelay)
                .vsimRegDelay(vsimRegDelay)
                .vsimSocketDelay(vsimSocketDelay)
                .vsimStartRegDelay(vsimStartRegDelay)
                .vsimStartDialDelay(vsimStartDialDelay)
                .loginRtt(loginRtt)
                .authApdu(authApdu)
                .totalConnDelay(totalConnDelay)
                .firstFlowDataDelay(firstFlowDataDelay)
                .build()
        PerfUntil.saveEventToList(vsim_DelayStat)
        clear()
    }

    fun clear() {
        mSTART = -1L
        mSSIM_REG_START = -1L
        mSSIM_REG_END_SOCKET_START = -1L
        mSSIM_SOCKET_END = -1L
        mVSIM_DOWNLOAD_VSIM = -1L
        mVSIM_REG_START = -1L
        mVSIM_POWER_ON = -1L
        mVSIM_INSERVICE = -1L
        mVSIN_DATA_CALL = -1L
        mEVENT_CLOUD_SOCKETOK = -1L
        mUPLOAD_FIRST_FLOWPACK = -1L
        mEND = -1L
        mLOGINRTT_START = -1L
        mLOGINRTT_END = -1L
        mAUTHAPDU_START = -1L
        mAUTHAPDU_END = -1L
        loginRtt.clear()
        authApdu.clear()
    }
}

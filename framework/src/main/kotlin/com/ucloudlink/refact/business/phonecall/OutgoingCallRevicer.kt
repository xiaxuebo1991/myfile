package com.ucloudlink.refact.business.phonecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.MAX_HOLD_TIME
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.MAX_WAIT_PHY_CARD_CS_ON_TIME
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.MAX_WAIT_PHY_CARD_ON_TIME
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.PHY_CARD_STATE_CS_ON
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.PHY_CARD_STATE_INSERTED
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.PHY_CARD_STATE_OFF
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.SOFTSIM_STATE_CLOSING
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.SOFTSIM_STATE_OFF
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.SOFTSIM_STATE_ON
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.isProcessOutCall
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv

/**
 * Created by jiaming.liang on 2018/3/1.
 *
 * 监听呼出电话，根据状态处理hold住广播
 * 详细流程图参考svn上的“打电话时关软卡流程”方案
 *
 */
class OutgoingCallReceiver(val no: String) : BroadcastReceiver() {


    private var startTime: Long = 0

    private var nowTime: Long = 0
        get() = SystemClock.elapsedRealtime()

    override fun onReceive(context: Context, intent: Intent) {

        //判断是否可以处理的系统
        val isTargetSystem = intent.getBooleanExtra("isGlocalMe", false)
        val isEmergencyCall = intent.getBooleanExtra("isEmergencyCall", false)
        /**
         * zhanlin and zhangyixuan 已测试
         */
        val isMmiCall = intent.getBooleanExtra("isMMi", false)
        logd("isEmergencyCall $isEmergencyCall, isMmiCall:$isMmiCall")
        if (isTargetSystem && !isEmergencyCall && !isMmiCall) {
            //判断是否为本通电话的第一个接收者
            val isFirstReceiver = intent.getBooleanExtra("isFirstReceiver", true)
            if (isFirstReceiver) {
                intent.putExtra("isFirstReceiver", false)
                isProcessOutCall = true
            }

            startTime = nowTime

            waitPhyOk()
        }
    }


    private fun waitPhyOk() {

        while (nowTime - startTime < MAX_HOLD_TIME) {
            var runNext = true
            logv("($no)waitPhyOk softSimState:${SoftSimStateMark.softSimState} phySimState:${SoftSimStateMark.phySimState}")
            when (SoftSimStateMark.softSimState) {
                SOFTSIM_STATE_ON -> {
//                    SoftSimStateMark.closeSoftSim()
                }
                SOFTSIM_STATE_OFF -> {
                    when (SoftSimStateMark.phySimState) {
                        PHY_CARD_STATE_INSERTED -> {
                            val phySimLastInsertTime = SoftSimStateMark.phySimLastInsertTime
                            if (nowTime - phySimLastInsertTime > MAX_WAIT_PHY_CARD_CS_ON_TIME) {
                                logv("wait to long to cs on")
                                runNext = false
                            }
                        }
                        PHY_CARD_STATE_OFF -> {
                            val softSimLastClosedTime = SoftSimStateMark.SoftSimLastClosedTime
                            if (nowTime - softSimLastClosedTime > MAX_WAIT_PHY_CARD_ON_TIME) {
                                logv("wait to long to phy card on")
                                runNext = false
                            }
                        }
                        PHY_CARD_STATE_CS_ON -> {
                            runNext = false
                        }
                    }
                }
                SOFTSIM_STATE_CLOSING -> {
                    /*do nothing */
                }
            }

            if (!runNext || !isProcessOutCall) {
                if (!isProcessOutCall) {
                    logv("isProcessOutCall ==  false")
                }
                abortBroadcast()
                logv("($no)waitPhyOk abortBroadcast")
                break
            }

            //todo 注意把这个广播放在子线程执行！不然会阻塞主线程
            Thread.sleep(1000)
        }

    }
}


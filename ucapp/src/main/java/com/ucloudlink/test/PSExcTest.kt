package com.ucloudlink.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.telecom.Log
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.ucapp.InitActivity

/**
 * Created by jiaming.liang on 2017/1/13.
 * PS 不恢复 复现测试
 */
class PSExcTest(val UI: InitActivity, looper: Looper) : Handler(looper) {
    val TAG = "PSExcTest"
    val EVENT_DEFAULT_NET_OK = 1
    val EVENT_DUN_NET_OK = 2
    val EVENT_DID_ONDEMAND_CALL = 3
    val EVENT_DID_UNDO_ONDEMAND_CALL = 4

    

    val STATE_RUNNING = 2   //正在跑
    val STATE_CLOSING = 3 //等待恢复
    var mState = STATE_RUNNING

    override fun handleMessage(msg: Message) {
        val event = msg.what
        when (mState) {
            STATE_RUNNING -> {
                //收到点击了ondemand 后2秒点击reset 并进入 closing 状态
                if (event == EVENT_DID_ONDEMAND_CALL) {
                    mState = STATE_CLOSING
                    postDelayed({
                        //点击reset
                        UI.onReset(null) 
                        postDelayed(runExc, 15 * 60 * 1000) //15分钟

                    }, 5000)
                }
            }
            STATE_CLOSING -> {
                //点击reset 后 收到default网络可用,3s后点击rutoRun 并进入 runing状态
                if (event == EVENT_DEFAULT_NET_OK) {
                    mState = STATE_RUNNING
                    removeCallbacks(runExc)
                    postDelayed({
                        //点击autoRun
                        UI.onInit(null)
                    }, 2000)
                }
            }
        }
    }


    private val runExc: Runnable = Runnable {
        Log.d(TAG, "too long to have default net")
    }

    init {
        //注册监听
        val filter = IntentFilter()
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction("com.ucloudlink.ucapp.dun")
        UI.registerReceiver(br(), filter)
    }

    inner class br : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            JLog.logd("onReceive action=" + intent.action)
            val action = intent.action
            when (action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val ni = intent.getParcelableExtra<NetworkInfo>(ConnectivityManager.EXTRA_NETWORK_INFO)
                    val state = ni.getState() // isConnected();
                    val type = ni.type
                    Log.d(TAG, " state:$state, type: $type")
                    if (state == NetworkInfo.State.CONNECTED && type == 0) {
                        sendEmptyMessage(EVENT_DEFAULT_NET_OK)
                    }
                }
                "com.ucloudlink.ucapp.dun" -> {
                    val msg = intent.getStringExtra("action")
                    if (msg == "doCall") {
                        sendEmptyMessage(EVENT_DID_ONDEMAND_CALL)
                    }
                }

            }
        }

    }
}
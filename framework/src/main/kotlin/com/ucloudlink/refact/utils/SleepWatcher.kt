package com.ucloudlink.refact.utils

import android.os.SystemClock
import com.ucloudlink.refact.utils.JLog.logd
import java.util.*

/**
 * Created by jiaming.liang on 2017/3/13.
 */
object SleepWatcher {

    private var OldSleepTime = 0L

    private val PERIOD_TIME = 2000L

    init {
        val wakeTime = SystemClock.uptimeMillis()
        val realtime = SystemClock.elapsedRealtime()
        OldSleepTime = realtime - wakeTime
        val timer = Timer("SleepWatcher")
        timer.schedule(CheckSleep(), PERIOD_TIME, PERIOD_TIME)
    }

    fun start(): Unit {
        //do nothing
    }

    class CheckSleep : UcTimerTask() {
        override fun run() {
            val wakeTime = SystemClock.uptimeMillis()
            val realTime = SystemClock.elapsedRealtime()
            val sleepTime = realTime - wakeTime
//            logd("new sleepTime $sleepTime  old sleepTime $OldSleepTime")
            val diff = sleepTime - OldSleepTime
            if (diff > 50) {
                //表示系统进入过休眠
                logd("system is sleep pass $diff ms(${timeFormat(diff)})")
                OldSleepTime = sleepTime
            }
        }

        private fun timeFormat(time: Long): String {
            val sec = 1000
            val min = 60 * sec
            val hour = 60 * min
            val stringBuilder = StringBuilder()

            var tempTime = time
            if (tempTime >= hour) {
                val count = tempTime / hour
                stringBuilder.append("$count hour ")
                tempTime -= count * hour
            }
            if (tempTime >= min) {
                val count = tempTime / min
                stringBuilder.append("$count min ")
                tempTime -= count * min
            }
            if (tempTime >= sec) {
                val count = tempTime / sec
                stringBuilder.append("$count sec ")
                tempTime -= count * sec
            }
            if (tempTime > 0) {
                stringBuilder.append("$tempTime ms ")
            }
            return stringBuilder.toString()
        }
    }

}
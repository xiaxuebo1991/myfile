package com.ucloudlink.refact.channel.transceiver


import com.ucloudlink.refact.utils.JLog.logd
import java.util.*

/**
 * Created by jiaming.liang on 2016/11/8.
 * 统计达到最大次数,就回调
 */
class StateKeeper {
    private val lock = Any()
    private val lock1 = Any()
    private val lostTime = 35 * 1000L //回复包超时时间
    private val maxLostRev = 2//最大掉包回调数
    private var lostCount = 0;

    private var timer = Timer()

    private fun addLostCount() {
        synchronized(lock) {
            lostCount++
            if (lostCount >= maxLostRev) {
                lostCount = 0
                doCallBack()
            }
        }
    }

    private var callback: () -> Unit = {}
    fun doWhenLost(callback: () -> Unit): Unit {
        this.callback = callback
    }

    private fun doCallBack() {
        callback.invoke()
    }

    private val taskList = ArrayList<TimerTask>()
    fun write() {
        synchronized(lock1) {
            val task = Task()
            taskList.add(task)
            timer.schedule(task, lostTime)
        }
    }

    fun receive() {
        synchronized(lock1) {
            lostCount = 0
            for (i in 0..taskList.size - 1) {
                taskList.removeAt(0).cancel()
            }
        }

    }

    private inner class Task : TimerTask() {
        override fun run() {
            logd("Task", "do Task")
            addLostCount()
        }
    }
}

package com.ucloudlink.framework.tasks

import android.os.SystemClock
import com.ucloudlink.refact.utils.JLog.logd
import java.util.*

/**
 * Created by jiaming.liang on 2016/8/11.
 * 周期任务抽象类
 * 不会重复启动
 *
 */
abstract class PeriodTask {
    val timer = Timer()
    private var task: Task? = null

    var isRunning = false
    var isPause = false
    /**
     * 开始任务
     */
    fun start() {
        if (isRunning) {
            return logd("task is running");
        }
        logd(this.javaClass.simpleName + " start");
        isRunning = true
        onStart()
        logd("start PeriodTask")
        startTaskWith(getDelayTime(), getPeriodTime())
    }

    open fun onStart() {
    }

    open fun onStop() {
    }

    fun startTaskWith(delay: Long) {
        startTaskWith(delay, getPeriodTime())
    }

    /**
     * 马上重新执行任务
     * 会重置下次任务时间
     */
    fun startTaskWith(Delay: Long, Period: Long) {
        logd("reRunTask")
        task?.cancel()
        task = Task()
        timer.schedule(task, Delay, Period)
    }

    /**
     * 暂停任务
     */
    fun pauseTask(): Unit {
        if (!isPause && task != null) {
            isPause = true
            logd(this.javaClass.simpleName + " pauseTask");
            val elapsedRealtime = SystemClock.elapsedRealtime()
            val periodTime = getPeriodTime()
            val remainTime1 = periodTime - (elapsedRealtime - lastTaskStartTime)
            if (remainTime1 > 0) {
                remainTime = remainTime1
            } else {
                remainTime = 0
            }
            logd("elapsedRealtime:$elapsedRealtime  periodTime $periodTime  remainTime $remainTime");
            task?.cancel()
            task = null
        }
    }

    /**
     * 继续倒计时,按照之前剩余时间延迟执行任务
     */
    fun resumeTask(): Unit {
        if ((isPause) && (isRunning)) {
            isPause = false
            logd(this.javaClass.simpleName + " resumeTask");
            logd("resumeTask remainTime:$remainTime");
            startTaskWith(remainTime)
        }
    }

    abstract fun getDelayTime(): Long
    abstract fun getPeriodTime(): Long
    abstract fun taskRun()

    /**
     * 停止任务
     */
    fun stop() {
        if (isRunning) {
            isRunning = false
            isPause = false
            onStop()
            logd(this.javaClass.simpleName + " stop");
            task?.cancel()
            task = null
        }
    }

    var lastTaskStartTime: Long = 0//上次任务开始的时间
    var remainTime: Long = 0//距离下次任务开始的时间

    inner class Task : TimerTask() {
        override fun run() {
            logd("do Task")
            lastTaskStartTime = SystemClock.elapsedRealtime()
            taskRun()
        }
    }
} 
package com.ucloudlink.refact.utils

import android.os.SystemClock
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Record APP/UService boot time for optimization
 * 过滤的filter为：“BootTimer”
 * Created by zhe.li on 2018/3/29.
 */
object BootOptimize {
    // 用于过滤启动日志的时间
    private val BOOT_TIME_FILTER = "BootTimer"
    // 启动基准时间，用于分析从初始化到执行过程所耗时
    private var BASE_TIME: Long = 0
    // 存储func耗时的map
    private var map = mutableMapOf<String, Long>()
    // 是否激活打印
    private var mIsInActive: Boolean = false
    // 是否已经初始化过，确保只初始化第一次
    private var mIsInit: Boolean = false

    /**
     * 初始化时间过滤，重置基准时间（仅第一次执行有效）
     */
    @JvmStatic
    fun init(tag: String) {
        if (mIsInActive) return
        val current = SystemClock.uptimeMillis()
        if (!mIsInit) {
            mIsInit = true
            BASE_TIME = current
            logd(BOOT_TIME_FILTER, "Boot device until $tag init cost: $current ms.")
        } else {
            logd(BOOT_TIME_FILTER, "Init failed by $tag at ${current - BASE_TIME} ms, BaseTime is ${BASE_TIME}.")
        }
    }

    /**
     * 开始执行方法，打印从基准时间开始的时间戳
     *
     * @param func 方法名称，用于标识
     */
    @JvmStatic
    fun startFun(func: String) {
        if (mIsInActive) return
        val current = SystemClock.uptimeMillis()
        map.put(func, current)
        logd(BOOT_TIME_FILTER, "$func start at: ${current - BASE_TIME} ms.")
    }

    /**
     * 结束执行的方法，打印该方法的执行时间及到目前的耗时
     *
     * @param func 方法名称，用于标识，必须和startFun调用的一致
     */
    @JvmStatic
    fun finishFun(func: String) {
        if (mIsInActive) return
        val current = SystemClock.uptimeMillis()
        val funcStartTime = map[func] ?: 0L
        var log = "$func finish at: ${current - BASE_TIME} ms"
        log += if (funcStartTime != 0L) {
            ", this func cost ${current - funcStartTime} ms."
        } else {
            ""
        }
        map.remove(func)
        logd(BOOT_TIME_FILTER, log)
    }

    /**
     * 打印时间点
     *
     * @param tag TAG参数
     */
    @JvmStatic
    fun tagPoint(tag: String) {
        if (mIsInActive) return
        val current = SystemClock.uptimeMillis()
        logd(BOOT_TIME_FILTER, "$tag at: ${current - BASE_TIME} ms.")
    }
}
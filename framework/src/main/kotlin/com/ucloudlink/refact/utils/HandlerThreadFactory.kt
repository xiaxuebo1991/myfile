package com.ucloudlink.refact.utils

import android.os.HandlerThread
import android.os.Looper

/**
 * 一个获取HandlerThread的工厂类
 * 为了节省资源，某些功能的HandleThread的looper实现共享
 *
 *
 */
class HandlerThreadFactory {

    /**
     * 获取一个独占的Looper
     */
    fun getMonopolizeLooper(threadName: String): Looper {
        val handlerThread = HandlerThread(threadName)
        handlerThread.start()
        return handlerThread.looper
    }

    var shareHandlerThread: HandlerThread? = null

    /**
     * 获取一个共享的looper
     * 注意，不要退出这个looper
     */
    fun getShareLooper(): Looper {
        var thread = shareHandlerThread
        if (thread == null) {
            thread = HandlerThread("shareThread")
            thread.start()
            shareHandlerThread = thread
        }
        return thread.looper
    }

}

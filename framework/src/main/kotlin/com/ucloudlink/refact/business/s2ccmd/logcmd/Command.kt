package com.ucloudlink.refact.business.s2ccmd.logcmd

/**
 * Created by hang.deng on 2017/9/6.
 */
abstract class Command {
    fun Invoke() {
        Thread({
            executer()
        }).start()
    }

    abstract protected fun executer()
}
package com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.watcher

import android.os.Looper
import com.android.internal.util.State
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.simcard.watcher.CloudSimWatcher

class SprdCloudSimWatcher(looper: Looper, dataEnabler: IDataEnabler, name: String) : CloudSimWatcher(looper, dataEnabler, name) {
    //展讯方案，云卡尝试注册状态时，不检查DDS
    override fun shouldcheckDDS(currentState: State): Boolean {
        return currentState != outOfService
    }
}
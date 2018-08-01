package com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.watcher

import android.os.Looper
import com.android.internal.util.State
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.simcard.watcher.TimeOutWatchBase

class QCSeedSimWatcher(looper: Looper, dataEnabler: IDataEnabler, name: String) : TimeOutWatchBase(looper, dataEnabler, name) {
    private var isOnDemandPsCall = false
    
    override fun shouldcheckDDS(currentState: State): Boolean {
        return !isOnDemandPsCall
    }
    fun dunCallTrigger(isDo: Boolean) {
        //表示dun拨号变化了
        logd("dunCallTrigger $isDo")
        isOnDemandPsCall = isDo
        sendMessage(EVENT_DUN_STATE_CHANGE)
    }
}
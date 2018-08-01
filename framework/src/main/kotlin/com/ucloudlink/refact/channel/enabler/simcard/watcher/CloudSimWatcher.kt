package com.ucloudlink.refact.channel.enabler.simcard.watcher

import android.os.Looper
import com.android.internal.util.State
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthState

/**
 * Created by jiaming.liang on 2017/7/11.
 */
open class CloudSimWatcher(looper: Looper, dataEnabler: IDataEnabler, name: String) : TimeOutWatchBase(looper, dataEnabler, name) {
    private val APDU_STATE_CHANGE = EVENT_EXCEPTION or 15
    private val DATA_ENABLE_STATE_CHANGE = EVENT_EXCEPTION or 16
    private var authListenKey: String? = null
    private var isAuthing = false
    var dataEnabled: Boolean = false
        set(value) {
            field = value
            sendMessage(DATA_ENABLE_STATE_CHANGE)
        }

    override fun startWatch(isMultiCountryCard: Boolean) {
        authListenKey = ServiceManager.cardController.addAuthListen { apduStatus ->
            isAuthing = apduStatus == AuthState.AUTH_BEGIN
            sendMessage(APDU_STATE_CHANGE)
        }

        super.startWatch(isMultiCountryCard)
    }

    override fun checkStateValid(currentState: State): Boolean {
        val checkStateValid = super.checkStateValid(currentState) && dataEnabled
        if (!dataEnabled) {
            logd("[checkStateValid] Cloudsim dataEnabled == false")
            return checkStateValid
        }
        

        if (currentState.name == "outOfServiceState" || currentState.name == "inServiceState") {
            val isNotAuthing = !isAuthing
            if (!isNotAuthing) {
                logd("[checkStateValid] Cloudsim is doing Auth")
            }
            return checkStateValid && isNotAuthing //如果不是在鉴权，就要开始计时，避免产生一直不鉴权的超时异常
        }
        return checkStateValid
    }

    override fun stopWatch() {
        isAuthing = false
        val listenKey = authListenKey
        if (listenKey != null) {
            ServiceManager.cardController.removeAuthListen(listenKey)
        }
        super.stopWatch()
    }

}
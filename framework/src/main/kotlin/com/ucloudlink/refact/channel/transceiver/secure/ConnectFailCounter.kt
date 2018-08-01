package com.ucloudlink.refact.channel.transceiver.secure

import android.net.NetworkInfo
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.plmnselect.SEED_SOCKET_FAIL_MAX_EXCEPTION
import com.ucloudlink.refact.channel.enabler.plmnselect.SeedPlmnSelector
import com.ucloudlink.refact.utils.JLog.logd
import java.util.concurrent.atomic.AtomicInteger

/**
 * 
 * 种子卡网络可用时，连续3次失败的话，触发切网（更新事件：SEED_SOCKET_FAIL_MAX_EXCEPTION）
 * 
 * 计数器需要清除的时机
 * 1，socket connect上（不管云卡还是种子卡，因为网络切换了）
 * 2，种子卡网络变成不可用了
 * 3，最大次数达到了
 * 
 */
class ConnectFailCounter {
    private val MAX_COUNT = 3
    private val atomicInteger = AtomicInteger(0)

    fun connectFail() {
        logd("connectFail")
        //目前只有种子卡网络可用时计数
        if (ServiceManager.seedCardEnabler.getNetState() == NetworkInfo.State.CONNECTED) {
            val ret = atomicInteger.incrementAndGet()
            logd("connectFail ret=$ret ,MAX_COUNT=$MAX_COUNT")
            if (ret == MAX_COUNT) {
                clear()
                doWhenFailMax()
            }
        }
    }

    private fun doWhenFailMax() {
        doSwitchSeedNet()
    }

    private fun doSwitchSeedNet() {

        val subId = ServiceManager.seedCardEnabler.getCard().subId
        if (subId != -1) {
            SeedPlmnSelector.updateEvent(SEED_SOCKET_FAIL_MAX_EXCEPTION, subId)
        }
    }

    fun clear() {
        atomicInteger.set(0)
    }
}
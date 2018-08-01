package com.ucloudlink.refact.channel.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.JLog.logv
import rx.lang.kotlin.BehaviorSubject

/**
 * Created by jiaming.liang on 2017/1/6.
 */
class WifiReceiver2 : BroadcastReceiver() {
    companion object {
        val wifiNetObser = BehaviorSubject(NetworkInfo.State.DISCONNECTED)
    }

//    init {
//        val connManager = ServiceManager.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
//        wifiNetObser.onNext(mWifi.state)
//    }

    /*
     * @see #EXTRA_NETWORK_INFO
     * @see #EXTRA_BSSID
     * @see #EXTRA_WIFI_INFO
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NETWORK_STATE_CHANGED_ACTION) {
            return loge("listen wrong")
        }
        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)

        logv("wifi networkInfo:$networkInfo")
        logv("wifiInfo:${intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO)}")
        logv("bssid:${intent.getStringExtra(WifiManager.EXTRA_BSSID)}")
        var state = NetworkInfo.State.DISCONNECTED

        if (networkInfo != null) {
            state = networkInfo.state
        }
        val lastValue = wifiNetObser.value
        if (lastValue == null || lastValue != state) {
            logv("wifi new network info state$state")
            wifiNetObser.onNext(state)
        }
    }
}
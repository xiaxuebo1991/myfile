package com.ucloudlink.refact.business.wifitrigger

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Handler
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.monitors.WifiReceiver2
import com.ucloudlink.refact.ServiceManager


/**
 * Created by jiaming.liang on 2017/4/20.
 *
 * 监听wifi状态，判断是否触发requestNetwork 使云卡驻网
 */
object WifiNetTrigger {
    private val hanlder = Handler()
    private val TAG = "WifiNetTrigger"
    private var mNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val mVSimEnabler = ServiceManager.cloudSimEnabler
    
    private val wifiReciver = WifiReceiver2.wifiNetObser.asObservable().subscribe {
        when (it) {
            NetworkInfo.State.CONNECTED -> {
                requestNetwork()
            }
            NetworkInfo.State.DISCONNECTED -> {
                clearRequest()
            }
        }

    }
    private val mVSimStateHandler = mVSimEnabler.cardStatusObser().asObservable().subscribe {
        when (it) {
            CardStatus.ABSENT -> {
                clearRequest()
            }
            CardStatus.READY, CardStatus.LOAD -> {
                if (WifiReceiver2.wifiNetObser.value  == NetworkInfo.State.CONNECTED) {
                    requestNetwork()
                } 
            }
        }
    }

    private fun clearRequest() {
        if (mNetworkCallback != null) {
            val connectivityManager = ConnectivityManager.from(ServiceManager.appContext)
            connectivityManager.unregisterNetworkCallback(mNetworkCallback)
            mNetworkCallback = null
            hanlder.removeCallbacks(releaseTask)
        }
    }

    private val releaseTask = Runnable {
        if (mVSimEnabler.getCardState() != CardStatus.IN_SERVICE) {
            requestNetwork()
        }
    }

    private val REQUEST_TIME_OUT: Long = 60 * 1000

    private fun requestNetwork() {
        if (mNetworkCallback == null && mVSimEnabler.isCardOn()) {
            
            val connectivityManager = ConnectivityManager.from(ServiceManager.appContext)
            val builder = NetworkRequest.Builder()
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            val mBuild = builder.build()

            mNetworkCallback = object : ConnectivityManager.NetworkCallback() {}
            connectivityManager.requestNetwork(mBuild, mNetworkCallback)

            hanlder.postDelayed(releaseTask, REQUEST_TIME_OUT)
        }
    }

    fun run(): Unit {

    }
}
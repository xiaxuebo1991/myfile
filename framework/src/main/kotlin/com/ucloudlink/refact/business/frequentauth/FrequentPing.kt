package com.ucloudlink.refact.business.frequentauth

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.ucloudlink.framework.protocol.protobuf.S2c_frequent_auth_action
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Created by haiping.liu on 2018/5/3.
 * ping操作
 */
class FrequentPing {
    val PING_SWITCH = true //ping功能开关
    private val FREQUENT_AUTH_ACTION = "frequent_auth_action"
    private var frequentAuthPingTask: FrequentAuthPingTask = FrequentAuthPingTask(ServiceManager.appContext)
    private var frequentAuthScreenTask: FrequentAuthScreenTask = FrequentAuthScreenTask(ServiceManager.appContext)

    /**
     * 开始执行ping操作
     * 只4G下ping
     */
    fun startPingTask() {
        val rat = OperatorNetworkInfo.ratCloudSim
        if (rat != RAT_TYPE_LTE) {
            loge("startPingTask error rat =$rat")
            return
        }
        if (isWifiConnected(ServiceManager.appContext)){
            loge("startPingTask wifi open, don't ping!!")
            return
        }
            if (ifNeedPing()) {
                val param = getFrequentAuthAction()
                if (param != null) {
                    logd("startPingTask")
                    frequentAuthPingTask.start(param.ping_param.interval, param.ping_param.address)
                } else {
                    loge("startPingTask error: paarm = null!")
                }
            } else {
                loge("startPingTask error: ifNeedPing() = ${ifNeedPing()}!")
            }
    }

    /**
     * 停止执行ping操作
     */
    fun stopPingTask() {
        logd("stopPingTask")
        frequentAuthPingTask.stop()
        frequentAuthScreenTask.stop()
    }

    /**
     * 是否需要执行ping操作：存在配置表，开关打开，对目前云卡操作，ping 开关打开
     */
    fun ifNeedPing(): Boolean {
        val pingParam = getFrequentAuthAction()
        if (pingParam == null) {
            logd("ifNeedPing : pingParam == null")
            return false
        }
        if (!pingParam.fa_switch) {
            logd("ifNeedPing : pingParam.fa_switch == false")
            return false
        }
        val cloudsimImsi = ServiceManager.accessEntry.accessState.imis
        if (cloudsimImsi == null || !pingParam.imsi.equals(cloudsimImsi)) {
            loge("ifNeedPing : not need :  param.imsi != cloudsim imsi (${pingParam.imsi},${cloudsimImsi})")
            return false
        }

        if (!PING_SWITCH) {
            loge("ifNeedPing : not need :  PING_SWITCH=$PING_SWITCH")
            return false
        }

        logd("ifNeedPing = true")
        return true
    }

    /**
     * ping 操作 1次 5秒超时
     */
    fun pingOnetime(ipAddress: String) {
        var process: Process? = null
        var buf: BufferedReader? = null
        var line: String = ""
        try {
            logd("ping:$ipAddress")
            process = Runtime.getRuntime().exec("ping -c 1 -W 5 " + ipAddress)
            buf = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                line = buf.readLine() ?: break
                logd("ping:$line")
            }
        } catch (ex: Exception) {
            logd(ex.message)
        } finally {
            logd("ping over!")
            if (process != null) {
                process.destroy()
            }
            if (buf != null) {
                buf.close()
            }
        }
    }

    /**
     *  保存频繁鉴权操作配置,收到后开始或者结束
     */
    fun saveFrequentAuthAction(s2c: S2c_frequent_auth_action) {
        val oldAction = getFrequentAuthAction()
        if (oldAction != null && oldAction.equals(s2c)) {
            loge("saveFrequentAuthAction: oldAction = new Action don't save")
        }
        logd("saveFrequentAuthAction:" + s2c)
        try {
            s2c.encode(ServiceManager.appContext.openFileOutput(FREQUENT_AUTH_ACTION, Context.MODE_PRIVATE))
        } catch (e: Exception) {
            e.printStackTrace()
            //保存出错则删除
            delFrequentAuthAction()
        }
        if (ifNeedPing()) {
            startPingTask()
        } else {
            stopPingTask()
        }
    }

    /**
     *  删除频繁鉴权操作配置
     */
    fun delFrequentAuthAction() {
        logd("delFAAction")
        ServiceManager.appContext.deleteFile(FREQUENT_AUTH_ACTION)
    }

    /**
     * 灭屏 + 热点关闭 1分钟后 停止ping
     * 亮屏 0.5分钟后开始ping
     */
    fun screenChange(statu: Boolean) {
        //关闭定时器
        if (frequentAuthScreenTask.isRunning) {
            frequentAuthScreenTask.stop()
        }
        if (!ifNeedPing()) {
            //不需要ping时不需要启动定时器
            return
        }

        if (statu) {
            //亮屏
            frequentAuthScreenTask.start(30)
        } else {
            //灭屏 + 热点关闭 = 1分钟后 停止ping
            if (!PerfUntil.isOpenHostpot()) {
                frequentAuthScreenTask.start(60)
            }
        }
    }

    /**
     *  获取频繁鉴权操作配置
     */
    private fun getFrequentAuthAction(): S2c_frequent_auth_action? {
        var s2cAction: S2c_frequent_auth_action?
        try {
            s2cAction = S2c_frequent_auth_action.ADAPTER.decode(ServiceManager.appContext.openFileInput(FREQUENT_AUTH_ACTION))
        } catch (e: Exception) {
            s2cAction = null
            e.printStackTrace()
        }
        logd("getFrequentAuthAction:" + s2cAction)
        return s2cAction
    }

    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (wifiNetworkInfo.isConnected()) {
            return true
        }
        return false
    }
}
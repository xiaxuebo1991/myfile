package com.ucloudlink.refact.channel.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.telephony.ServiceState
import android.telephony.ServiceState.STATE_IN_SERVICE
import android.telephony.SubscriptionManager
import android.text.TextUtils
import com.android.internal.telephony.PhoneConstants
import com.android.internal.telephony.TelephonyIntents
import com.google.gson.Gson
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Plmn
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.config.SYSTEM_SP_KEY_LAST_MCC_LIST
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 网络监控
 * 汇集周围网络信息
 */
class NwMonitor(val context: Context) {
    private val PERIOD_OF_NETWORK_VALIDITY: Long = 300000 /*搜网结果有效时间*/
    private val SP_NW_PLMNLIST : String = "SP_NW_PLMNLIST"
    private val plmnsList = LinkedList<Plmn>()
    private val lock = ReentrantReadWriteLock()

    private val serviceStateReceiver = ServiceStateReceiver()

    init {
        val filter = IntentFilter(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)
        context.registerReceiver(serviceStateReceiver, filter)
    }

    fun clean() {
        context.unregisterReceiver(serviceStateReceiver)
    }

    fun refreshNw(plmns: ArrayList<Plmn>? = null) {
        lock.writeLock().lock()
        if (plmns != null) {
            plmnsList.addAll(0, plmns)
        }

        val nowTime = SystemClock.elapsedRealtime()
        while (plmnsList.size > 0 && (nowTime - plmnsList.last.time) > PERIOD_OF_NETWORK_VALIDITY) {
            plmnsList.removeLast()
        }
        savePlmnsListSP()
        lock.writeLock().unlock()
    }

    fun savePlmnsListSP(){
        var plmns = Gson().toJson(plmnsList)
        JLog.logd("NwMonitor save plmnlist to sp plmnsList $plmns")
        SharedPreferencesUtils.putString(ServiceManager.appContext, SP_NW_PLMNLIST, plmns)
    }

    fun hasAvailableNetwork(): Boolean {
        if (plmnsList.size <= 0) {
            JLog.logd("[hasAvailableNetwork] plmnsList.size <= 0 ignore check")
            return true
        }
        lock.readLock().lock()
        plmnsList.forEach {
            if (it.signalQuality < 98) {
                lock.readLock().unlock()
                return true
            }
        }
        lock.readLock().unlock()
        return false
    }

    /**
     * 除了本国，还有其他运营商的强信号
     */
    fun haveAvailableNetworkHere(mcc: String): Boolean {
        if (plmnsList.size <= 0) {
            JLog.logd("[haveAvailableNetworkHere] plmnsList.size <= 0 ignore check")
            return true
        }
        lock.readLock().lock()
        plmnsList.forEach {
            if (it.signalQuality < 98) {
                val nwMcc = it.mccmnc.substring(0, 3)
                if (nwMcc != mcc) {
                    lock.readLock().unlock()
                    return true
                }
            }
        }
        lock.readLock().unlock()
        return false
    }

    /**
     * 判断目标mcc是否再搜网记录中
     */
    fun isMccInNwResult(mcc: String): Boolean {
        if (plmnsList.size <= 0) {
            JLog.logd("plmnsList.size <= 0 ignore check")
            return true
        }
        lock.readLock().lock()
        plmnsList.forEach {
            val nwMcc = it.mccmnc.substring(0, 3)
            if (nwMcc == mcc || (MccTypeMap[nwMcc] != null && MccTypeMap[nwMcc] == MccTypeMap[mcc])) {
                JLog.logd("card mcc($mcc) in plmnsList($nwMcc)")
                lock.readLock().unlock()
                return true
            }
        }
        lock.readLock().unlock()
        return false
    }

    /**
     * 非本国强信号 小于98
     */
    fun checkIsRoam(mcc: String): Boolean {
        if (plmnsList.size <= 0) {
            JLog.logd("[checkIsRoam] plmnsList.size <= 0 ignore check")
            return checkWithLastPlmnsList(mcc)
        }
        lock.readLock().lock()
        JLog.logd("plmnsList $plmnsList")
        plmnsList.forEach {
            if (it.signalQuality < 98) {
                val nwMcc = it.mccmnc.substring(0, 3)
                if (nwMcc != mcc && (MccTypeMap[nwMcc] == null || MccTypeMap[nwMcc] != MccTypeMap[mcc])) {
                    JLog.logd("in roam place $nwMcc -- $mcc")
                    lock.readLock().unlock()
                    return true
                }
            }
        }
        lock.readLock().unlock()
        return false
    }

    fun checkWithLastPlmnsList(mcc: String): Boolean{
        var plmns = SharedPreferencesUtils.getString(ServiceManager.appContext, SP_NW_PLMNLIST, "")
        JLog.logd("SharedPreferencesUtils plmns $plmns")
        val plmnList = try {
            Gson().fromJson(plmns, Array<Plmn>::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return false
        JLog.logd("SharedPreferencesUtils plmnList ${plmnList.size}")
        if (plmnList.isEmpty()) {
            JLog.logd("[checkIsRoam] Last plmnsList.size <= 0 ignore check")
            return false
        }
        lock.readLock().lock()
        JLog.logd("plmnsList $plmnList")
        plmnList.forEach {
            JLog.logd("plmns it $it")
            if (it.signalQuality < 98) {
                val nwMcc = it.mccmnc.substring(0, 3)
                if (nwMcc != mcc && (MccTypeMap[nwMcc] == null || MccTypeMap[nwMcc] != MccTypeMap[mcc])) {
                    JLog.logd("in roam place $nwMcc -- $mcc")
                    lock.readLock().unlock()
                    return true
                }
            }
        }
        lock.readLock().unlock()
        return false
    }

    fun getRoundNetCount(): Int {
        return plmnsList.size
    }

    /**
     * 获取最新的网络的mcc
     * 如果当前网络mcc为空，取文件缓存的值
     */
    fun getLastNetMcc(): HashSet<String> {
        lock.readLock().lock()
        val set = HashSet<String>()
        plmnsList.forEach {
            val mccmnc = it.mccmnc
            if (mccmnc.length >= 3) {
                val mcc = mccmnc.substring(0, 3)
                set.add(mcc)
            }
        }

        if (set.size == 0) {
            val lastMcc = SharedPreferencesUtils.getString(SYSTEM_SP_KEY_LAST_MCC_LIST)
            val list = lastMcc.split(",")
            list.forEach {
                if (!TextUtils.isEmpty(it)) {
                    set.add(it)
                }
            }
        } else {
            val sb = StringBuilder()
            set.forEachIndexed { i, it ->
                sb.append(it)
                if (i != set.size - 1) {
                    sb.append(",")
                }
            }
            SharedPreferencesUtils.putString(SYSTEM_SP_KEY_LAST_MCC_LIST, sb.toString())
        }
        lock.readLock().unlock()
        return set
    }

    private inner class ServiceStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            JLog.logv("ServiceStateReceiver: action=" + intent.action)
            val serviceState = ServiceState.newFromBundle(intent.extras)
            val subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID)

            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && (serviceState.dataRegState == STATE_IN_SERVICE || serviceState.voiceRegState == STATE_IN_SERVICE)) {
                var mccmnc: String?

                mccmnc = serviceState.dataOperatorNumeric

                if (TextUtils.isEmpty(mccmnc)) {
                    mccmnc = serviceState.voiceOperatorNumeric
                }
                if (TextUtils.isEmpty(mccmnc)) {
                    return loge("[onReceive] mccmnc is empty")
                }
                val rat = serviceState.rilDataRadioTechnology

                val signalQuality = 1
                val signalStrength = 90
                val plmn = Plmn(mccmnc, rat, signalQuality, signalStrength, SystemClock.elapsedRealtime())
                refreshNw(arrayListOf(plmn))
                JLog.logd("[onReceive]: subId:$subId  plmn: $plmn")
            } else {
                JLog.logv("[onReceive]: out of service")
            }

        }
    }
}
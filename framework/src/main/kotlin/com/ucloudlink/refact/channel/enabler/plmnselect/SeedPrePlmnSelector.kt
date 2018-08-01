package com.ucloudlink.refact.channel.enabler.plmnselect

import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.utils.printContent
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class SeedPrePlmnSelector {
    private val lastPerPlmnMapLock = ReentrantReadWriteLock()

    /**
     * 检查物理卡首选plmn列表是否有更新
     */
    fun checkPhySeedPerPlmnUpdate(imsi: String, subId: Int): Pair<Boolean, Array<String>?> {
        if (TextUtils.isEmpty(imsi)) {
            JLog.loge("[checkSeedPerPlmnUpdate] imsi is empty")
            return Pair(false, null)
        }

        if (subId == -1) {
            JLog.loge("[checkSeedPerPlmnUpdate] subId == -1")
            return Pair(false, null)
        }


        val lastPerPlmn: Array<String>? = getPhyLastFplmn(imsi)

        val newPerPlmnsList = getCurrentMccPreferredPlmn(imsi, CardType.PHYSICALSIM)

        val isSame = isArraySame(lastPerPlmn, newPerPlmnsList)
        if (!isSame) {
            val regPlmn = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
            val doUpdateFplmn: Boolean
            JLog.logd("[checkSeedFplmnUpdate] regPlmn :$regPlmn")
            if (TextUtils.isEmpty(regPlmn)) {
                //没注册上,直接更新
                doUpdateFplmn = true
            } else {
                //注册上
                doUpdateFplmn = kotlin.run {
                    newPerPlmnsList ?: return@run false
                    newPerPlmnsList.forEach {
                        if (it == regPlmn) {
                            return@run true
                        }
                    }
                    return@run false
                }
            }
            JLog.logd("[checkSeedFplmnUpdate] doUpdateFplmn :$doUpdateFplmn newPerPlmnsList:${newPerPlmnsList?.printContent()}")
            return Pair(doUpdateFplmn, newPerPlmnsList)
        }
        return Pair(false, null)

    }

    private fun isArraySame(array1: Array<String>?, array2: Array<String>?): Boolean {
        val isArray1Empty = array1 == null || array1.isEmpty()
        val isArray2Empty = array2 == null || array2.isEmpty()
        if (isArray1Empty && isArray2Empty) {//都为空
            return true
        }
        val isNotSame = isArray1Empty.xor(isArray2Empty)
        if (isNotSame) {//一个空，一个不空
            return false
        } else {//都不空
            val isSameSize = array1?.size == array2?.size
            if (isSameSize) {
                array1!!
                array2!!
                for (i in array1.indices) {
                    if (array1[i] != array2[i]) {
                        return false
                    }
                }
                return true
            } else {
                return false
            }
        }

    }

    private fun getPhyLastFplmn(imsi: String): Array<String>? {
        lastPerPlmnMapLock.read {
            val phyPlmnStr = SharedPreferencesUtils.getString(ServiceManager.appContext, "PHY_LAST_FPLMN_$imsi")
            if (phyPlmnStr.isEmpty()) {
                return null
            }
            val plmns = phyPlmnStr.split(",")
            val plmnArray = Array(plmns.size, { plmns[it] })
            return plmnArray
        }
    }

    /**
     * 标记上次物理卡使用的首选plmn
     */
    fun markPhyLastPerPlmn(imsi: String, newFplmn: Array<String>?) {
        lastPerPlmnMapLock.write {
            val sb = StringBuilder()

            newFplmn?.forEachIndexed { index, plmn ->
                sb.append(plmn)
                if (index != newFplmn.size - 1) {
                    sb.append(",")
                }
            }

            SharedPreferencesUtils.putString(ServiceManager.appContext, "PHY_LAST_FPLMN_$imsi", sb.toString())
        }
    }

    /**
     * 获取当前mcc的首选plmn
     */
    fun getCurrentMccPreferredPlmn(imsi: String, cardType: CardType): Array<String>? {
        if (TextUtils.isEmpty(imsi)) {
            JLog.loge("[getCurrentMccPreferredPlmn] param error imsi:$imsi")
            return null
        }

        val netMcc = ServiceManager.phyCardWatcher.nwMonitor.getLastNetMcc()

        val configPerPlmn: Array<String>?
        if (netMcc.isNotEmpty()) {
            configPerPlmn = getConfigPerPlmn(imsi, cardType, netMcc)
        } else {
            configPerPlmn = null
        }

        JLog.logv("[getCurrentMccPreferredPlmn] configPerPlmn:${configPerPlmn?.printContent()}")
        return configPerPlmn

    }

    private fun getConfigPerPlmn(imsi: String, cardType: CardType, netMccs: HashSet<String>): Array<String>? {
        val plmnFeeList = ServiceManager.productApi.getSeedSimPlmnFeeListByImsi(imsi, cardType)
        JLog.logv("[getConfigPerPlmn] feePlmnList:${plmnFeeList?.printContent()}")

        val set = TreeSet<PlmnFee>()
        plmnFeeList?.forEach { plmnFee ->
            netMccs.forEach { netMcc ->
                if (plmnFee.plmn.startsWith(netMcc)) {
                    set.add(plmnFee)
                } else {
                    val mcctype = MccTypeMap[netMcc]
                    if (mcctype != null) {
                        val map = MccTypeMap.filterValues { mcctype == it }
                        map.keys.forEach { mcc ->
                            if (plmnFee.plmn.startsWith(mcc)) {
                                set.add(plmnFee)
                            }
                        }
                    }
                }
            }
        }

        val list = ArrayList<String>()
        set.forEach {
            if (!list.contains(it.plmn)) {
                list.add(it.plmn)
            }
        }

        return list.toTypedArray()
    }

}

data class PlmnFee(val plmn: String, val rat: Int, val fee: Float) : Comparable<PlmnFee> {
    override fun compareTo(other: PlmnFee): Int {
        val ret = fee - other.fee
        return if (ret > 0) 1 else -1
    }

}
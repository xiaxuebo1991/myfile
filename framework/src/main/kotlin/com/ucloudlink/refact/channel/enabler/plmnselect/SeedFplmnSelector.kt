package com.ucloudlink.refact.channel.enabler.plmnselect

import android.telephony.TelephonyManager
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.monitors.AttachReject
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.utils.printContent
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 切网
 */
class SeedFplmnSelector {
    private val tempfplmnMapLock = ReentrantReadWriteLock()
    private val lastfplmnMapLock = ReentrantReadWriteLock()
    private val lastPhyfplmnMapLock = ReentrantReadWriteLock()

    //根据imsi指定的fplmn,这是软卡临时禁用的，清除条件：用户退出/大循环时/资费文件存满
    private val imsiFplmnMap = HashMap<String, Array<String>?>()
    //根据slot指定的fplmn,这是物理卡临时禁用的，清除条件：用户退出/大循环时/资费文件存满
    private val phyIMsiFplmnMap = HashMap<String, Array<String>?>()

    //软卡最后使用的fplmn列表
    private val lastSoftSimFplmns = HashMap<String, Array<String>?>()

    //内置软卡写死的一些禁用fplmn ，尽量不需要使用这个 由服务器配置
    private val alwaysSoftFplmnMap = arrayOf("45500", "45505", "51010")
    //内置物理卡写死的一些禁用fplmn ，尽量不需要使用这个 由服务器配置
    private val alwaysPhyFplmnMap: Array<String>? = null


    @Synchronized
    fun updateEvent(event: Int, arg: Any? = null) {
        logv("[updateEvent] event:$event arg:$arg")

        when (event) {
            SEED_SOCKET_FAIL_MAX_EXCEPTION -> {
                arg ?: return loge("arg should not be null")
                val subId = arg as Int

                if (subId == -1) {
                    return loge("subId == -1 return")
                }

                val enabler = ServiceManager.seedCardEnabler
                if (enabler.isCardOn() && !enabler.isClosing()) {
                    val card = enabler.getCard()
                    val cardSubId = card.subId
                    if (subId == cardSubId) {
                        //put fplmn to imsiFplmnMap
                        val registeredPlmn = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
                        val isUpData = markImsiFplmn(subId, card.imsi, card.cardType, registeredPlmn)
                        if (isUpData) {
                            NotifySeedFplmnChanged()
                        }
                    }
                }
            }
            PDP_REJECT_EXCEPTION,ATTACH_REJECT_EXCEPTION -> {
                arg ?: return loge("arg should not be null")
                val attachReject = arg as AttachReject

                if (attachReject.subId == -1) {
                    return loge("subId == -1 return")
                }

                val enabler = ServiceManager.seedCardEnabler
                if (enabler.isCardOn() && !enabler.isClosing()) {
                    val card = enabler.getCard()
                    val cardSubId = card.subId
                    if (attachReject.subId == cardSubId) {
                        //put fplmn to imsiFplmnMap
                        val isUpData = markImsiFplmn(attachReject.subId, card.imsi, card.cardType, attachReject.plmn)
                        logd("isUpData == " + isUpData)
                        if (isUpData) {
                            NotifySeedFplmnChanged()
                        }
                    }
                }
            }
            CLEAN_TEMP_FPLMN -> {
                cleanTempFplmn()
            }
            else -> {
            }
        }
    }

    private fun cleanTempFplmn() {
        lastfplmnMapLock.write {
            lastSoftSimFplmns.clear()
            imsiFplmnMap.clear()
            phyIMsiFplmnMap.clear()
        }
    }

    /**
     * 把当前注册有问题plmn放进列表
     * 获取当前subId 注册的Plmn
     * 先判断是否已经在imsiFplmn里面
     *
     * @return 是否更新
     */
    private fun markImsiFplmn(subId: Int, imsi: String, cardType: CardType, registeredPlmn: String): Boolean {
        if (!TextUtils.isEmpty(registeredPlmn)) {
            return tempfplmnMapLock.write {
                val fplmns = if (cardType == CardType.SOFTSIM) imsiFplmnMap[imsi] else phyIMsiFplmnMap[imsi]
                val newFplmns: Array<String>
                val isUpdate: Boolean
                if (fplmns != null) {
                    val isInFplmns = kotlin.run {
                        fplmns.forEach {
                            if (it == registeredPlmn) {
                                logd("[checkSeedFplmnUpdate] imsiFplmnMap1 " + imsiFplmnMap[imsi]?.printContent())
                                return@run true
                            }
                        }
                        logd("[checkSeedFplmnUpdate] imsiFplmnMap2 " + imsiFplmnMap[imsi]?.printContent())
                        return@run false
                    }
                    if (!isInFplmns) {
                        newFplmns = Array(fplmns.size + 1, {
                            if (it == fplmns.size) {
                                registeredPlmn
                            } else {
                                fplmns[it]
                            }
                        })
                        isUpdate = true
                    } else {
                        newFplmns = fplmns
                        isUpdate = false
                    }
                } else {
                    newFplmns = arrayOf(registeredPlmn)
                    isUpdate = true
                }
                if (cardType == CardType.SOFTSIM) imsiFplmnMap[imsi] = newFplmns else phyIMsiFplmnMap[imsi] = newFplmns
                logd("[checkSeedFplmnUpdate] imsiFplmnMap3 " + imsiFplmnMap[imsi]?.printContent())
                return@write isUpdate
            }
        } else {
            loge("no registered subId:$subId,imsi:$imsi,cardtype:$cardType")
        }
        logd("[checkSeedFplmnUpdate] imsiFplmnMap4 " + imsiFplmnMap[imsi]?.printContent())
        return false

    }

    /**
     * 获取当前mcc的fplmn以及imsi的fplmn
     * 临时+当前mcc服务器配置的+固定的，最多四个
     * 1，从最新缓存取当前mcc，取不到取sp里的
     * 2，物理
     */
    fun getCurrentMccFplmnByImsi(imsi: String, cardType: CardType): Array<String>? {
        logv("[getCurrentMccFplmnByImsi] imsi:$imsi")
        logv("[getCurrentMccFplmnByImsi] cardType:$cardType")
        if (TextUtils.isEmpty(imsi)) {
            loge("[getCurrentMccFplmnByImsi] param error imsi:$imsi")
            return null
        }

        /*
        U3c 临时代码
        val set = HashSet<String>()
        set.add("460")
        val netMcc = set
*/

        val netMcc = ServiceManager.phyCardWatcher.nwMonitor.getLastNetMcc()
        logv("[getCurrentMccFplmnByImsi] netMcc:$netMcc")
        val configFplmn: Array<String>?
        configFplmn = if (netMcc.isNotEmpty()) {
            getConfigFplmn(imsi, cardType, netMcc)
        } else {
            null
        }
        logv("[getCurrentMccFplmnByImsi] configFplmn:${configFplmn?.printContent()}")
        tempfplmnMapLock.read {

            var tempFplmn = if (cardType == CardType.PHYSICALSIM) phyIMsiFplmnMap[imsi] else imsiFplmnMap[imsi]

            logv("[getCurrentMccFplmnByImsi] tempFplmn1:${tempFplmn?.printContent()}")
            //临时加入的fplmn结果，如果包含eplmn的全部，就清空
            tempFplmn = sortByPrePlmn(imsi, cardType, netMcc, tempFplmn)
            logv("[getCurrentMccFplmnByImsi] tempFplmn2:${tempFplmn?.printContent()}")
            val alwaysFplmn = kotlin.run {
                when (cardType) {
                    CardType.PHYSICALSIM -> alwaysPhyFplmnMap
                    CardType.SOFTSIM -> alwaysSoftFplmnMap
                    else -> null
                }
            }
            logv("[getCurrentMccFplmnByImsi] alwaysFplmn:${alwaysFplmn?.printContent()}")
            val list = ArrayList<String>()
            for (array in arrayOf(tempFplmn, configFplmn, alwaysFplmn)) {
                array?.forEach {
                    if (!list.contains(it)) {
                        list.add(it)
                    }
                }
            }
            logv("[getCurrentMccFplmnByImsi] list:${list.toString()}")

            val MAX_SIZE = 4
            val result = Array(if (list.size <= MAX_SIZE) list.size else MAX_SIZE, { list[it] })
            logv("[getCurrentMccFplmnByImsi] result:${result.printContent()}")
            return result
        }
    }

    /**
     * 检查临时的fplmn列表是否全部包含了服务器配置的资费列表的plmn
     */
    private fun sortByPrePlmn(imsi: String, cardType: CardType, currMcc: HashSet<String>, tempFplmn: Array<String>?): Array<String>? {
        if (tempFplmn == null || tempFplmn.isEmpty()) {
            logv("[sortByPrePlmn] tempFplmn isEmpty")
            return tempFplmn
        }

        if (currMcc.size == 0) {
            logv("[sortByPrePlmn] currMcc.size")
            return tempFplmn
        }

        val feeList = ServiceManager.productApi.getSeedSimPlmnFeeListByImsi(imsi, cardType)

        if (feeList == null || feeList.isEmpty()) {
            JLog.logv("[sortByPrePlmn] feeList isEmpty")
            return tempFplmn
        } else {
            val sb = StringBuilder("currentMccFeeList:")
            val currentMccFeeList = feeList.filter { feeplmn ->
                currMcc.forEach { netMcc ->
                    if (feeplmn.plmn.startsWith(netMcc)) {
                        sb.append(feeplmn.plmn).append(",")
                        return@filter true
                    } else {
                        val mccType = MccTypeMap[netMcc]
                        if (mccType != null) {
                            val map = MccTypeMap.filterValues { mccType == it }
                            for (mcc in map.keys) {
                                if (feeplmn.plmn.startsWith(mcc)) {
                                    sb.append(feeplmn.plmn).append(",")
                                    return@filter true
                                }
                            }
                        }
                    }
                }
                return@filter false
            }
            logd("currentMccFeeList:$sb")

            if (currentMccFeeList.isNotEmpty()) {
                val goodPlmn = TreeSet<PlmnFee>()
                currentMccFeeList.forEachIndexed { i, plmnFee ->
                    var shouldAdd = true
                    //判断这个是否就在临时fplmn列表里面，如果在，就不加入goodPlmn中
                    tempFplmn.forEach { fplmn ->
                        if (fplmn == plmnFee.plmn) {
                            logv("[getCurrentMccFplmnByImsi] plmnFee.plmn == " + plmnFee.plmn)
                            shouldAdd = false
                            return@forEach
                        }
                    }

                    if (shouldAdd) {
                        goodPlmn.add(plmnFee)
                    }
                }
                //一轮过滤后，如果goodPlmn为空，表示没可用的plmn，清除临时禁用的plmn
                if (goodPlmn.size == 0) {
                    logv("[sortByPrePlmn] goodPlmn.size == 0")
                    if (cardType == CardType.PHYSICALSIM) {
                        phyIMsiFplmnMap[imsi] = null
                    } else {
                        imsiFplmnMap[imsi] = null
                    }
                    return null
                }
            }else{
                logv("[sortByPrePlmn] currentMccFeeList.is empty")
            }
        }
        return tempFplmn

    }


    private fun getConfigFplmn(imsi: String, cardType: CardType, netMccs: HashSet<String>): Array<String>? {
        val fplmnList = ServiceManager.productApi.getSeedSimFplmnRefByImsi(imsi, cardType)
        logv("[getConfigFplmn] fplmnList:${fplmnList?.printContent()}")

        val set = HashSet<String>()
        //从服务器配置的fplmnlist中根据 当前网络环境的mcc（包括同国家mcc）过滤出一个fplmn集合
        fplmnList?.forEach { fplmn ->
            netMccs.forEach { netMcc ->
                if (fplmn.startsWith(netMcc)) {
                    set.add(fplmn)
                } else {
                    val mcctype = MccTypeMap[netMcc]
                    if (mcctype != null) {
                        val map = MccTypeMap.filterValues { mcctype == it }
                        map.keys.forEach { mcc ->
                            if (fplmn.startsWith(mcc)) {
                                set.add(fplmn)
                            }
                        }
                    }
                }
            }
        }

        return set.toTypedArray()
    }

    private fun NotifySeedFplmnChanged() {
        ServiceManager.seedCardEnabler.notifyEventToCard(DataEnableEvent.ENV_MCC_CHANGED, null)
    }

    //记录物理卡最后一次设置的fplmn 要持久保存
    fun markPhyLastFplmn(imsi: String, newFplmn: Array<String>?) {
        lastPhyfplmnMapLock.write {
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

    //获取物理卡最后一次设置的fplmn,从持久保存里面取
    private fun getPhyLastFplmn(imsi: String): Array<String>? {
        lastPhyfplmnMapLock.read {
            val phyPlmnStr = SharedPreferencesUtils.getString(ServiceManager.appContext, "PHY_LAST_FPLMN_$imsi")
            if (phyPlmnStr.isEmpty()) {
                return null
            }
            val plmns = phyPlmnStr.split(",")
            val plmnArray = Array(plmns.size, { plmns[it] })
            return plmnArray
        }
    }

    //获取软卡最后一次配置fplmn设置，无需持久保存
    private fun getSoftSeedLastFplmn(imsi: String): Array<String>? {
        lastfplmnMapLock.read {
            return lastSoftSimFplmns[imsi]
        }
    }

    //记录软卡最后一次配置fplmn设置
    fun markSoftSeedLastFplmn(imsi: String, fplmn: Array<String>?) {
        lastfplmnMapLock.write {
            lastSoftSimFplmns[imsi] = fplmn
        }
    }

    /**
     *
     * @return 返回Pair pair.first 表示是否要更新，pair.second 表示要更新fplmnlist
     */
    fun checkSeedFplmnList(imsi: String, subId: Int, cardType: CardType): Pair<Boolean, Array<String>?> {

        logd("[checkSeedFplmnUpdate] enter")
        if (TextUtils.isEmpty(imsi)) {
            loge("[checkSeedFplmnUpdate] imsi is empty")
            return Pair(false, null)
        }

        if (subId == -1) {
            loge("[checkSeedFplmnUpdate] subId == -1")
            return Pair(false, null)
        }

        val lastFplmn: Array<String>? = when (cardType) {
            CardType.PHYSICALSIM -> getPhyLastFplmn(imsi)
            CardType.SOFTSIM -> getSoftSeedLastFplmn(imsi)
            else -> {
                loge("[checkSeedFplmnUpdate] unknown card type")
                return Pair(false, null)
            }
        }

        val newfplmnsList = getCurrentMccFplmnByImsi(imsi, cardType)
        logd("[checkSeedFplmnUpdate] lastFplmn " + lastFplmn?.printContent())
        logd("[checkSeedFplmnUpdate] newfplmnsList " + newfplmnsList?.printContent())
        val isSame = isArraySame(lastFplmn, newfplmnsList)
        if (!isSame) {
            val regPlmn = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
            val doUpdateFplmn: Boolean
            logd("[checkSeedFplmnUpdate] regPlmn :$regPlmn")
            doUpdateFplmn = if (TextUtils.isEmpty(regPlmn)) {
                //没注册上,直接更新
                true
            } else {
                //注册上
                kotlin.run {
                    newfplmnsList ?: return@run false
                    newfplmnsList.forEach {
                        if (it == regPlmn) {
                            return@run true
                        }
                    }
                    return@run false
                }
            }
            logd("[checkSeedFplmnUpdate] doUpdateFplmn :$doUpdateFplmn newfplmnsList:${newfplmnsList?.printContent()}")
            return Pair(doUpdateFplmn, newfplmnsList)
        }
        logd("[checkSeedFplmnUpdate] return false")
        return Pair(false, null)
    }


    private fun isArraySame(array1: Array<String>?, array2: Array<String>?): Boolean {
        logd("[checkSeedFplmnUpdate] array1  " + array1?.printContent())
        logd("[checkSeedFplmnUpdate] array2  " + array2?.printContent())
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
                val mark = Array(array1!!.size, { false })

                for (i in array1.indices) {
                    array2!!.forEach {
                        if (array1[i] == it) {
                            mark[i] = true
                            return@forEach
                        }
                    }
                }

                mark.forEach {
                    if (!it) {
                        return false
                    }
                }
                return true
            } else {
                return false
            }
        }
    }

}

const val EXCEPTION_BASE = 1000
/**
 * 注册上，但不能用的网络。
 * 如果没注册上，请不要发送这个事件
 */
const val PDP_REJECT_EXCEPTION = EXCEPTION_BASE + 1
const val SEED_SOCKET_FAIL_MAX_EXCEPTION = EXCEPTION_BASE + 2
const val ATTACH_REJECT_EXCEPTION = EXCEPTION_BASE + 3
const val CLEAN_TEMP_FPLMN = 2
package com.ucloudlink.refact.platform.sprd.flow.netlimit

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.text.TextUtils
import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.framework.mbnload.MbnTestUtils.getPropertyValue
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.flow.netlimit.common.DnsUtils
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.utils.JLog
import java.util.*

/**
 *
 * copy from QCSeedCardNetCtrlImpl
 */

open class SprdSeedCardNetCtrlImpl : ISeedCardNetCtrl {

    private var seedCardIfName: String? = null

    private var mNetworkInfoState: NetworkInfo.State = NetworkInfo.State.DISCONNECTED

    private var mUidISeedCardNetCtrl: ISeedCardNetCtrl = SprdUidSeedCardNetCtrlImpl()
    private val operaterNetLock = byteArrayOf(0)

    private val networkListen: CardStateMonitor.NetworkStateListen = CardStateMonitor.NetworkStateListen { ddsId: Int, networkState: NetworkInfo.State, type: Int, ifName: String, isExistIfNameExtra: Boolean, subId: Int ->

        synchronized(operaterNetLock){
            JLog.logd("SeedCardNetLog NetworkStateListen -> ddsId = $ddsId, networkState = $networkState" +
                    ", type = $type, ifName = $ifName, isExistIfNameExtra = $isExistIfNameExtra, subId = $subId")

            mNetworkInfoState = networkState

            if (isExistIfNameExtra && !TextUtils.isEmpty(ifName) && type == ConnectivityManager.TYPE_MOBILE) {// Sprd没有TYPE_MOBILE_DUN

                if (SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks) {
                    var isSeedCardIfName: Boolean = type == ConnectivityManager.TYPE_MOBILE_DUN
                    if (!isSeedCardIfName) {
                        if (ServiceManager.seedCardEnabler.getCardState() >= CardStatus.READY
                                && ServiceManager.seedCardEnabler.getCard().subId > -1) {
                            if (subId == ServiceManager.seedCardEnabler.getCard().subId) {
                                isSeedCardIfName = true
                            }
                        }
                    }

                    JLog.logd("SeedCardNetLog NetworkStateListen -> isInRestrictAllNetworks = ${SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks}" +
                            ", isSeedCardIfName = $isSeedCardIfName, ifName = $ifName, seedCardIfName = $seedCardIfName")

                    if (isSeedCardIfName) {
                        seedCardIfName = ifName
                        val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
                        tempMapIfName.forEach {
                            FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.clearRestrictAllRule(it.value)
                        }

                        val ifNameList = ArrayList<String?>()
                        ifNameList.add(ifName)
                        FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setRestrictAllNetworks(ifName)
                        enableIp(ifNameList)
                        enableDns(ifNameList)

                    } else {
                        if(TextUtils.isEmpty(seedCardIfName)){
                            val ifNameList = ArrayList<String?>()
                            ifNameList.add(ifName)
                            FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setRestrictAllNetworks(ifName)
                            enableIp(ifNameList)
                            enableDns(ifNameList)

                        } else if(ifName.equals(seedCardIfName)){
                            seedCardIfName = null
                            val ifNameList = ArrayList<String?>()
                            val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
                            tempMapIfName.forEach {
                                ifNameList.add(it.value)
                                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setRestrictAllNetworks(it.value)
                            }
                            enableIp(ifNameList)
                            enableDns(ifNameList)

                        } else {
                            FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.clearRestrictAllRule(ifName)
                        }
                    }
                } else {
                    JLog.logd("SeedCardNetLog NetworkStateListen -> isInRestrictAllNetworks = ${SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks}"
                            + ", ifName = $ifName" +
                            ", seedCardIfName = $seedCardIfName, -> will call clearRule")
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.clearRestrictAllRule(ifName)
                }
            }
        }

    }

    private val mUiSupportSeedNetLimitChangListener = object : SeedCardNetLimitHolder.OnUiSuppordSeedNetLimitChangeListener {

        override fun onUiSupportSeedNetLimitChange(isUiSupportSeedNetworkLimitChange: Boolean) {
            SeedCardNetLimitHolder.getInstance().removeUiSuppordSeedNetLimitChangeListener(mUiSupportSeedNetLimitChangListener@ this)
            SeedCardNetLimitHolder.getInstance().removeNetworkStateListen(networkListen)
        }
    }

    override fun initState(seedSimEnable: IDataEnabler, cloudSimEnabler: IDataEnabler) {
        mUidISeedCardNetCtrl.initState(seedSimEnable, cloudSimEnabler)
        SeedCardNetLimitHolder.getInstance().addUiSuppordSeedNetLimitChangeListener(mUiSupportSeedNetLimitChangListener)
        //ServiceManager.simMonitor.addNetworkStateListen(networkListen)
        SeedCardNetLimitHolder.getInstance().addNetworkStateListen(networkListen)
        getPreIfNameList().forEach {
            if (it != null && it.isNotEmpty()) {
                SeedCardNetLimitHolder.getInstance().putIfName(it)
            }
        }
    }

    private fun putIP(info: SeedCardNetInfo) {
        val cacheKey = SeedCardNetLimitHolder.getCacheKey(info)
        if (TextUtils.isEmpty(cacheKey)) {
            return
        }

        if (SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks) {
            if (!TextUtils.isEmpty(seedCardIfName)) {
                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setNetworkPassByIp(seedCardIfName, info.uid, info.vl)
            } else {
                val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
                tempMapIfName.forEach {
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setNetworkPassByIp(it.value, info.uid, info.vl)
                }
            }
        }
    }

    private fun removeIP(info: SeedCardNetInfo) {
        val cacheKey = SeedCardNetLimitHolder.getCacheKey(info)
        if (TextUtils.isEmpty(cacheKey)) {
            return
        }
        if (SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks) {
            if (!TextUtils.isEmpty(seedCardIfName)) {
                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.removeNetworkPassByIp(seedCardIfName, info.uid, info.vl)
            } else {
                val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
                tempMapIfName.forEach {
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.removeNetworkPassByIp(it.value, info.uid, info.vl)
                }
            }
        }
    }

    private fun configIP(info: SeedCardNetInfo) {
        if (info.enable) {
            putIP(info)
        } else {
            removeIP(info)
        }
    }

    private fun putDns(info: SeedCardNetInfo) {
        val cacheKey = SeedCardNetLimitHolder.getCacheKey(info)
        if (TextUtils.isEmpty(cacheKey)) {
            return
        }

        if (SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks) {
            if (!TextUtils.isEmpty(seedCardIfName)) {
                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setEnableDNSByDomain(seedCardIfName, info.uid, info.vl)
            } else {
                val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
                tempMapIfName.forEach {
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setEnableDNSByDomain(it.value, info.uid, info.vl)
                }
            }
        }
    }

    private fun removeDns(info: SeedCardNetInfo) {
        val cacheKey = SeedCardNetLimitHolder.getCacheKey(info)
        if (TextUtils.isEmpty(cacheKey)) {
            return
        }
        if (SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks) {
            if (!TextUtils.isEmpty(seedCardIfName)) {
                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.removeEnableDNSByDomain(seedCardIfName, info.uid, info.vl)
            } else {
                val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
                tempMapIfName.forEach {
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.removeEnableDNSByDomain(it.value, info.uid, info.vl)
                }
            }
        }
    }

    private fun configDns(info: SeedCardNetInfo) {
        if (info.enable) {
            putDns(info)
        } else {
            removeDns(info)
        }
    }

    /**
     * 注意域名参数，如果是http://www.baidu.com：8080?a=xxx则需要截取到域名www.baidu.com
     */
    override fun configDnsOrIp(info: SeedCardNetInfo) {
        JLog.logi("SeedCardNetLog, isInRestrictAllNetworks = ${SeedCardNetLimitHolder.getInstance().isInRestrictAllNetworks}" +
                ", seedCardIfName = $seedCardIfName, configDnsOrIp() info = " + info.toString())
        if (!TextUtils.isEmpty(info.vl) /*&& info.uid > 0*/) {
            if (DnsUtils.isDnsStartWithNumber(info.vl)) {
                configIP(info)
            } else {
                configDns(info)
            }
        }
    }

    // 允许Ip通过ifName上网
    private fun enableIp(listIfName: ArrayList<String?>) {
        val tempMapIP = SeedCardNetLimitHolder.getInstance().copyMapIP
        tempMapIP.forEach { ipIt ->
            val info = ipIt.value
            if (!TextUtils.isEmpty(info.vl) /*&& info.uid > 0*/ && info.enable) {
                JLog.logi("SeedCardNetLog, enableIp -> , " + info.toString())
                listIfName.forEach { ifNameIt ->
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setNetworkPassByIp(ifNameIt, info.uid, info.vl)
                }
            }
        }
    }

    // 允许dns通过ifName上网
    private fun enableDns(listIfName: ArrayList<String?>) {
        val tempMapDns = SeedCardNetLimitHolder.getInstance().copyMapDns
        tempMapDns.forEach { dnsIt ->
            val info = dnsIt.value
            if (!TextUtils.isEmpty(info.vl) /*&& info.uid > 0*/ && info.enable) {
                JLog.logi("SeedCardNetLog, enableDns -> , " + info.toString())
                listIfName.forEach { ifNameIt ->
                    FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setEnableDNSByDomain(ifNameIt, info.uid, info.vl)
                }
            }
        }
    }

    // 限制ifName网口上网
    @Synchronized override fun setRestrictAllNet(tag: String?) {
        synchronized(operaterNetLock){
            JLog.logi("SeedCardNetLog, setRestrictAllNet() seedCardIfName = $seedCardIfName, tag = $tag")
            val listIfName = ArrayList<String?>()
            val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
            tempMapIfName.forEach {
                listIfName.add(it.value)
                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.setRestrictAllNetworks(it.value)
            }
            enableIp(listIfName)
            enableDns(listIfName)
        }

    }

    // 清除ifName下所有新接口的规则
    @Synchronized override fun clearRestrictAllRuleNet(tag: String?) {
        synchronized(operaterNetLock){
            mUidISeedCardNetCtrl.clearRestrictAllRuleNet(tag)
            JLog.logi("SeedCardNetLog, clearRestrictAllRuleNet() seedCardIfName = $seedCardIfName, tag = $tag")
            seedCardIfName = null
            val tempMapIfName = SeedCardNetLimitHolder.getInstance().copyMapIfName
            tempMapIfName.forEach {
                FlowBandWidthControl.getInstance().seedCardNetManager.iSeedCardNet.clearRestrictAllRule(it.value)
            }
        }

    }

    /**
     * 获取ifaceName 接口列表, List<String>{rmnet_data0, rmnet_data1}
     */
    open fun getPreIfNameList(): ArrayList<String?> {
        val ret = ArrayList<String?>()

        var strVal: String?
        var key = "net.seth_lte0.dns1"
        strVal = getPropertyValue(key)
        if (TextUtils.isEmpty(strVal)) {
            key = "net.seth_lte0.dns2"
            strVal = getPropertyValue(key)
        }
        JLog.logd("getPreIfNameList(1): " + "key = " + key + ", value:" + if (strVal == null) "null" else strVal)
        if (!TextUtils.isEmpty(strVal) && strVal!!.length > FlowBandWidthControl.BWCC_IP_ADDR_MIN_LENG) {
            ret.add("seth_lte0")
        }

        key = "net.seth_lte1.dns1"
        strVal = getPropertyValue(key)
        if (TextUtils.isEmpty(strVal)) {
            key = "net.seth_lte1.dns2"
            strVal = getPropertyValue(key)
        }
        JLog.logd("getPreIfNameList(2): " + "key = " + key + ", value:" + if (strVal == null) "null" else strVal)
        if (!TextUtils.isEmpty(strVal) && strVal!!.length > FlowBandWidthControl.BWCC_IP_ADDR_MIN_LENG) {
            ret.add("seth_lte1")
        }

        //Note U3C刚开机时上面的代码会获取失败（strVal的值是空字符串)，该方法在SprdU3CSeedNetCtrlImpl中被重写
        return ret
    }
}
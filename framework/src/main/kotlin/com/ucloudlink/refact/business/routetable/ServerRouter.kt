package com.ucloudlink.refact.business.routetable

import android.os.SystemProperties
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.routetable.RouteTableManager.getLocalAssIPList
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.netlimit.common.DnsUtils
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils
import java.net.InetSocketAddress
import java.util.*

/**
 * Created by chentao on 2016/8/29.
 */
object ServerRouter {
    enum class Dest {
        ASS,
        OSS,
        LOG
    }

    const val BUSINESS: Int = 100
    const val SAAS2: Int = 101
    const val SAAS3: Int = 102
    const val FACTORY: Int = 103

    const val SERVER_TEST_PROP_CFG = "persist.ucloud.vsim.server"  // 服务器模式，0 商用  1 商用测试   2：saas2  3：saas3  4:工厂 5：app中设置的测试模式 6：读配置文件
    const val FACTORY_MODE_PROP = "ro.ucloud.factorymode" // 工厂模式： 0 正常模式  1 工厂模式

    const val ROUTE_IP_SAAS2: String = "58.251.37.197:10144"
    const val ROUTE_IP_SAAS3: String = "58.251.37.197:10157"
    internal var ROUTE_IP_FACTORY: String = "58.251.37.197:10183"

    const val ROUTE_IP_BUSSINESS: String = "rts.ucloudlink.com:9000"
    const val ROUTE_IP_BUSSINESS_BACKUP: String = "52.220.59.112:9000"

    val LAST_FACTORY_CHANGE = "last_factory_change" //这个用于检测是否通过ro.ucloud.factorymode切换的模式
    var current_mode: Int = BUSINESS
    var current_AssIp = ROUTE_IP_BUSSINESS
    var current_RouteIp: String = ROUTE_IP_BUSSINESS

    val ipAddrMap = HashMap<Dest, InetSocketAddress>()

    init {
        ROUTE_IP_FACTORY = SharedPreferencesUtils.getString(ServiceManager.appContext, "factory_ip", "58.251.37.197:10183")
        SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ROUTE_IP_FACTORY), SysUtils.getUServiceUid()))

        val facMode = SystemProperties.get(FACTORY_MODE_PROP, "0")
        logd("get ro.ucloud.factorymode: " + facMode)
        if (facMode.equals("1")) {
            // get ip from factory mode file!!
            SharedPreferencesUtils.putInt(ServiceManager.appContext, LAST_FACTORY_CHANGE, 1)
            setIpMode(FACTORY)
        } else {
            val saveMode = SharedPreferencesUtils.getInt(ServiceManager.appContext, "IP_MODE", BUSINESS)
            val lastFactory = SharedPreferencesUtils.getInt(ServiceManager.appContext, LAST_FACTORY_CHANGE, 0)
            if(saveMode.equals(FACTORY) && lastFactory == 1) {
                SharedPreferencesUtils.putInt(ServiceManager.appContext, LAST_FACTORY_CHANGE, 0)
                setIpMode(BUSINESS)
            }
            else {
                logd("tRoute init, saveMode = ${saveMode}")
                setIpMode(saveMode)
            }
        }
    }

    //设置ip模式
    fun setIpMode(mode: Int) {
        if (mode != SAAS2 && mode != SAAS3 && mode != FACTORY && mode != BUSINESS) {
            loge("tRoute setIpMode error mode="+mode+" (100-103)")
            return
        }
        if (mode != current_mode) {
            SharedPreferencesUtils.putInt(ServiceManager.appContext, "IP_MODE", mode)
        }
        when (mode) {
            SAAS2 -> {
                current_mode = SAAS2
                current_RouteIp = ROUTE_IP_SAAS2
            }
            SAAS3 -> {
                current_mode = SAAS3
                current_RouteIp = ROUTE_IP_SAAS3
            }
            FACTORY -> {
                ROUTE_IP_FACTORY = SharedPreferencesUtils.getString(ServiceManager.appContext, "factory_ip", "58.251.37.197:10183")
                current_mode = FACTORY
                current_RouteIp = ROUTE_IP_FACTORY
            }
            BUSINESS -> {
                current_mode = BUSINESS
                current_RouteIp = ROUTE_IP_BUSSINESS
            }
        }
        logd("tRoute setIpMode current_mode = ${current_mode} ,(100-BUSINESS 101-SAAS2 102-SAAS3 104-Factory) current_RouteIp=${current_RouteIp}")
    }

    //设置ass ip，仅限路由模块调用
    fun setAssIp(ipAndPort: String) {
        val ipPort: List<String> = ipAndPort.split(":")
        if (ipPort.size != 2) {
            loge("tRoute setAssIp error ip error:$ipAndPort")
            return
        }
        val ip: String = ipPort[0]
        val port: Int = ipPort[1].toInt()
        try {

            FlowBandWidthControl.getInstance().iNetSpeedCtrl.configNetSpeedWithIP(ip, SysUtils.getUServiceUid()
                    , Configuration.isBandWidthIp, Configuration.isBandWidthSet, Configuration.isEnableBandWidth, 0, 0)
            loge("NetSpeedCtrlLog", "SeedCardNetLog server set ip=" + ip + ", isEnableBandWidth=" + Configuration.isEnableBandWidth
                    + ", isBandWidthIp=" + Configuration.isBandWidthIp + ", isBandWidthSet=" + Configuration.isBandWidthSet)

            SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ip), SysUtils.getUServiceUid()))

            ipAddrMap.put(Dest.ASS, InetSocketAddress(ip, port))//10.1.1

            if (current_AssIp != ipAndPort) {
                ServiceManager.transceiver.disconnect(Dest.ASS)
            }

            current_AssIp = ipAndPort
            logd("tRoute setAssIp current_AssIp =${current_AssIp}")

        } catch (e: Exception) {
            loge("tRoute setAssIp InetSocketAddress error:${e}")
        }
    }

    //根据当前模式初始化ip，仅限路由模块调用
    fun initIpByCurrentMode() {
        logd("tRoute initIpByCurrentMode:${current_mode}")
        val assIpList = getLocalAssIPList(current_mode)
        if (assIpList != null) {
            var assIP = assIpList[0]
            setAssIp(assIP)
        } else {
            setAssIp(current_RouteIp)
        }
    }

    //通过广播修改工厂路由ip
    fun setFactoryIP(ip: String) {
        logd("tRoute setFactoryIP ip:${ip}")
        ROUTE_IP_FACTORY = ip
        SharedPreferencesUtils.putString(ServiceManager.appContext, "factory_ip", ip)
        SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ROUTE_IP_FACTORY), SysUtils.getUServiceUid()))
    }

    fun removeAssIP(){
        ipAddrMap.remove(Dest.ASS)
    }

}

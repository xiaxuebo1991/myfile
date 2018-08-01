package com.ucloudlink.refact.config

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import kotlin.properties.Delegates

/**
 * Created by chentao on 2016/6/28.
 */

const val SYSTEM_TYPE_QCOMM = 0
const val SYSTEM_TYPE_MTK = 1
const val SYSTEM_TYPE_SPORD = 2

object Configuration {
    val SID = "P2_SERVICE";
    val frameworkVersion = "0.0.5"

    var appDataDir: String by Delegates.notNull()
    var extLibDir: String by Delegates.notNull()
    var simDataDir: String by Delegates.notNull()
    var modemCfgDataDir: String by Delegates.notNull()
    var qxdmDataDir: String by Delegates.notNull()
    var preferencesDir: String by Delegates.notNull()

    // 规则文件相关
    var RULE_FILE_DIR :String by Delegates.notNull()
    const val RULE_FILE_NAME ="seedRule.bin"

    fun initDir(context: Context) {
        appDataDir = context.applicationInfo.dataDir
        extLibDir = "${appDataDir}/extlib/"
        simDataDir = "${appDataDir}/simdata/"
        modemCfgDataDir = "/data/misc/radio/"
        qxdmDataDir = "/sdcard/qxdmlogcfg/"
        preferencesDir = "${appDataDir}/shared_prefs/"

        //种子卡配置更新相关
        RULE_FILE_DIR= "${appDataDir}/files/rules/"
        

    }

    private const val KEY_IMEI = "DEVICE_IMEI"
    private const val KEY_IMEI0 = "DEVICE_IMEI_SLOT0"

    private var localImeiSlot1 = ""
    private var localImeiSlot0 = ""
    @SuppressLint("MissingPermission", "NewApi")
    @JvmStatic
    @JvmOverloads
    fun getImei(context: Context, slotId: Int = 1): String {
        var imei = if (slotId == 1) localImeiSlot1 else localImeiSlot0
        if (imei.isNotEmpty())
            return imei
        val key = if (slotId == 1) KEY_IMEI else KEY_IMEI0
        try {
            val imeiProp = TelephonyManager.getTelephonyProperty(slotId, "gsm.device.imei", "")
            if (imeiProp.isEmpty()) {
                imei = SharedPreferencesUtils.getString(context, key, "")
                if (imei.isEmpty()) {
                    imei = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).getImei(slotId)
                    logd("get imei $slotId from slot $slotId return $imei")
                    if (imei.isEmpty()) {
                        throw Exception("cannot read imei!!!!")
                    } else {
                        SharedPreferencesUtils.putString(context, key, imei)
                    }
                }
                saveImei(slotId, imei)
            } else {
                logd("get imei $slotId from telephony property $imeiProp")
                saveImei(slotId, imeiProp)
                SharedPreferencesUtils.putString(context, key, imeiProp)
            }
        } catch (e: Exception) {
            loge("get imei $slotId failed: $e")
        }
        return if (slotId == 1) localImeiSlot1 else localImeiSlot0
    }

    private fun saveImei(slotId: Int, imei: String) {
        if (slotId == 1) {
            localImeiSlot1 = imei
        } else {
            localImeiSlot0 = imei
        }
    }

    val iccid = ""
    val rstCardDelay: Long = 14 * 1000L  //完成鉴权等待多少毫秒rst Apdu card
    val delayToEnableSeedCard = 4 * 1000L // 首次鉴权时，DDS切换到Vsim后，延迟时间启动种子卡

    var cloudSimSlot: Int = 0
        get() {
            val slot = RunningStates.getCloudSimSlot()
            return if (slot >= 0) {
                slot
            } else {
                if (ServiceManager.sysModel.getDefaultSeedSlot() == 1) 0 else 1
            }
        }
    var seedSimSlot: Int = 1
        get() {
            val slot = RunningStates.getSeedSimSlot()
            return if (slot >= 0) {
                slot
            } else {
                ServiceManager.sysModel.getDefaultSeedSlot()
            }
        }
    var LOGIN_TYPE:Int = 0
    /**
     * 注意从0开始,0表示卡槽1   1 表示卡槽2
     */
    fun setSlots(seedSimSlot: Int, cloudSimSlot: Int) {
        logd("setSlots: " + seedSimSlot + " " + cloudSimSlot + " " + ServiceManager.accessEntry.isServiceRunning)
        this.seedSimSlot = seedSimSlot
        this.cloudSimSlot = cloudSimSlot
        RunningStates.saveSeedSimSlot(seedSimSlot)
        RunningStates.saveCloudSimSlot(cloudSimSlot)
    }

    var orderId = ""

    /**
     * 方案配置
     */
    val ApduMode_soft = 0  //软卡方案
    val ApduMode_Phy = 1    //实体卡方案
    var ApduMode = ApduMode_Phy //0

    val isShowThreadAtLog = false   //是否在log中显示当前线程

    //系统版本
    var currentSystemVersion: Int = 0 //当前系统版本
    /**
     * 系统类型
     * SYSTEM_TYPE_QCOMM = 0
     * SYSTEM_TYPE_MTK = 1
     * SYSTEM_TYPE_SPORD = 2
     *
     * */
    var SYSTEM_TYPE = SYSTEM_TYPE_QCOMM

    val pingTask = false //是否启动ping任务
    /**
     * debug 配置
     */
    //    val isForChina=false   //是否为中国版本
    val isForChina = true   //是否为中国版本

    val NeedScanNetwork = false //是否需要搜网获取周边网络的sidList
    val FLOWSTATS_READ_PERIOD = 5 * 1000L   //流量读取周期
    val UPLOADFLOW_PERIOD = 120 * 1000L   //上报流量周期
    val FlowStatsReadPeriod = 5 * 1000L   //流量读取周期
    val UploadFlowPeriod = 120 * 1000L           //默认上报流量周期
    val UploadFlowThreosHold = 10 * 1024 * 1024L   //默认上报流量阈值
    val SCUploadFlowThreosHold = 10 * 1024L   //上报流量阈值
    val SCUloadFlowValue = 100 * 1024L  // 累加流量阈值
    val SCUploadFlowTimeValue = 30 * 60 * 1000 // 流量缓存时间
    var UPLOADFLOW_INTERVAL = 5000     //流量上报间隔最短时间单位ms
    var isEnableBandWidth: Boolean // 是否开关流量控制
        set(value) {
            SharedPreferencesUtils.putBoolean("isEnableBandWidth", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("isEnableBandWidth", true)
        }
    var isBandWidthIp: Boolean  // true-通过ip控制流量， false-通过uid控制流量
        set(value) {
            SharedPreferencesUtils.putBoolean("isBandWidthIp", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("isBandWidthIp", true)
        }
    var isBandWidthSet: Boolean  // true-放通流量， false-限制流量
        set(value) {
            SharedPreferencesUtils.putBoolean("isBandWidthSet", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("isBandWidthSet", true)
        }
    var RECOVE_WHEN_REBOOT: Boolean    //是否开机恢复
        set(value) {
            SharedPreferencesUtils.putBoolean("RECOVE_WHEN_REBOOT", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("RECOVE_WHEN_REBOOT", false)
        }

    var AUTO_WHEN_REBOOT: Boolean
        set(value) {
            SharedPreferencesUtils.putBoolean("AUTO_WHEN_REBOOT", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("AUTO_WHEN_REBOOT",false )
        }

    var PHY_ROAM_ENABLE: Boolean
        set(value) {
            SharedPreferencesUtils.putBoolean("PHY_ROAM_ENABLE", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("PHY_ROAM_ENABLE", true)
        }

    var MODEM_LOG_ENABLE: Boolean
        set(value) {
            SharedPreferencesUtils.putBoolean("MODEM_LOG_ENABLE", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("MODEM_LOG_ENABLE", false)
        }

    var HARD_GPS_CFG:Boolean
        set(value) {
            SharedPreferencesUtils.putBoolean("HARD_GPS_CFG", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("HARD_GPS_CFG", false)
        }

    var NETWORK_GPS_CFG:Boolean
        set(value) {
            SharedPreferencesUtils.putBoolean("NETWORK_GPS_CFG", value)
        }
        get() {
            return SharedPreferencesUtils.getBoolean("NETWORK_GPS_CFG", false)
        }

    var LOCAL_SEEDSIM_DEPTH_OPT:Boolean
        set(value) {
            logd("set LOCAL_SEEDSIM_DEPTH_OPT $value")
            SharedPreferencesUtils.putBoolean("LOCAL_SEEDSIM_DEPTH_OPT", value)
        }
        get() {
            val v = SharedPreferencesUtils.getBoolean("LOCAL_SEEDSIM_DEPTH_OPT", true)
            logd("get LOCAL_SEEDSIM_DEPTH_OPT $v")
            return v
        }

    var cloudSimApns: List<Apn>? = null

    var isDoingPsCall = false

    var temp_dunStr = ""//临时存放当前使用的dunApn
    var temp_dun_numericStr = ""//临时存放当前使用的dunApn的numeric
    var vsim_mnc_numeric = 2
    var softsim_mnc_numeric = 2

    var isOpenRplmnTest = false
    var originSubid: Int? = null

    val soketConnnectTimeout: Long = 74
    val enableSoftsimTimeout: Long = 220
    val enablePhysimTimeout: Long = 20
    var tcpdumpEnable: Boolean = true
    val useServerSoftsim = true
    //val useServerSoftsim = false//调试使用

    var username: String? = null

//    var isPhyCardAvailable: Boolean = true
//    var isSoftCardAvailable: Boolean = false
//    var softCartAvailableSlot: Int = 0
    //表示软卡是否因为异常被关闭
//    var softCartExceptFlag: Boolean = false
//    var phyCardUnavailableReason: EnablerException = EnablerException.EXCEPTION_DATA_ENABLE_CLOSED

    const val ANDROID_ORGIN = 0
    const val ANDROID_MIUI_V8 = 1
    const val ANDROID_COOL_C103 = 2

    val FTP_DN = "223.197.68.225"
    val TIME_URL = "http://t1.ukelink.com/"
    var internalPhyCardImsi: String =""
    set(value) {
        logd("set phyCardImsi: $field -> $value")
        field = value
    }

    const val RULE_ITEM_VALUE_SPLIT = ":"
    const val RULE_ITEM_SPLIT = ";"
    
}

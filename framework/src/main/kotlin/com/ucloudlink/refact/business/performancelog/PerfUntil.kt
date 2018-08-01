package com.ucloudlink.refact.business.performancelog

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.telephony.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.ucloudlink.framework.protocol.protobuf.Performance_log_report
import com.ucloudlink.framework.protocol.protobuf.performance_log_resp
import com.ucloudlink.framework.protocol.protobuf.preflog.*
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.netcheck.*
import com.ucloudlink.refact.business.netcheck.NetworkManager.getSystemVersionName
import com.ucloudlink.refact.business.performancelog.db.DBHelper
import com.ucloudlink.refact.business.performancelog.db.SqliteHelper
import com.ucloudlink.refact.business.performancelog.logs.PerfLogPownOn.SP_BOOT_TIME
import com.ucloudlink.refact.business.statebar.NoticeStatusBarServiceStatus
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.PackageUtils
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import okio.ByteString
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*


/**
 * 性能统计工具类
 */
object PerfUntil {
    val SOCKET_TIME_OUT = 35
    private var seq_id = 0
    val eventList = ArrayList<Any>()//保存事件列表
    val eventListFreq = ArrayList<Any>()//保存频繁触发的事件

    val MAX_SAVE_COUNT = 400     //非频繁触发的事件 数据库最大保存条数，新的覆盖旧的
    val FREQ_MAX_SAVE_COUNT = 50//频繁触发的事件 数据库最大保存条数，新的覆盖旧的


    /**
     * 获取当前网络信息如PLMN,CELLID,LAC等
     */
    fun getNetInfo(slot: Int): NetInfo {
        val teleMnger = NetworkManager.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val mSubscriptionManager = SubscriptionManager.from(NetworkManager.context)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        val dds = ServiceManager.systemApi.getDefaultDataSubId()
        val slotId = SubscriptionManager.getPhoneId(dds)

        var subId: Int = -1
        var iccid: String = ""
        var mccmnc: String = ""
        var rat: Int = -1
        var imsi: String = ""



        Thread.sleep(1000)
        mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)

        if (mSubscriptionInfo != null) {
            subId = mSubscriptionInfo.subscriptionId
            iccid = mSubscriptionInfo.iccId
            mccmnc = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
            rat = teleMnger.getNetworkType(subId)
            imsi = teleMnger.getSubscriberId(subId) ?: ""
        }

        var imei = Configuration.getImei(NetworkManager.context)
        var cellid: Int = -1
        var lac: Int = -1

        var signalStrength: Int = -1
        var signalQuality = -1
        var rssi = -1

        val cellInfos = teleMnger.allCellInfo
        if (cellInfos == null) {
            JLog.logd("getNetInfo cellinfos is null")
        } else {
            var i = 0
            while (i < cellInfos.size) {
                if (cellInfos[i].isRegistered) {
                    logd("getNetInfo cellInfos[$i] = ${cellInfos[i]}")
                    if (cellInfos[i] is CellInfoWcdma) {
                        val cellInfoWcdma: CellInfoWcdma = cellInfos[i] as CellInfoWcdma
                        val cellIdentityWcdma = cellInfoWcdma.cellIdentity
                        val cellSignalStrengthWcdma: CellSignalStrengthWcdma = cellInfoWcdma.cellSignalStrength

                        val mcc = cellIdentityWcdma.mcc
                        val mnc = cellIdentityWcdma.mnc
                        val mccmncCellInfo = getMccmnc(mcc, mnc)
                        if (mccmncCellInfo != null && mccmnc.length > 0) {
                            if (mccmncCellInfo == mccmnc) {
                                signalStrength = cellSignalStrengthWcdma.dbm
                                cellid = cellIdentityWcdma.cid
                                lac = cellIdentityWcdma.lac
                                JLog.logd("getNetInfo CellInfoWcdma: $cellInfoWcdma")
                                break
                            }
                        }
                    } else if (cellInfos[i] is CellInfoGsm) {
                        val cellInfoGsm: CellInfoGsm = cellInfos[i] as CellInfoGsm
                        val cellSignalStrengthGsm: CellSignalStrengthGsm = cellInfoGsm.cellSignalStrength
                        val cellIdentityGsm = cellInfoGsm.cellIdentity

                        val mcc = cellIdentityGsm.mcc
                        val mnc = cellIdentityGsm.mnc
                        val mccmncCellInfo = getMccmnc(mcc, mnc)
                        if (mccmncCellInfo != null && mccmnc.length > 0) {
                            if (mccmncCellInfo == mccmnc) {
                                signalStrength = cellSignalStrengthGsm.dbm
                                cellid = cellIdentityGsm.cid
                                lac = cellIdentityGsm.lac
                                JLog.logd("getNetInfo CellInfoGsm: $cellInfoGsm")
                                break
                            }
                        }

                    } else if (cellInfos[i] is CellInfoLte) {
                        val cellInfoLte: CellInfoLte = cellInfos[i] as CellInfoLte
                        val cellSignalStrengthLte: CellSignalStrengthLte = cellInfoLte.cellSignalStrength
                        val cellIdentityLte = cellInfoLte.cellIdentity

                        val mcc = cellIdentityLte.mcc
                        val mnc = cellIdentityLte.mnc
                        val mccmncCellInfo = getMccmnc(mcc, mnc)
                        if (mccmncCellInfo != null && mccmnc.length > 0) {
                            if (mccmncCellInfo == mccmnc) {
                                signalStrength = cellSignalStrengthLte.dbm
                                signalQuality = signalStrength //4G rsrp 等于 信号强度
                                cellid = cellIdentityLte.ci
                                lac = cellIdentityLte.tac
                                JLog.logd("getNetInfo CellInfoLte:$cellInfoLte")
                                break
                            }
                        }
                    } else if (cellInfos[i] is CellInfoCdma) {
                        /*
                        fixme 由于目前不支持CDMA作为种子或者云卡，所以这里可能有问题，请注意！！！
                         */
                        val cellInfoCdma: CellInfoCdma = cellInfos[i] as CellInfoCdma
                        val cellSignalStrengthCdma: CellSignalStrengthCdma = cellInfoCdma.cellSignalStrength
                        val cellIdentityCdma = cellInfoCdma.cellIdentity

                        if(slotId == slot){
                            signalStrength = cellSignalStrengthCdma.dbm

                            if (rat == TelephonyManager.NETWORK_TYPE_CDMA) {
                                signalQuality = cellInfoCdma.cellSignalStrength.cdmaEcio
                                rssi = cellInfoCdma.cellSignalStrength.cdmaDbm
                            } else if (rat == TelephonyManager.NETWORK_TYPE_EVDO_0 || rat == TelephonyManager.NETWORK_TYPE_EVDO_A || rat == TelephonyManager.NETWORK_TYPE_EVDO_B) {
                                signalQuality = cellInfoCdma.cellSignalStrength.evdoEcio
                                rssi = cellInfoCdma.cellSignalStrength.evdoDbm
                            }
                            cellid = cellIdentityCdma.basestationId
                            lac = cellIdentityCdma.systemId
                            JLog.logd("getNetInfo CellInfoCdma:$cellInfoCdma")
                            break
                        }
                    }
                }
                i++
            }
        }
        val loginNetInfo = NetInfo(rat = getPerfRat(rat).value, imei = imei, cellid = cellid, lac = lac, imsi = imsi, iccid = iccid, mccmnc = mccmnc, signal_quality = signalQuality, signal_strength = signalStrength, rssi = rssi, band = Net_band_e.NET_BAND_CLASS_NONE)
        JLog.logd("getNetInfo $loginNetInfo")
        return loginNetInfo
    }

    private fun getMccmnc(mcc: Int, mnc: Int): String? {
        if (mcc != Integer.MAX_VALUE && mnc != Integer.MAX_VALUE) {
            return String.format("%03d", mcc) + String.format("%02d", mnc)
        }
        return null
    }

    data class NetInfo(
            val rat: Int,//todo 无法区分FDD TDD
            val imei: String,
            val cellid: Int,
            val lac: Int,
            val imsi: String,
            val iccid: String,
            var mccmnc: String,
            val signal_quality: Int,  // todo 2G网络 RxQual 无法获取  3G只有cdma evod 能获取,4G rsrp 等于 信号强度
            val signal_strength: Int, // dbm
            val rssi: Int,              //todo   只有cdma网络才能获取
            val band: Net_band_e      // TODO: s1 无法获取
    ) {
        override fun toString(): String {
            return "NetInfo(rat=$rat, imei='$imei', cellid=$cellid, lac=$lac, imsi='$imsi', iccid='$iccid', mccmnc='$mccmnc', signal_quality=$signal_quality, signal_strength=$signal_strength)"
        }
    }

    /**
     * 获取rat
     */
    fun getRatBySlot(slot: Int): Int {
        val teleMnger = NetworkManager.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val mSubscriptionManager = SubscriptionManager.from(NetworkManager.context)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        var subId: Int = -1
        var rat: Int = -1

        Thread.sleep(1000)
        mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)

        if (mSubscriptionInfo != null) {
            mSubscriptionInfo.dataRoaming
            subId = mSubscriptionInfo.subscriptionId
            rat = teleMnger.getNetworkType(subId)
            teleMnger.isNetworkRoaming()
        }
        return rat
    }

    /**
     * 获取iccid
     */
    /**
     * 获取当前网络信息如PLMN,CELLID,LAC等
     */
    fun getIccid(slot: Int): String {
        val mSubscriptionManager = SubscriptionManager.from(NetworkManager.context)
        var iccid: String = ""
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        logd("getIccid mSubscriptionInfo=$mSubscriptionInfo")
        if (mSubscriptionInfo != null) {
            iccid = mSubscriptionInfo.iccId
        }
        return iccid
    }

    /**
     * 获取 Comm_head 共同消息
     */
    fun getCommnoHead(): Comm_head {
        val devicesName = ServiceManager.sysModel.getDeviceName()
        var type = Ter_type_e.TER_TYPE_S1

        if (devicesName.equals("U3C") || devicesName.equals("U2S") || devicesName.equals("GLMU18A01")) { // TODO: need to check device
            type = Ter_type_e.TER_TYPE_U3C
        } else {
            val roFotaVersion = getSystemVersionName()
            if (roFotaVersion.contains("GP17")) {
                type = Ter_type_e.TER_TYPE_COOL1
            } else if (roFotaVersion.contains("S1_S00")) {
                type = Ter_type_e.TER_TYPE_S1
            }
        }
        val hostswver = PackageUtils.getAppVersionName()
        val extswver = NetworkManager.getSystemVersionName()
        val imei = Configuration.getImei(ServiceManager.appContext)
        val accessId: Int = SharedPreferencesUtils.getInt(ServiceManager.appContext, SP_BOOT_TIME)
        val userName = ServiceManager.accessEntry.accessState.userName
        var sessionId = ServiceManager.accessEntry.curSessionId
        if (sessionId != null && sessionId.length >= 8) {
            sessionId = sessionId.substring(sessionId.length - 8, sessionId.length)
        }
        val batteryLevel = getBatteryLevel(ServiceManager.appContext)
        val isConnectWifi = NoticeStatusBarServiceStatus.isWifiConnected(ServiceManager.appContext)
        val isOpenHostpot = isOpenHostpot()
        val isScreenOn = ifScreenOn(ServiceManager.appContext)
        val commonHead = Comm_head(0, type, hostswver, extswver, imei, accessId, userName, sessionId, batteryLevel, isConnectWifi, isOpenHostpot, isScreenOn)
        return commonHead
    }

    /**
     * 获取电量百分比
     */
    fun getBatteryLevel(context: Context): Int {
        val batteryInfoIntent = context.getApplicationContext()
                .registerReceiver(null,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryInfoIntent.getIntExtra("level", 0)
    }

    /**
     * 屏幕是否亮屏
     */
    fun ifScreenOn(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive()
    }

    /**
     * 是否开启热点
     */
    fun isOpenHostpot(): Boolean {
        val mContext: Context = ServiceManager.appContext
        val wifiManager: WifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiApState: Int = wifiManager.getWifiApState();  //获取wifi AP状态
        logd("isOpenHostpot: $wifiApState")
        if (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED || wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) {
            return true
        }
        return false
    }

    /**
     * 无线信道
     */
    fun getAPChaneel(): Int {
        val mContext: Context = ServiceManager.appContext
        val wifiManager: WifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.wifiApConfiguration.apChannel
    }

    fun startUploadPerfLog() {
        logd("startUploadPerfLog()")
        var count = 0
        //读取数据
        val cursor = DBHelper.instance().queryAll(SqliteHelper.PerfLogEntry.TABLE_NAME)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndex("id"))
                val type = cursor.getInt(cursor.getColumnIndex(SqliteHelper.PerfLogEntry.COLUMN_NAME_TYPE))
                val retryCount = cursor.getInt(cursor.getColumnIndex(SqliteHelper.PerfLogEntry.COLUMN_NAME_RETRYCOUNT))
                val date = cursor.getBlob(cursor.getColumnIndex(SqliteHelper.PerfLogEntry.COLUMN_NAME_DATA))
                logd("startUploadPerfLog query datebase", "id=$id; type=$type , retryCount=$retryCount")

                val performance_log_report = createPerformanceLogReport(type, date)
                if (performance_log_report == null) {
                    loge("startUploadPerfLog() error performance_log_report==null, continue")
                    continue
                }
                logd("startUploadPerfLog():" + performance_log_report)
                Requestor.requestPerformanceLogReportResult(performance_log_report, SOCKET_TIME_OUT)
                        .subscribe({
                            if (it is performance_log_resp && it.errorCode == 100) {
                                uploadPerfLogSuccess(id)
                            } else {
                                uploadPerfLogFail(id)
                            }
                        }, {
                            uploadPerfLogFail(id)
                        })
                count++
                if (count >= 100) {
                    //一次最多上传100条
                    break
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private fun uploadPerfLogSuccess(id: Int) {
        logd("uploadPerfLogSuccess() id=$id")
        //删除数据库中上传成功数据
        DBHelper.instance().delete("delete from " + SqliteHelper.PerfLogEntry.TABLE_NAME + " where id = ?", arrayOf(id))
    }

    private fun uploadPerfLogFail(id: Int) {
        logd("uploadPerfLogFail() id=$id")
        //上传失败的数据增加失败次数
        var retryCount = 0
        val cursor = DBHelper.instance().query("select * from " + SqliteHelper.PerfLogEntry.TABLE_NAME + " where id = '" + id + "'")
        if (cursor.moveToFirst()) {
            do {
                retryCount = cursor.getInt(cursor.getColumnIndex(SqliteHelper.PerfLogEntry.COLUMN_NAME_RETRYCOUNT))
            } while (cursor.moveToNext())
        }
        cursor.close()
        retryCount++
        logd("uploadPerfLogFail() retryCount=$retryCount")
        DBHelper.instance().update("update " + SqliteHelper.PerfLogEntry.TABLE_NAME + " set " + SqliteHelper.PerfLogEntry.COLUMN_NAME_RETRYCOUNT + " = ? where id = ?", arrayOf(retryCount, id))
    }

    /**
     * 创建PerformanceLogReport上报请求
     * @param id Perf_log_id_e
     */
    private fun createPerformanceLogReport(perfLogID: Int, date: ByteArray?): Performance_log_report? {
        val id = Perf_log_id_e.fromValue(perfLogID)
        var time = (System.currentTimeMillis() / 1000).toInt()
        var ter_perf_log: Ter_perf_log? = null
        logd("createPerformanceLogReport() date=$date, id=$id")
        if (date == null) {
            loge("createPerformanceLogReport date = null")
            return null
        }
        when (id) {
            Perf_log_id_e.PERF_LOG_SSIM_CONN_FAIL -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .ssim_conn_fail(Ssim_EstFail.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_SSIM_CONN_SUCC -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .ssim_conn_succ(Ssim_EstSucc.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_SSIM_LOGIN -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .ssim_login(Ssim_Login.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TRANS_VSIM_FILE -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_trans_file(Vsim_ResAllo.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_VSIM_CONN_FAIL -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_conn_fail(Vsim_EstFail.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_VSIM_CONN_SUCC -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_conn_succ(Vsim_EstSucc.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_VSIM_INTER_HO -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_ho(Vsim_InterHO.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_VSIM_MR -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_mr(Vsim_MR.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_VSIM_CONN_RELEASE -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_rel(Ter_conn_rel.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_CONN_DELAY -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .vsim_delay(Vsim_DelayStat.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_WIFI_USER_CHANGE -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .user_change(Wifi_user_change.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TER_POWER_ON -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .poweron(Ter_power_on.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TER_POWER_OFF -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .poweroff(Ter_power_off.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_ACCESS_ABNORMAL_A -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .access_ab_a(Ter_access_abnormalA.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_ACCESS_ABNORMAL_B -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .access_ab_b(Ter_access_abnormalB.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_ACCESS_NORMAL -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .access_normal(Ter_access_normal.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_FOTA_UPGRADE_SUCC -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .fota_upgrade_succ(Ter_fota_upgrade_succ.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_FOTA_UPGRADE_FAIL -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .fota_upgrade_fail(Ter_fota_upgrade_fail.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_FOTA_DOWNLOAD_FILE -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .fota_file_download(Ter_fota_file_download.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TER_DATA_EVENT -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .data_event(Ter_data_event.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TER_SCREEN_EVENT -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .screen_event(Ter_screen_event.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TER_BIG_CYCLE -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .big_cycle(Ter_big_cycle.ADAPTER.decode(date))
                        .build()
            }
            Perf_log_id_e.PERF_LOG_TER_SOFT_PHY_SWICTH -> {
                ter_perf_log = Ter_perf_log.Builder().logid(id)
                        .upload_time(time)
                        .seq_id(seq_id++)
                        .soft_phy_switch(Ter_soft_phy_swicth.ADAPTER.decode(date))
                        .build()
            }
        }
        if (ter_perf_log != null) {
            val byteArray = ter_perf_log.encode()
            val req = Performance_log_report(ByteString.of(byteArray, 0, byteArray.size))
            return req
        }
        loge("createPerformanceLogReport ter_perf_log = null")
        return null
    }


    /**
     * 保存事件到arrayList
     */
    fun saveEventToList(any: Any) {
        logd("saveEventToList eventList.size=${eventList.size}, event=$any")
        if (eventList.size >= MAX_SAVE_COUNT) {
            eventListFreq.removeAt(0)
        }
        eventList.add(any)
    }

    /**
     * 保存频繁触发的事件到arrayList
     */
    fun saveFreqEventToList(any: Any) {
        logd("saveFreqEventToList eventListFreq.size=${eventList.size}, event=$any")
        if (eventListFreq.size >= FREQ_MAX_SAVE_COUNT) {
            eventListFreq.removeAt(0)
        }
        eventListFreq.add(any)
    }

    /**
     * 将 eventList eventListFreq 中事件保存到database
     *达到以下条件即触发：
     * 1,云卡成功 保存
     * 2,云卡起来后 60分钟保存一次
     * 4，云卡停止 保存
     */
    fun saveEventToDatabase() {
        logd("saveEventToDatabase eventList.size=${eventList.size} eventListfreq.size=${eventListFreq.size}")
        val copyList = eventList.clone() as ArrayList<Any>
        eventList.clear()
        val copyListFreq = eventListFreq.clone() as ArrayList<Any>
        eventListFreq.clear()

        //保存非频繁触发的事件
        val iterator = copyList.iterator();
        while (iterator.hasNext()) {
            val event = iterator.next();
            logd("saveEventToDatabase  event=$event")
            var byteArray: ByteArray? = null
            var id = -1
            var isfreq = 0
            if (event is Ter_big_cycle) {
                id = Perf_log_id_e.PERF_LOG_TER_BIG_CYCLE.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ter_data_event) {
                id = Perf_log_id_e.PERF_LOG_TER_DATA_EVENT.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ter_power_off) {
                id = Perf_log_id_e.PERF_LOG_TER_POWER_OFF.value
                byteArray = event.encode()
            } else if (event is Ter_power_on) {
                id = Perf_log_id_e.PERF_LOG_TER_POWER_ON.value
                byteArray = event.encode()
            } else if (event is Ter_screen_event) {
                id = Perf_log_id_e.PERF_LOG_TER_SCREEN_EVENT.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ter_soft_phy_swicth) {
                id = Perf_log_id_e.PERF_LOG_TER_SOFT_PHY_SWICTH.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ssim_EstFail) {
                id = Perf_log_id_e.PERF_LOG_SSIM_CONN_FAIL.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ssim_EstSucc) {
                id = Perf_log_id_e.PERF_LOG_SSIM_CONN_SUCC.value
                byteArray = event.encode()
            } else if (event is Ssim_Login) {
                id = Perf_log_id_e.PERF_LOG_SSIM_LOGIN.value
                byteArray = event.encode()
            } else if (event is Ter_access_normal) {
                id = Perf_log_id_e.PERF_LOG_ACCESS_NORMAL.value
                byteArray = event.encode()
            } else if (event is Ter_access_abnormalA) {
                id = Perf_log_id_e.PERF_LOG_ACCESS_ABNORMAL_A.value
                byteArray = event.encode()
            } else if (event is Ter_access_abnormalB) {
                id = Perf_log_id_e.PERF_LOG_ACCESS_ABNORMAL_B.value
                byteArray = event.encode()
            } else if (event is Ter_conn_rel) {
                id = Perf_log_id_e.PERF_LOG_VSIM_CONN_RELEASE.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_DelayStat) {
                id = Perf_log_id_e.PERF_LOG_CONN_DELAY.value
                byteArray = event.encode()
            } else if (event is Vsim_EstFail) {
                id = Perf_log_id_e.PERF_LOG_VSIM_CONN_FAIL.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_EstSucc) {
                id = Perf_log_id_e.PERF_LOG_VSIM_CONN_SUCC.value
                byteArray = event.encode()
            } else if (event is Vsim_InterHO) {
                id = Perf_log_id_e.PERF_LOG_VSIM_INTER_HO.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_MR) {
                id = Perf_log_id_e.PERF_LOG_VSIM_MR.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_ResAllo) {
                id = Perf_log_id_e.PERF_LOG_TRANS_VSIM_FILE.value
                byteArray = event.encode()
            } else if (event is Wifi_user_change) {
                id = Perf_log_id_e.PERF_LOG_WIFI_USER_CHANGE.value
                isfreq = 1
                byteArray = event.encode()
            } else {
                loge("saveEventToDatabase  event error ")
                return
            }

            if (byteArray != null && byteArray.isNotEmpty()) {
                val contentValues = ContentValues()
                contentValues.put(SqliteHelper.PerfLogEntry.COLUMN_NAME_TYPE, id)
                contentValues.put(SqliteHelper.PerfLogEntry.COLUMN_NAME_RETRYCOUNT, 0)
                contentValues.put(SqliteHelper.PerfLogEntry.IS_FREQ_EVENT, isfreq)
                contentValues.put(SqliteHelper.PerfLogEntry.COLUMN_NAME_DATA, byteArray)
                val result = DBHelper.instance().insert(SqliteHelper.PerfLogEntry.TABLE_NAME, contentValues)
                logd("---------insert---------------- result =$result")
            } else {
                loge("saveEventToDatabase byteArray = null or empty")
            }
        }

        //删除多余的非频繁触发的事件
        val count = DBHelper.instance().getAllCaseNum(SqliteHelper.PerfLogEntry.TABLE_NAME, 0)
        logd("saveEventToDatabase count=$count")
        DBHelper.instance().delCase(SqliteHelper.PerfLogEntry.TABLE_NAME, 0, MAX_SAVE_COUNT)
        val count2 = DBHelper.instance().getAllCaseNum(SqliteHelper.PerfLogEntry.TABLE_NAME, 0)
        logd("saveEventToDatabase count2=$count2")

        //保存频繁触发的事件
        val iteratorFreq = copyListFreq.iterator();
        while (iteratorFreq.hasNext()) {
            val event = iteratorFreq.next();
            logd("saveFreqEventToDatabase  event=$event")
            var byteArray: ByteArray? = null
            var id = -1
            var isfreq = 0
            if (event is Ter_big_cycle) {
                id = Perf_log_id_e.PERF_LOG_TER_BIG_CYCLE.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ter_data_event) {
                id = Perf_log_id_e.PERF_LOG_TER_DATA_EVENT.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ter_power_off) {
                id = Perf_log_id_e.PERF_LOG_TER_POWER_OFF.value
                byteArray = event.encode()
            } else if (event is Ter_power_on) {
                id = Perf_log_id_e.PERF_LOG_TER_POWER_ON.value
                byteArray = event.encode()
            } else if (event is Ter_screen_event) {
                id = Perf_log_id_e.PERF_LOG_TER_SCREEN_EVENT.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ter_soft_phy_swicth) {
                id = Perf_log_id_e.PERF_LOG_TER_SOFT_PHY_SWICTH.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ssim_EstFail) {
                id = Perf_log_id_e.PERF_LOG_SSIM_CONN_FAIL.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Ssim_EstSucc) {
                id = Perf_log_id_e.PERF_LOG_SSIM_CONN_SUCC.value
                byteArray = event.encode()
            } else if (event is Ssim_Login) {
                id = Perf_log_id_e.PERF_LOG_SSIM_LOGIN.value
                byteArray = event.encode()
            } else if (event is Ter_access_normal) {
                id = Perf_log_id_e.PERF_LOG_ACCESS_NORMAL.value
                byteArray = event.encode()
            } else if (event is Ter_access_abnormalA) {
                id = Perf_log_id_e.PERF_LOG_ACCESS_ABNORMAL_A.value
                byteArray = event.encode()
            } else if (event is Ter_access_abnormalB) {
                id = Perf_log_id_e.PERF_LOG_ACCESS_ABNORMAL_B.value
                byteArray = event.encode()
            } else if (event is Ter_conn_rel) {
                id = Perf_log_id_e.PERF_LOG_VSIM_CONN_RELEASE.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_DelayStat) {
                id = Perf_log_id_e.PERF_LOG_CONN_DELAY.value
                byteArray = event.encode()
            } else if (event is Vsim_EstFail) {
                id = Perf_log_id_e.PERF_LOG_VSIM_CONN_FAIL.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_EstSucc) {
                id = Perf_log_id_e.PERF_LOG_VSIM_CONN_SUCC.value
                byteArray = event.encode()
            } else if (event is Vsim_InterHO) {
                id = Perf_log_id_e.PERF_LOG_VSIM_INTER_HO.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_MR) {
                id = Perf_log_id_e.PERF_LOG_VSIM_MR.value
                isfreq = 1
                byteArray = event.encode()
            } else if (event is Vsim_ResAllo) {
                id = Perf_log_id_e.PERF_LOG_TRANS_VSIM_FILE.value
                byteArray = event.encode()
            } else if (event is Wifi_user_change) {
                id = Perf_log_id_e.PERF_LOG_WIFI_USER_CHANGE.value
                isfreq = 1
                byteArray = event.encode()
            } else {
                loge("saveFreqEventToDatabase  event error ")
                return
            }

            if (byteArray != null && byteArray.isNotEmpty()) {
                val contentValues = ContentValues()
                contentValues.put(SqliteHelper.PerfLogEntry.COLUMN_NAME_TYPE, id)
                contentValues.put(SqliteHelper.PerfLogEntry.COLUMN_NAME_RETRYCOUNT, 0)
                contentValues.put(SqliteHelper.PerfLogEntry.IS_FREQ_EVENT, isfreq)
                contentValues.put(SqliteHelper.PerfLogEntry.COLUMN_NAME_DATA, byteArray)
                val result = DBHelper.instance().insert(SqliteHelper.PerfLogEntry.TABLE_NAME, contentValues)
                logd("---------insert Freq---------------- result =$result")
            } else {
                loge("saveFreqEventToDatabase byteArray = null or empty")
            }
        }

        //删除多余的频繁触发的事件
        val countFreq = DBHelper.instance().getAllCaseNum(SqliteHelper.PerfLogEntry.TABLE_NAME, 1)
        logd("saveEventToDatabase countFreq=$countFreq")
        DBHelper.instance().delCase(SqliteHelper.PerfLogEntry.TABLE_NAME, 1, FREQ_MAX_SAVE_COUNT)
        val countFreq2 = DBHelper.instance().getAllCaseNum(SqliteHelper.PerfLogEntry.TABLE_NAME, 1)
        logd("saveEventToDatabase countFreq2=$countFreq2")
    }

    fun getMobileNetInfo(isSeed: Boolean, simInfoData: SimInfoData): Mobile_net {
        val slot = if (isSeed) Configuration.seedSimSlot else Configuration.cloudSimSlot
        val netInfo = getNetInfo(slot)
        var mcc = "000"
        var mnc = "00"
        if (netInfo.mccmnc.length >= 5) {
            mcc = netInfo.mccmnc.substring(0, 3)
            mnc = netInfo.mccmnc.substring(3, 5)
        } else {
            val seedMccmnc = OperatorNetworkInfo.mccmnc
            val cloudMccMnc = OperatorNetworkInfo.mccmncCloudSim
            if (isSeed && seedMccmnc.length >= 5) {
                mcc = seedMccmnc.substring(0, 3)
                mnc = seedMccmnc.substring(3, 5)
            } else if(!isSeed && cloudMccMnc.length >= 5) {
                mcc = cloudMccMnc.substring(0, 3)
                mnc = cloudMccMnc.substring(3, 5)
            }
        }
        val netPos = Mobile_net_pos.Builder().mcc(mcc).mnc(mnc).build()
        val signal = Mobile_signal.Builder().sig_quality(netInfo.signal_quality).sig_strength(netInfo.signal_strength).build()

        val ps_reg = kotlin.run {
            if (simInfoData.dataReg) {
                if (simInfoData.dataRoam) {
                    return@run Reg_status_e.REG_STATUS_ROAMING
                } else {
                    return@run Reg_status_e.REG_STATUS_HOME
                }
            } else if (simInfoData.dataDeny) {
                return@run Reg_status_e.REG_STATUS_DENIED
            } else {
                return@run Reg_status_e.REG_STATUS_NONE
            }
        }
        val cs_reg = kotlin.run {
            if (simInfoData.voiceReg) {
                if (simInfoData.voiceRoam) {
                    return@run Reg_status_e.REG_STATUS_ROAMING
                } else {
                    return@run Reg_status_e.REG_STATUS_HOME
                }
            } else if (simInfoData.voiceDeny) {
                return@run Reg_status_e.REG_STATUS_DENIED
            } else {
                return@run Reg_status_e.REG_STATUS_NONE
            }
        }
        var rat = netInfo.rat
        if (Net_type_e.fromValue(rat) == Net_type_e.NET_TYPE_INVALID){
            if (isSeed){
                rat = operatorRatToPerfRat(OperatorNetworkInfo.rat)
            }else{
                rat = operatorRatToPerfRat(OperatorNetworkInfo.ratCloudSim)
            }
        }

        val reg = Mobile_reg_status.Builder().cs_reg_status(cs_reg).ps_reg_status(ps_reg).build()
        val cell = Net_cell.Builder().cellid(netInfo.cellid).lacid(netInfo.lac).build()
        val net = Mobile_net.Builder().net_pos(netPos)
                .rat(Net_type_e.fromValue(rat))
                .band(netInfo.band)
                .rssi(netInfo.rssi)
                .signal(signal)
                .reg(reg)
                .cell(cell)
                .build()
        return net
    }

    // TODO:  PerfLogSsimEstFail 事件 错误码不一样，要新增错误枚举类型
    fun getPerfSsimErrType(exception: EnablerException?): Int {
        if (exception == null) {
            return Ssim_est_fail_errtype.SSIM_EST_FAIL_UNKNOWN.value
        }
        when (exception) {
            EnablerException.EXCEPTION_REG_DENIED -> {
                return Ssim_est_fail_errtype.SSIM_EST_FAIL_NETWORK_ATTACH_REJECT.value
            }
            EnablerException.EXCEPTION_CARD_NET_FAIL -> {
                return Ssim_est_fail_errtype.SSIM_EST_FAIL_CONN_SOCKET_TIMEOUT.value
            }
            else -> {
                return Ssim_est_fail_errtype.SSIM_EST_FAIL_UNKNOWN.value
            }

        }
    }

    /**
     * 开机时间，单位：秒
     */
    fun getBootTime(): Int {
        return ((System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos() / 1000000) / 1000).toInt()
    }

    //获取运行时间：秒
    fun getBootTimeToNow(): Int {
        return (SystemClock.elapsedRealtimeNanos() / 1000000 / 1000).toInt()
    }

    //生成8位随机数
    fun getRandomNum(): String {
        val random = Random()
        var result = ""
        for (i in 0..8) {
            result += random.nextInt(10)
        }
        return result
    }

    /**
     * 获取ip地址
     */
    fun getIPAddress(context: Context): Int {
        val info = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).getActiveNetworkInfo()
        if (info != null && info!!.isConnected()) {
            if (info!!.getType() == ConnectivityManager.TYPE_MOBILE) {
                try {
                    val en = NetworkInterface.getNetworkInterfaces()
                    while (en.hasMoreElements()) {
                        val intf = en.nextElement()
                        val enumIpAddr = intf.getInetAddresses()
                        while (enumIpAddr.hasMoreElements()) {
                            val inetAddress = enumIpAddr.nextElement()
                            if (!inetAddress.isLoopbackAddress() && inetAddress is Inet4Address) {
                                val strIp = inetAddress.getHostAddress()
                                logd("perfip getIPAddress = $strIp")
                                return ipToInt(strIp)
                            }
                        }
                    }
                } catch (e: SocketException) {
                    e.printStackTrace()
                }
            }
        }
        return 0
    }

    fun ipToInt(strIp: String): Int {
        val ip = LongArray(4)
        val iplist = strIp.split(".")
        if (iplist.size != 4) {
            return 0
        }
        ip[0] = java.lang.Long.parseLong(iplist[0])
        ip[1] = java.lang.Long.parseLong(iplist[1])
        ip[2] = java.lang.Long.parseLong(iplist[2])
        ip[3] = java.lang.Long.parseLong(iplist[3])
        val logip = (ip[0] shl 24) + (ip[1] shl 16) + (ip[2] shl 8) + ip[3]
        logd("perfip ipToInt ,long = $logip ,int = ${logip.toInt()}")
        return logip.toInt()
    }


    /**
     * /** Network type is unknown */
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    /** Current network is GPRS */
    public static final int NETWORK_TYPE_GPRS = 1;
    /** Current network is EDGE */
    public static final int NETWORK_TYPE_EDGE = 2;
    /** Current network is UMTS */
    public static final int NETWORK_TYPE_UMTS = 3;
    /** Current network is CDMA: Either IS95A or IS95B*/
    public static final int NETWORK_TYPE_CDMA = 4;
    /** Current network is EVDO revision 0*/
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    /** Current network is EVDO revision A*/
    public static final int NETWORK_TYPE_EVDO_A = 6;
    /** Current network is 1xRTT*/
    public static final int NETWORK_TYPE_1xRTT = 7;
    /** Current network is HSDPA */
    public static final int NETWORK_TYPE_HSDPA = 8;
    /** Current network is HSUPA */
    public static final int NETWORK_TYPE_HSUPA = 9;
    /** Current network is HSPA */
    public static final int NETWORK_TYPE_HSPA = 10;
    /** Current network is iDen */
    public static final int NETWORK_TYPE_IDEN = 11;
    /** Current network is EVDO revision B*/
    public static final int NETWORK_TYPE_EVDO_B = 12;
    /** Current network is LTE */
    public static final int NETWORK_TYPE_LTE = 13;
    /** Current network is eHRPD */
    public static final int NETWORK_TYPE_EHRPD = 14;
    /** Current network is HSPA+ */
    public static final int NETWORK_TYPE_HSPAP = 15;
    /** Current network is GSM {@hide} */
    public static final int NETWORK_TYPE_GSM = 16;
    /** Current network is TD_SCDMA {@hide} */
    public static final int NETWORK_TYPE_TD_SCDMA = 17;
    /** Current network is IWLAN {@hide} */
    public static final int NETWORK_TYPE_IWLAN = 18;
     */
    fun getPerfRat(rat: Int): Net_type_e {
        when (rat) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_CDMA -> {
                return Net_type_e.NET_TYPE_2G
            }

            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> {
                return Net_type_e.NET_TYPE_3G
            }
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_LTE_CA -> {
                return Net_type_e.NET_TYPE_FDD_4G
            }
            else -> {
                return Net_type_e.NET_TYPE_INVALID
            }
        }
    }

    fun operatorRatToPerfRat(operatorRat:Int):Int{
        when(operatorRat){
            RAT_TYPE_CDMA ->{
                return Net_type_e.NET_TYPE_2G.value
            }
            RAT_TYPE_GSM  ->{
                return Net_type_e.NET_TYPE_2G.value
            }
            RAT_TYPE_WCDMA ->{
                return Net_type_e.NET_TYPE_3G.value
            }
            RAT_TYPE_LTE ->{
                return Net_type_e.NET_TYPE_FDD_4G.value
            }
        }
        return Net_type_e.NET_TYPE_INVALID.value
    }
}

data class SimInfoData(val dataReg: Boolean, val dataRoam: Boolean, val dataDeny: Boolean, val voiceReg: Boolean, val voiceRoam: Boolean, val voiceDeny: Boolean)

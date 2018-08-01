package com.ucloudlink.refact.business.frequentauth

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ucloudlink.framework.protocol.protobuf.Frequent_auth_detection_result_req
import com.ucloudlink.framework.protocol.protobuf.S2c_frequent_auth_detection_param
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.netcheck.*
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import java.util.*

/**
 * Created by haiping.liu on 2018/5/3.
 * 鉴权检测
 */
class FrequentDetection {
    private val FREQUENT_AUTH_PARAM = "frequent_auth_param"
    private val SP_END_DETECTION_TIME = "EndDetectionTime"

    private val apduList: ArrayList<Frequent_auth_detection_result_req> = ArrayList()
    private var isRatChange = false

    private val TYPE_SR = 1
    private val TYPE_TAU = 2
    private val TYPE_UNKONWN = 3

    /**
     * 停止检测
     */
    fun stopDetection() {
        logd("stopDetection()")
        apduList.clear()
        ServiceManager.appContext.deleteFile(FREQUENT_AUTH_PARAM)
    }

    /**
     * 是否需要频繁鉴权检测条件：配置表存在，检测这张卡 ，开关打开,没有超过检测时间
     */
     fun ifNeedDetection(): Boolean {
        val param = getFrequentAuthParam()
        if (param == null) {
            loge("ifNeedDetection : not need :  param == null")
            return false
        }

        val cloudsimImsi = ServiceManager.accessEntry.accessState.imis
        if (cloudsimImsi == null || !param.imsi.equals(cloudsimImsi)) {
            loge("ifNeedDetection : not need :  param.imsi != cloudsim imsi (${param.imsi},${cloudsimImsi})")
            return false
        }

        if (!param.fa_switch) {
            loge("ifNeedDetection : not need :  param.fa_switch = ${param.fa_switch}")
            return false
        }

        val endTime = SharedPreferencesUtils.getLong(ServiceManager.appContext, SP_END_DETECTION_TIME)
        val curTime = System.currentTimeMillis()
        if (curTime > endTime) {
            loge("ifNeedDetection  Time out ,not need ")
            return false
        }

        logd("ifNeedDetection = true")
        return true
    }

    /**
     * 判断是否需要获取鉴权检测配置表（配置表存在且未到检测时间不需获取）
     */
    fun ifNeedGetAuthParam(): Boolean {
        val param = getFrequentAuthParam()
        if (param == null) {
            logd("ifNeedGetAuthParam : param not exist!")
            return true
        }
        val endTime = SharedPreferencesUtils.getLong(ServiceManager.appContext, SP_END_DETECTION_TIME)
        val curTime = System.currentTimeMillis()
        if (curTime > endTime) {
            logd("ifNeedGetAuthParam : detection time is end ")
            return true
        }
        logd("ifNeedGetAuthParam : false")
        return false
    }

    /**
     * 保存鉴权相关info
     */
    fun saveApduInfo():Boolean{
        if (ifNeedDetection()) {
            logd("saveApduInfo(): save")
            savaApduInfoToList()
            return true
        }else{
            logd("saveApduInfo(): don't save")
            return false
        }
    }


    /**
     *  保存检测配置表,更新检测结束时间
     */
    fun saveFrequentAuthParam(s2c: S2c_frequent_auth_detection_param) {
        val oldParam = getFrequentAuthParam()
        if (oldParam != null && oldParam.equals(s2c)) {
            loge("saveFrequentAuthParam:same param don't save ($oldParam,$s2c)")
            //配置表相同时，也需更新检测结束时间
            val endDetectionTime = System.currentTimeMillis() + s2c.duration * 1000
            SharedPreferencesUtils.putLong(ServiceManager.appContext, SP_END_DETECTION_TIME, endDetectionTime)
            logd("saveFrequentAuthParam  update endDetectionTime=$endDetectionTime")
            return
        }
        logd("saveFrequentAuthParam:" + s2c)
        try {
            s2c.encode(ServiceManager.appContext.openFileOutput(FREQUENT_AUTH_PARAM, Context.MODE_PRIVATE))
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        //更新检测结束时间
        val endDetectionTime = System.currentTimeMillis() + s2c.duration * 1000
        SharedPreferencesUtils.putLong(ServiceManager.appContext, SP_END_DETECTION_TIME, endDetectionTime)
        logd("saveFrequentAuthParam  update endDetectionTime=$endDetectionTime")
    }

    fun getApduList():ArrayList<Frequent_auth_detection_result_req>{
        return apduList
    }

    /**
     * 保存鉴权相关信息
     */
    private fun savaApduInfoToList() {
        val cloudsimRat = OperatorNetworkInfo.ratCloudSim
        val apdutime = System.currentTimeMillis()
        if (apduList.size > 0) {
            val apdulast = apduList[apduList.lastIndex]
            if ((apdutime - apdulast.apdu_time) < 2 * 60 * 1000) {
                //减少误报，2分钟内只记录一个
                loge("savaApduInfoToList:  in 2min don't save!!!")
                return
            }
            if (cloudsimRat != apdulast.rat) {
                if (isRatChange) {
                    //制式切换过滤一次
                    isRatChange = false
                    logd("savaApduInfoToList rat change over， save!")
                } else {
                    //发生制式切换，不统计
                    isRatChange = true
                    loge("savaApduInfoToList: rat change don't save, before rat = $apdulast.rat ,now  = $cloudsimRat")
                    return
                }
            }
        }
        var sr_count = 0
        var tau_count = 0
        var tacNew = 0
        var tacOld = 0

        var type = TYPE_UNKONWN
        if (false){ // TOOD:需要通过platform接口调用
            //val authType = SprdApiInst.getInstance().getVsimApduCause(Configuration.cloudSimSlot)
            val authType = TYPE_UNKONWN
            logd("savaApduInfoToList authType=$authType")
            when(authType){
                1->{type = TYPE_UNKONWN}
                2->{type = TYPE_TAU}
                3->{type = TYPE_SR}
                4->{type = TYPE_UNKONWN}
                5->{type = TYPE_UNKONWN}
                6->{type = TYPE_SR}
                0->{type = TYPE_UNKONWN}
            }
        }else{
            val mApduInfoModem = getModemApduInfo()
            if (mApduInfoModem != null && mApduInfoModem.size == 4) {
                sr_count = mApduInfoModem[0]
                tau_count = mApduInfoModem[1]
                tacNew = mApduInfoModem[2]
                tacOld = mApduInfoModem[3]
            }
            val param = getFrequentAuthParam()
            if (param != null) {
                if (sr_count >= param.sr_count) {
                    type = TYPE_SR
                } else if (tau_count >= param.tau_count) {
                    type = TYPE_TAU
                }
            }
        }

        if(tacNew == 0){
            tacNew = OperatorNetworkInfo.lacCloudSim
        }

        val mApduInfoModem = getModemApduInfo()
        if (mApduInfoModem != null && mApduInfoModem.size == 4) {
            sr_count = mApduInfoModem[0]
            tau_count = mApduInfoModem[1]
            tacNew = mApduInfoModem[2]
            tacOld = mApduInfoModem[3]
        }
        if(tacNew == 0){
            tacNew = OperatorNetworkInfo.lacCloudSim
        }

        val param = getFrequentAuthParam()
        if (param != null) {
            if (sr_count >= param.sr_count) {
                type = TYPE_SR
            } else if (tau_count >= param.tau_count) {
                type = TYPE_TAU
            }
        }

        val cloudsimimsi = ServiceManager.accessEntry.accessState.imis
        val imei = Configuration.getImei(ServiceManager.appContext)
        val cloudsimPlmn = OperatorNetworkInfo.mccmncCloudSim
        val cloudsimCellid = OperatorNetworkInfo.cellidCloudSim
        val cloudsimCellidOld = OperatorNetworkInfo.cellidCloudSimOld
        val seedPlmn = OperatorNetworkInfo.mccmnc
        val seedRat = OperatorNetworkInfo.rat
        val seedLac = OperatorNetworkInfo.lac
        val seedCellid = OperatorNetworkInfo.cellid
        val apduInfo = Frequent_auth_detection_result_req.Builder()
                .imsi(cloudsimimsi)
                .imei(imei)
                .plmn(cloudsimPlmn)
                .rat(getServiceRat(cloudsimRat))
                .type(type)
                .cellid(cloudsimCellid)
                .cellid_old(cloudsimCellidOld)
                .tac(tacNew)
                .tac_old(tacOld)
                .sr_count(sr_count)
                .tau_count(tau_count)
                .apdu_time(apdutime)
                .extm_plmn(seedPlmn)
                .extm_rat(getServiceRat(seedRat))
                .extm_lac(seedLac)
                .extm_cellid(seedCellid)
                .build()
        logd("savaApduInfoToList to apduList:$apduInfo")
        apduList.add(apduInfo)
    }

    /**
     *  获取频繁鉴权配置表
     */
    private fun getFrequentAuthParam(): S2c_frequent_auth_detection_param? {
        var s2cParam: S2c_frequent_auth_detection_param?
        try {
            s2cParam = S2c_frequent_auth_detection_param.ADAPTER.decode(ServiceManager.appContext.openFileInput(FREQUENT_AUTH_PARAM))
        } catch (e: Exception) {
            e.printStackTrace()
            s2cParam = null
        }

        logd("getFAParam:" + s2cParam)
        return s2cParam
    }

    /**
     * 从modem获取鉴权信息
     *返回一个长度为4的数组：
     *      SR_count----Int[0]
     *      TAU_count---Int[1]
     *      tac_old-----Int[2]
     *      tac_new-----Int[3]
     */
    private fun getModemApduInfo(): IntArray? {
        val teleMnger = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val mSubscriptionManager = SubscriptionManager.from(ServiceManager.appContext)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(Configuration.cloudSimSlot)
        var subId = -1
        var freqInfo: IntArray? = null
        if (mSubscriptionInfo != null) {
            subId = mSubscriptionInfo.subscriptionId
        }
        if (subId > 0) {
            try {
                freqInfo = teleMnger.getFreqInfo(subId)
            } catch (e: Exception) {
                loge("getModemApduInfo error:$e")
            }catch (e1:NoSuchMethodError){
                loge("getModemApduInfo error:$e1")
            }

        } else {
            loge("getModemApduInfo error: mSubscriptionInfo=${mSubscriptionInfo} subId=${subId}")
        }
        if (freqInfo != null && freqInfo.size == 4) {
            logd("getModemApduInfo freqInfo=(${freqInfo[0]},${freqInfo[1]},${freqInfo[2]},${freqInfo[3]})")
        } else {
            loge("getModemApduInfo freqInfo = null or size != 4")
        }
        return freqInfo
    }

    private fun getServiceRat(rat:Int):Int{
        when(rat){
            RAT_TYPE_CDMA->{
                return 0
            }
            RAT_TYPE_GSM->{
                return 4
            }
            RAT_TYPE_WCDMA->{
                return 8
            }
            RAT_TYPE_LTE->{
                return 16
            }
        }
        return 16//默认4G
    }
}
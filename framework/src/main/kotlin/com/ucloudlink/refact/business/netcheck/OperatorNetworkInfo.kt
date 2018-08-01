package com.ucloudlink.refact.business.netcheck

import com.ucloudlink.framework.protocol.protobuf.PlmnInfo
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import java.util.*

/**
 * Created by chunjiao.li on 2016/7/27.
 */
object OperatorNetworkInfo {

    //版本号
    var version: String = ""
    //apdu card 卡信息
    var imei: String = ""
    //小区号
    var cellid: Int = -1
    //位置区码
    var lac: Int = -1
    var imsi: String = ""
    //SIM卡的卡号
    var iccid: String = ""
    //信号强度
    var signalStrength: Int = -1
    //制式代码0-2G; 1-3G; 2-4G
    var rat: Int = -1
    val MAX_PLMNLIST_NUM: Int = 10
    //运营商
    var mccmnc: String = ""
        set(value) {
            JLog.logi("mccmnc new:$value old:$mccmnc")
            field = value
        }
    //包含内容("mccmnc,signalStrength,rat") "46001,23,2"
    var sidList: ArrayList<CharSequence> = ArrayList()

    //CloudSim 卡信息
    var imeiCloudSim: String = ""
    var cellidCloudSim: Int = -1
        set(value) {
            if (value != cellidCloudSim) {
                cellidCloudSimOld = cellidCloudSim
            }
            field = value
        }
    //旧的小区号
    var cellidCloudSimOld: Int = -1
    var lacCloudSim: Int = -1
    var imsiCloudSim: String = ""
    var iccidCloudSim: String = ""

    var signalStrengthCloudSim: Int = -1
    var ratCloudSim: Int = -1
    var mccmncCloudSim: String = ""

    //包含内容(mcc,mnc,rat,signalStrength)(460,01,2,23)
    var seedPlmnList: ArrayList<PlmnInfo> = ArrayList()
    var cloudPlmnList: ArrayList<PlmnInfo> = ArrayList()

    fun setSeedSingalByLevel(level:Int)
    {
           if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_NONE)
               this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_NONE
           else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_POOR)
               this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_POOR
           else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_MODERATE)
               this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_MODERATE
           else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GOOD)
                this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GOOD
           else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT)
               this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GREAT
           else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT_MORE)
               this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GREAT_MORE
           else{
               loge("setSeedSingalByLevel unknown level="+level)
               this.signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_UNKNOWN
           }
    }

    fun setCloudSignalByLevel(level: Int)
    {
        if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_NONE)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_NONE
        else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_POOR)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_POOR
        else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_MODERATE)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_MODERATE
        else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GOOD)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GOOD
        else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GREAT
        else if (level == com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT_MORE)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GREAT_MORE
        else {
            loge("setCloudSignalByLevel unknown level=" + level)
            this.signalStrengthCloudSim = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_UNKNOWN
        }
    }

    fun clear() {
        this.imei = ""
        this.cellid = -1
        this.lac = -1
        this.imsi = ""
        this.iccid = ""
        this.signalStrength = -1
        this.rat = -1
        this.mccmnc = ""

        this.imeiCloudSim = ""
        this.cellidCloudSim = -1
        this.cellidCloudSimOld = -1
        this.lacCloudSim = -1
        this.imsiCloudSim = ""
        this.iccidCloudSim = ""
        this.signalStrengthCloudSim = -1
        this.ratCloudSim = -1
        this.mccmncCloudSim = ""

        synchronized(this.seedPlmnList) {
            this.seedPlmnList.clear()
        }
        synchronized(this.cloudPlmnList) {
            this.cloudPlmnList.clear()
        }
    }

    /**
     * 保存云卡当前注册的plmn list信息
     */
    fun reflashCloudPlmnList()
    {
        if (this.signalStrengthCloudSim >= 0 && this.ratCloudSim >= 0 && this.mccmncCloudSim != "") {

            //todo 网络频段参数不正确，暂时用0代替
            logd("mccmncCloudSim : $mccmncCloudSim")
            val plmnmb :PlmnInfo

            if(this.mccmncCloudSim.length == 5){
                plmnmb = PlmnInfo(this.mccmncCloudSim.substring(0, 3)+this.mccmncCloudSim.substring(3, 5), this.ratCloudSim, this.signalStrengthCloudSim, 0)

            }else if(this.mccmncCloudSim.length == 6){
                plmnmb = PlmnInfo(this.mccmncCloudSim.substring(0, 3)+this.mccmncCloudSim.substring(3, 6), this.ratCloudSim, this.signalStrengthCloudSim, 0)
            }else{
                logd("OperatorInfo reflashCloudPlmnList mccmnc err")
                return
            }

            synchronized(this) {
                if (this.cloudPlmnList.size == 0)
                    this.cloudPlmnList.add(plmnmb)
                else
                    this.cloudPlmnList.add(0, plmnmb)

                checkPlmnListNumExceed(MAX_PLMNLIST_NUM)
                logd("OperatorInfo reflashCloudPlmnList", "plmnmb:$plmnmb,->plmnList:$cloudPlmnList")
            }
        }
    }

    /**
     * 保存种子卡当前注册的Plmn信息
     * */
    fun reflashSeedPlmnList()
    {
        if (this.signalStrength >= 0 && this.rat >= 0 && this.mccmnc != "") {

            //todo 网络频段参数不正确，暂时用0代替
            logd("mccmnc : $mccmnc")

            val plmnmb :PlmnInfo

            logd("OperatorInfo reflashSeedPlmnList mccmnc.length:" + this.mccmnc.length + " " +this.mccmnc)
            if(this.mccmnc.length == 5){
                plmnmb = PlmnInfo(this.mccmnc.substring(0, 3)+this.mccmnc.substring(3, 5), this.rat, this.signalStrength, 0)
            }else if(this.mccmnc.length == 6){
                plmnmb = PlmnInfo(this.mccmnc.substring(0, 3)+this.mccmnc.substring(3, 6), this.rat, this.signalStrength, 0)
            }else{
                logd("OperatorInfo reflashSeedPlmnList mccmnc err")
                return
            }

            synchronized(this.seedPlmnList) {
                if (this.seedPlmnList.size == 0) {
                    this.seedPlmnList.add(plmnmb)
                } else {
                    this.seedPlmnList.add(0, plmnmb)
                }
                checkPlmnListNumExceed(MAX_PLMNLIST_NUM)

                val listTemp = StringBuilder()
                for (plmninfo in seedPlmnList) {
                    listTemp.append(plmninfo.toString())
                }
                logd("OperatorInfo reflashSeedPlmnList", "plmnmb:$plmnmb,->plmnList:${listTemp.toString()}")

                if (listTemp.length > 0) {
                    listTemp.delete(0, listTemp.length)
                }
            }
        }
    }

    /**
     * 保存搜网结果的Plmn List信息
     * */
    fun addScanNetworkPlmnList(plmnmb:PlmnInfo)
    {
        var index = 0
        synchronized(this.seedPlmnList) {
            for (plmn in seedPlmnList) {
                if (plmn.plmn == plmnmb.plmn && plmn.rat == plmnmb.rat && plmn.band == plmnmb.band) {
                    //如果存在则移除，然后插入到seedPlmnList第1个
                    this.seedPlmnList.removeAt(index)
                    logd("addScanNetworkPlmnList remove plmn=" + plmnmb + "exist in plmnlist" + plmn)
                    break;
                }
                index++
            }
            if (!seedPlmnList.contains(plmnmb))
            {
                if (this.seedPlmnList.size>1){
                    logd("addScanNetworkPlmnList plmn1=:"+plmnmb+"to index 1")
                    this.seedPlmnList.add(1,plmnmb)
                }else{
                    logd("addScanNetworkPlmnList plmn1=:"+plmnmb+"to end")
                    this.seedPlmnList.add(plmnmb)
                }
            }
        }

        checkPlmnListNumExceed(MAX_PLMNLIST_NUM)
        synchronized(this.seedPlmnList) {
            logd("addScanNetworkPlmnList all=" + this.seedPlmnList)
        }
    }

    /**
     * 限制sidList最大数量
     */
    private fun checkPlmnListNumExceed(LimitCount: Int) {

        synchronized(this.cloudPlmnList) {
            while (this.cloudPlmnList.size > LimitCount) {
                this.cloudPlmnList.removeAt(this.cloudPlmnList.size - 1)
            }
        }
        synchronized(this.seedPlmnList){
            while (this.seedPlmnList.size > LimitCount) {
                this.seedPlmnList.removeAt(this.seedPlmnList.size - 1)
            }
        }
    }

    fun getNetworkInfoBySlot(slot:String):NetInfo{
        var netinfo : NetInfo ? = null
        val sidListStr: ArrayList<CharSequence> =  ArrayList()
        val SID_SPLIT: String = ","

        if (slot.equals("seed")) {
            synchronized(this.seedPlmnList){
                for (plmn in seedPlmnList) {
                    sidListStr.add(plmn.plmn + SID_SPLIT + plmn.rssi + SID_SPLIT + plmn.rat)
                }
            }
            netinfo = NetInfo(rat = this.rat,imei = this.imei,cellid = this.cellid,
                                lac = this.lac,imsi = this.imsi,iccid = this.iccid,mccmnc = this.mccmnc,
                                version = this.version,sidList = sidListStr, signal = this.signalStrength)
        }
        else {
            synchronized(this.cloudPlmnList) {
                for (plmn in cloudPlmnList) {
                    sidListStr.add(plmn.plmn + SID_SPLIT + plmn.rssi + SID_SPLIT + plmn.rat)
                }
            }

            netinfo = NetInfo(rat = this.ratCloudSim,imei = this.imeiCloudSim,cellid = this.cellidCloudSim,
                                lac = this.lacCloudSim,imsi = this.imsiCloudSim, iccid = this.iccidCloudSim,
                                mccmnc = this.mccmncCloudSim, version = this.version,sidList = sidListStr,
                                signal = this.signalStrengthCloudSim)
        }

        return netinfo
    }

    fun getSeedPlmnListString(): ArrayList<String>{
        var seedPlmnListString: ArrayList<String> = ArrayList<String>()
        synchronized(this.seedPlmnList) {
            for (plmn in seedPlmnList) {
                //(mcc,mnc,rat,signalStrength)(460,01,2,23)
                logd("getSeedPlmnListString plmnStr ${plmn.plmn}")
                var plmnStr = plmn.plmn.subSequence(0, 3).toString() + "," + plmn.plmn.subSequence(3, 5).toString() + "," + plmn.rat + "," + plmn.rssi
                if (!seedPlmnListString.contains(plmnStr)) {
                    logd("getSeedPlmnListString plmnStr $plmnStr")
                    seedPlmnListString.add(plmnStr)
                }
            }
        }
        return seedPlmnListString
    }


    /**
     * 云卡的plmn值
     * */
    fun getCloudPlmnListString(): ArrayList<String>{
        var seedPlmnListString: ArrayList<String> = ArrayList<String>()
        synchronized(this.cloudPlmnList) {
            for (plmn in cloudPlmnList) {
                //(mcc,mnc,rat,signalStrength)(460,01,2,23)
                logd("getSeedPlmnListString plmnStr ${plmn.plmn}")
                var plmnStr = plmn.plmn.subSequence(0, 3).toString() + "," + plmn.plmn.subSequence(3, 5).toString() + "," + plmn.rat + "," + plmn.rssi
                if (!seedPlmnListString.contains(plmnStr)) {
                    logd("getSeedPlmnListString plmnStr $plmnStr")
                    seedPlmnListString.add(plmnStr)
                }
            }
        }

        return seedPlmnListString
    }
}

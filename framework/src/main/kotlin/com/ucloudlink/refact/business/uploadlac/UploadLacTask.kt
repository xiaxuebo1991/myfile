package com.ucloudlink.refact.business.uploadlac

import android.content.Context
import android.telephony.*
import com.ucloudlink.refact.Framework
import com.ucloudlink.refact.business.netcheck.NetInfo
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.framework.protocol.protobuf.upload_lac_change_type
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.Requestor
import java.util.*

/**
 * 上传lac、 cellid
 * Created by yongbin.qin on 2017/8/16.
 */

object UploadLacTask{

    var timer: Timer? = null
    val interval:Long = 60*1000  //时间间隔60秒
    var plmn:String? = ""
    var lac:Int = 0
    var cellid:Int? = 0

//    val RAT_REG_ON_2G = 0
//    val RAT_REG_ON_3G = 1
//    val RAT_REG_ON_4G = 2

    var lastTime: Long = 0L

    var myTask: MyTask? = null

    init {
        timer = Timer()
    }

    /**
     * 启动云卡成功调用
     */
    fun startTask(){
        JLog.logd(" startTask....")
        myTask?.cancel()
        myTask = MyTask()
        timer?.schedule(myTask, interval , interval)
    }

    fun stopTask(){
        JLog.logd(" stopTask....")
        myTask?.cancel()
    }

    fun uploadLacChange(){

        JLog.logd("uploadLacChange  ....")
        JLog.logd("uploadLacChange  process:${ServiceManager.accessEntry.systemPersent} ")
        if (ServiceManager.accessEntry.systemPersent == 100){
            val slot = Configuration.cloudSimSlot
            val teleMnger = NetworkManager.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val cellInfos:List<CellInfo>? = teleMnger.allCellInfo
            val info  = getCloudSimNetInfo(cellInfos)
            //val info  = OperatorNetworkInfo.getNetworkInfoBySlot("cloud")
            JLog.logd("uploadLacChange  newNetinfo:{${info.toString()}}")
            JLog.logd("uploadLacChange  oldLlac:${lac}")
            JLog.logd("uploadLacChange  isGreateThanMint :${isGreateThanMint()}")
            //云卡可用的前提下，lac或者cellid其中的一个发生变化，并且距离上次上报时间大于20秒则上报
            if (info != null){
                if ((info.lac != lac) && isGreateThanMint()){
                    JLog.logd("uploadLacChange")
                    val reaq  = upload_lac_change_type(plmn, info.mccmnc,lac,info.lac, cellid, info.cellid)
                    plmn = info.mccmnc
                    lac = info.lac
                    cellid = info.cellid
                    Requestor.requstUploadLacChangeType(reaq).subscribe(
                            {
                                JLog.logd("requstUploadLacChangeType succ")
                                lastTime = System.currentTimeMillis();
                            },
                            {
                                JLog.logd("requstUploadLacChangeType faild ")
                            }
                    )
                } else{
                    JLog.logd("uploadLacChange info?.lac == lac")
                }
            } else{
                JLog.logd(" uploadLacChange info == null")
            }

        } else{

        }
    }



    class MyTask: TimerTask(){
        override fun run() {
            JLog.logd(" MyTask process:${ServiceManager.accessEntry.getSystemPersent()} ")
            if (ServiceManager.accessEntry.getSystemPersent() == 100){

               //uploadLacChange()
            } else{
                stopTask()
            }

        }

    }

    fun getCloudSimNetInfo(cellInfos: List<CellInfo>?) : NetInfo?{
        val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//        val dds = SubscriptionManager.getDefaultDataSubId()
        val dds = ServiceManager.systemApi.getDefaultDataSubId()
        val slotId = SubscriptionManager.getPhoneId(dds)
        val configuration = Configuration
        val seedSimSlot = configuration.seedSimSlot
        val cloudSimSlot = configuration.cloudSimSlot
        val seedOperator = mPhone.getNetworkOperatorForPhone(seedSimSlot)
        val vsimOperator = mPhone.getNetworkOperatorForPhone(cloudSimSlot)

        JLog.logd("getCloudSimNetInfo  seedOperator:$seedOperator")
        JLog.logd("getCloudSimNetInfo  vsimOperator:$vsimOperator")

        if (cellInfos == null) {
            JLog.logd("cellinfos is null")
        } else {
            var i = 0
            while (i < cellInfos.size) {
                if (cellInfos[i].isRegistered) {
                    if (cellInfos[i] is CellInfoWcdma) {
                        val cellInfo = cellInfos[i] as CellInfoWcdma
                        val cellIdentity = cellInfo.cellIdentity
                        val cellSignalStrength = cellInfo.cellSignalStrength
                        val mcc = cellIdentity.mcc
                        val mnc = cellIdentity.mnc
                        val mccmnc = getMccmnc(mcc, mnc)
                        if (mccmnc != null) {
                            val cid = cellIdentity.cid
                            val lac = cellIdentity.lac
                            val level = cellSignalStrength.level
                            if (mccmnc == seedOperator) {
                                //setSeedResult(cid, lac, level, RAT_REG_ON_3G)
                            }
                            if (mccmnc == vsimOperator) {
                                //setVsimResult(cid, lac, level, RAT_REG_ON_3G)
                                val array = ArrayList<CharSequence>()
                                return NetInfo(com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA,"",cid,lac,"","","",level,mccmnc,array)
                            }
                        }
                        JLog.logd("getNetInfo CellInfoWcdma cellid:${cellid} ,lac:${lac}")
                    } else if (cellInfos[i] is CellInfoGsm) {
                        val cellInfo = cellInfos[i] as CellInfoGsm
                        val cellIdentity = cellInfo.cellIdentity
                        val cellSignalStrength = cellInfo.cellSignalStrength
                        val mcc = cellIdentity.mcc
                        val mnc = cellIdentity.mnc
                        val mccmnc = getMccmnc(mcc, mnc)
                        if (mccmnc != null) {
                            val cid = cellIdentity.cid
                            val lac = cellIdentity.lac
                            val level = cellSignalStrength.level
                            if (mccmnc == seedOperator) {
                                //setSeedResult(cid, lac, level, RAT_REG_ON_2G)
                            }
                            if (mccmnc == vsimOperator) {
                                //setVsimResult(cid, lac, level, RAT_REG_ON_2G)
                                val array = ArrayList<CharSequence>()
                                return NetInfo(com.ucloudlink.refact.business.netcheck.RAT_TYPE_GSM,"",cid,lac,"","","",level,mccmnc,array)
                            }
                        }
                        JLog.logd("getNetInfo CellInfoGsm cellid:${cellid} ,lac:${lac}")
                    } else if (cellInfos[i] is CellInfoLte) {
                        val cellInfo = cellInfos[i] as CellInfoLte
                        val cellIdentity = cellInfo.cellIdentity
                        val cellSignalStrength = cellInfo.cellSignalStrength
                        val mcc = cellIdentity.mcc
                        val mnc = cellIdentity.mnc
                        val mccmnc = getMccmnc(mcc, mnc)
                        if (mccmnc != null) {
                            val cid = cellIdentity.ci
                            val lac = cellIdentity.tac
                            val level = cellSignalStrength.level
                            if (mccmnc == seedOperator) {
                                //setSeedResult(cid, lac, level, RAT_REG_ON_4G)
                            }
                            if (mccmnc == vsimOperator) {
                                //setVsimResult(cid, lac, level, RAT_REG_ON_4G)
                                val array = ArrayList<CharSequence>()
                                return NetInfo(com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE,"",cid,lac,"","","",level,mccmnc,array)
                            }
                        }
                        JLog.logd("getNetInfo CellInfoLte cellid:${cellid} ,lac:${lac}")
                    } else if (cellInfos[i] is CellInfoCdma) {
                        val cellInfoCdma: CellInfoCdma = cellInfos[i] as CellInfoCdma
                        val cellSignalStrengthCdma: CellSignalStrengthCdma = cellInfoCdma.cellSignalStrength
                        //signalStrength = cellSignalStrengthCdma.level
                        val cellIdentityCdma = cellInfoCdma.cellIdentity
                        cellid = cellIdentityCdma.basestationId
                        lac = cellIdentityCdma.systemId
                        JLog.logd("getNetInfo CellInfoCdma cellid:${cellid} ,lac:${lac}")
                    }
                }
                i++

            }
        }
        return null
    }

    private fun getMccmnc(mcc: Int, mnc: Int): String? {
        if (mcc != Integer.MAX_VALUE && mnc != Integer.MAX_VALUE) {
            return String.format("%03d", mcc) + String.format("%02d", mnc)
        }
        return null
    }

    fun isGreateThanMint():Boolean{

        if (lastTime == 0L){
            return true
        }
        val currentTime = System.currentTimeMillis()
        if((currentTime - lastTime) >= interval){
            return true
        }
        return false
    }
}
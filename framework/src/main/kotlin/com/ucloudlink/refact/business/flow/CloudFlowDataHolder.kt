package com.ucloudlink.refact.business.flow

import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.SupplemenUf
import com.ucloudlink.framework.tasks.UploadFlowTask
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.access.struct.LoginInfo
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.utils.DateUtil
import com.ucloudlink.refact.utils.JLog
import okio.ByteString
import java.io.*
import java.util.*

/**
 * Created by jianguo.he on 2018/1/19.
 */
class CloudFlowDataHolder {

    var userUpAdd: Long = 0
    var userDownAdd: Long = 0
    var userUpTotal: Long = 0
    var userDownTotal: Long = 0
    var sysUpAdd:Long = 0
    var sysDownAdd:Long = 0
    var mobileUpAdd:Long = 0
    var mobileDownAdd:Long = 0


    fun initData() {
        userUpAdd = 0
        userDownAdd = 0
        userUpTotal = 0
        userDownTotal = 0
        sysUpAdd = 0
        sysDownAdd = 0
        mobileUpAdd = 0
        mobileDownAdd = 0
    }

    fun updateData(stats: StatsData?) :Int{
        var ret = 0
        if (stats != null) {
            userUpAdd     = stats.userUpIncr
            userUpTotal   = stats.userUp
            userDownTotal = stats.userDown
            userDownAdd   = stats.userDownIncr
            sysUpAdd      = stats.sysUpIncr
            sysDownAdd    = stats.sysDownIncr
            mobileUpAdd   = stats.userUpIncr   + stats.sysUpIncr
            mobileDownAdd = stats.userDownIncr + stats.sysDownIncr

        }else{
            ret = -1
            userUpAdd     = 0
            userUpTotal   = 0
            userDownTotal = 0
            userDownAdd   = 0
            sysUpAdd      = 0
            sysDownAdd    = 0
            mobileUpAdd   = 0
            mobileDownAdd = 0

        }
        return ret
    }


    fun saveLocalFlowStats(logId: Int, uploadLastTimeMills: Long): Int{
        val lStartTime:Long = uploadLastTimeMills
        val lEndTime:Long = System.currentTimeMillis()
        JLog.logi("CCFlow SupplyFlowLog saveLocalFlowStats(-) -> logId = $logId, uploadLastTime = $uploadLastTimeMills" +
                ", ${DateUtil.format_YYYY_MM_DD_HH_SS_SSS(lStartTime)}, ${DateUtil.format_YYYY_MM_DD_HH_SS_SSS(lEndTime)}")
        var dos: DataOutputStream?= null

        val accessEntry = ServiceManager.accessEntry
        val loginInfo: LoginInfo? = if(accessEntry==null) null else accessEntry.loginInfo

        val strSessionid: String = if(accessEntry==null) "" else accessEntry.getCurSessionId()
        val strImis: String =  if(accessEntry==null) "" else accessEntry.getCurImis()
        var strUc: String   = if(loginInfo==null) "" else loginInfo.username
        if(TextUtils.isEmpty(strUc)){
            try{
                strUc = RunningStates.getUserName()
            }catch (e: Exception){
                JLog.logi("CCFlow SupplyFlowLog saveLocalFlowStats -> getUserName -> Exception: "+e.toString())
                e.printStackTrace()
            }
        }

        val strPlmn: String  = OperatorNetworkInfo.mccmnc

        JLog.logi("CCFlow SupplyFlowLog saveLocalFlowStats -> strSessionid = $strSessionid, strImis = $strImis, strUc = $strUc, strPlmn = $strPlmn"
                +", accessEntry:" + (if(accessEntry==null) " null" else " not null")
                + ", loginInfo: " + (if(loginInfo==null) " null" else " not null"))

        try {
            /*
            * (strSessionid, strUc, ilogid, strImis, lStartTime, lEndTime, lUserTx,
            *                lUserRx, lSysTx, lSysRx, strPlmn, iLac, iCid, dLongitude, dLatitude, ByteString.EMPTY)
            * */
            JLog.logi("CCFlow SupplyFlowLog saveLocalFlowStats -> DataPath:" + UploadFlowTask.flowStatsSavePath)
            dos = DataOutputStream (BufferedOutputStream (FileOutputStream (UploadFlowTask.flowStatsSavePath,true)))
            dos.writeUTF(strSessionid)
            dos.writeUTF(strUc)
            dos.writeInt(logId)
            dos.writeUTF(strImis)
            dos.writeLong(lStartTime)
            dos.writeLong(lEndTime)
            dos.writeLong(userUpAdd)
            dos.writeLong(userDownAdd)
            dos.writeLong(sysUpAdd)
            dos.writeLong(sysDownAdd)
            dos.writeUTF(strPlmn)
            dos.writeInt(OperatorNetworkInfo.lac)
            dos.writeInt(OperatorNetworkInfo.cellid)
            dos.writeDouble(0.0)
            dos.writeDouble(0.0)
            JLog.logi("CCFlow SupplyFlowLog saveLocalFlowStats -> Save Stats: ${logId}, $strUc, $strSessionid, $strImis, $strPlmn, $lStartTime, $lEndTime, $userUpAdd, $userDownAdd, $sysUpAdd, $sysDownAdd")
        } catch (e: IOException){
            JLog.loge("CCFlow SupplyFlowLog saveLocalFlowStats -> save localFlowStats Exception:$e")
        } finally {
            if (dos != null) {
                try {
                    dos.close()
                } catch(e: IOException) {
                    JLog.logd("CCFlow SupplyFlowLog saveLocalFlowStats -> close localFlowStats File Exception:$e")
                }
            }
        }
        return 0
    }

    fun getListLocalCacheFlowStats(): ArrayList<SupplemenUf> {
        var dis: DataInputStream?= null
        val flowSupplement : ArrayList<SupplemenUf> = ArrayList()

        JLog.logi("CCFlow SupplyFlowLog try to readLocalCacheFlowStats:${UploadFlowTask.flowStatsSavePath}")
        val fl = File(UploadFlowTask.flowStatsSavePath)

        if (!fl.exists()){
            JLog.logi("CCFlow SupplyFlowLog read LocalCacheFlowStats does not exist.")
            return flowSupplement
        }

        try{
            dis = DataInputStream (BufferedInputStream (FileInputStream (UploadFlowTask.flowStatsSavePath)))
            while(dis != null) {
                val strSessionid: String = dis.readUTF()
                val strUc: String = dis.readUTF()
                val ilogid: Int = dis.readInt()
                val strImsi: String = dis.readUTF()
                val lStartTime: Long = dis.readLong()
                val lEndTime: Long = dis.readLong()
                val lUserTx: Long = dis.readLong()
                val lUserRx: Long = dis.readLong()
                val lSysTx: Long = dis.readLong()
                val lSysRx: Long = dis.readLong()
                val strPlmn: String = dis.readUTF()
                val iLac:Int = dis.readInt()
                val iCid:Int = dis.readInt()
                val dLongitude:Double = dis.readDouble()
                val dLatitude:Double = dis.readDouble()

                JLog.logi("CCFlow SupplyFlowLog Read local Stats: $ilogid, $strUc, $strSessionid, ${strImsi}, $strPlmn, $lStartTime, $lEndTime, $lUserTx, " +
                        "$lUserRx, $lSysTx, $lSysRx, ${DateUtil.format_YYYY_MM_DD_HH_SS_SSS(lStartTime)}, ${DateUtil.format_YYYY_MM_DD_HH_SS_SSS(lEndTime)}")

                var imsi: Long = 0
                if (!checkValidImsi(strImsi)) {
                    JLog.logi("CCFlow SupplyFlowLog read invalid record imsilen:" + strImsi.length)
                    throw Exception("invalid imsi $strImsi")
                }

                JLog.logi("CCFlow SupplyFlowLog read strImsi:" + if(strImsi==null) "null" else strImsi)
                try{
                    imsi = strImsi.toLong()
                } catch (e: Exception){
                    JLog.loge("CCFlow SupplyFlowLog read imsi string to long Exception. Exception: "+e.toString())
                }


                val flowSup = SupplemenUf(strSessionid, strUc, ilogid, imsi, lStartTime, lEndTime, lUserTx,
                        lUserRx, lSysTx, lSysRx, strPlmn, iLac, iCid, dLongitude, dLatitude, ByteString.EMPTY)

                flowSupplement.add(flowSup)
            }
            JLog.loge("CCFlow SupplyFlowLog read to eof savedFlowStats file. result: "+flowSupplement.toString())
        }catch(e: Exception) {
            JLog.loge("CCFlow SupplyFlowLog read to eof savedFlowStats file. Exception: "+e.toString())
            try {
                if (dis != null) {
                    dis.close()
                    dis = null
                }
            } catch(e: IOException) {
                JLog.loge("CCFlow SupplyFlowLog close read local save CloudFlowCtrl error.")
            } finally {
                val file = File(UploadFlowTask.flowStatsSavePath)
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            }

        } finally {
            try {
                if (dis != null) {
                    dis.close()
                }
            } catch(e: IOException) {
                JLog.loge("CCFlow SupplyFlowLog close read local save CloudFlowCtrl error.")
            }
        }

        return flowSupplement
    }


    fun checkValidImsi(imsi: String?): Boolean {
        if (imsi == null) {
            return false
        }
        JLog.logd("virtImei", "virtImei length is: " + imsi)
        if (imsi.length > 15) {
            return false
        }
        if (isAllZero(imsi)) {
            return false
        }

        for (c in imsi.toCharArray()) {
            if (c < '0' || c > '9') {
                return false
            }
        }
        return true
    }

    private fun isAllZero(imsi: String): Boolean {
        return ((imsi.length == 1) && (imsi == "0"))
                || ((imsi.length == 2) && (imsi == "00"))
                || ((imsi.length == 3) && (imsi == "000"))
                || ((imsi.length == 4) && (imsi == "0000"))
                || ((imsi.length == 5) && (imsi == "00000"))
                || ((imsi.length == 6) && (imsi == "000000"))
                || ((imsi.length == 7) && (imsi == "0000000"))
                || ((imsi.length == 8) && (imsi == "00000000"))
                || ((imsi.length == 9) && (imsi == "000000000"))
                || ((imsi.length == 10) && (imsi == "0000000000"))
                || ((imsi.length == 11) && (imsi == "00000000000"))
                || ((imsi.length == 12) && (imsi == "000000000000"))
                || ((imsi.length == 13) && (imsi == "0000000000000"))
                || ((imsi.length == 14) && (imsi == "00000000000000"))
                || ((imsi.length == 15) && (imsi == "000000000000000"))
    }
}
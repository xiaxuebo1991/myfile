package com.ucloudlink.refact.product.mifi.seedUpdate.utils

import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.SoftsimInfo
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.softsim.CardRepository
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.Configuration.RULE_FILE_NAME
import com.ucloudlink.refact.product.mifi.ExtSoftsimDB
import com.ucloudlink.refact.utils.FileIOUtils
import com.ucloudlink.refact.utils.FileUtils
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import java.io.File
import java.util.*

/**
 * 提供软卡备份与回复
 */
object SoftSimDataBackup {
    private const val ROOT_DIR = "/productinfo/sofsimbackup"
    private const val SIM_INFO_FILE_NAME = "siminfo.bin"
    private const val RULE_FILE_PATH = "$ROOT_DIR/$RULE_FILE_NAME"

    private fun getSimDir(imsi: String) = "$ROOT_DIR/$imsi"
    private fun getSimInfoFile(imsi: String) = File("${getSimDir(imsi)}/$SIM_INFO_FILE_NAME")

    /**
     * 在安全分区(productinfo/)保存以下数据
    softsimbackup
    rulefile    规则文件
    123456789012345(imsi)
    plmnbin
    feebin
    fplmnbin
    ...
     */
    fun backup(simInfos: Array<SoftsimInfo>) {
        logd("[backup] size = ${simInfos.size}")
        simInfos.forEach { simInfo ->
            backupSimInfo(simInfo)
        }
    }

    /**
     * 保存simInfo
     */
    private fun backupSimInfo(simInfo: SoftsimInfo) {
        var res: Boolean
        val imsi = simInfo.imsi.toString()
        logd("[backup] imsi = $imsi")

        val file = getSimInfoFile(imsi)
        val bytes = simInfo.encode()
        res = FileIOUtils.writeFileFromBytesByStream(file, bytes)
        logd("[backup] siminfo data: ${Arrays.toString(bytes)} to $file, $res")

        // backup bins
        val plmnBinRef = simInfo.plmnBinRef
        val srcPlmnBinFile = File(Configuration.simDataDir, "00001${plmnBinRef.substring(plmnBinRef.length - 12, plmnBinRef.length)}.bin")
        val destPlmnBinFile = File(getSimDir(imsi), plmnBinRef)
        res = FileUtils.copyFile(srcPlmnBinFile, destPlmnBinFile)
        logd("[backup] plmn bin ref from $srcPlmnBinFile to $destPlmnBinFile, $res")

        // backup fplmn
        val fplmnRef = simInfo.fplmnRef
        if (fplmnRef.isNotEmpty()) {
            val srcfplmnBinFile = File(Configuration.RULE_FILE_DIR, fplmnRef)
            val destfplmnBinFile = File(getSimDir(imsi), fplmnRef)
            res = FileUtils.copyFile(srcfplmnBinFile, destfplmnBinFile)
            logd("[backup] fplmn from $srcfplmnBinFile to $destfplmnBinFile, $res")
        } else {
            logd("[backup] fplmn failed: no fplmn ref.")
        }

        // back fee bin
        val feeBinRef = simInfo.feeBinRef
        if (feeBinRef.isNotEmpty()) {
            val srcfeeBinFile = File(Configuration.RULE_FILE_DIR, feeBinRef)
            val destfeeBinFile = File(getSimDir(imsi), feeBinRef)
            res = FileUtils.copyFile(srcfeeBinFile, destfeeBinFile)
            logd("[backup] fee bin from $srcfeeBinFile to $destfeeBinFile, $res")
        }
    }

    /**
     * 删除对应的back文件
     */
    fun deleteBackup(imsis: Array<String>) {
        imsis.forEach { imsi ->
            val ret = FileUtils.deleteDir(File(getSimDir(imsi)))
            logd("[backup] delete backup file $imsi, $ret")
            if (!ret) {
                loge("[backup] delete backup file failed!")
                // TODO: 确认删除失败的操作
                FileUtils.deleteDir(File(getSimDir(imsi)))
            }
        }
    }

    /**
     * 恢复数据
     */
    fun recover() {
        //get backup data
        val rootDir = File(ROOT_DIR)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return
        }
        rootDir.listFiles().forEach { file ->
            logd("[recover] file:${file.absolutePath}")
            if (file.name.contains(RULE_FILE_NAME)) {
                //recover rule file
                val destPath = File(Configuration.RULE_FILE_DIR, Configuration.RULE_FILE_NAME)
                val srcPath = File(RULE_FILE_PATH)
                if (srcPath.exists()) {
                    FileUtils.copyFile(srcPath, destPath)
                }
            } else {
                //recover simInfo
                val imsi = file.name
                val simInfoFile = getSimInfoFile(imsi)
                logd("[recover] simInfoFile:${simInfoFile.absolutePath}")
                val bytes = FileIOUtils.readFile2BytesByStream(simInfoFile)
                if (bytes != null && bytes.isNotEmpty()) {
                    val simInfo = SoftsimInfo.ADAPTER.decode(bytes)
                    val builder = simInfo.newBuilder()
                    builder.ki = ""
                    builder.opc = ""
                    val simInfoNoKi = builder.build()
                    ExtSoftsimDB(ServiceManager.appContext).updataExtSoftsimDb(simInfoNoKi)

                    // recover bins
                    val plmnBinRef = simInfo.plmnBinRef
                    val destPlmnBinFile = File(Configuration.simDataDir, "00001${plmnBinRef.substring(plmnBinRef.length - 12, plmnBinRef.length)}.bin")
                    val srcPlmnBinFile = File(getSimDir(imsi), plmnBinRef)
                    FileUtils.copyFile(srcPlmnBinFile, destPlmnBinFile)

                    // recover fplmn
                    val fplmnRef = simInfo.fplmnRef
                    if (!TextUtils.isEmpty(fplmnRef)) {
                        val destfplmnBinFile = File(Configuration.RULE_FILE_DIR, fplmnRef)
                        val srcfplmnBinFile = File(getSimDir(imsi), fplmnRef)
                        FileUtils.copyFile(srcfplmnBinFile, destfplmnBinFile)
                    }

                    // recover fee bin
                    val feeBinRef = simInfo.feeBinRef
                    if (!TextUtils.isEmpty(feeBinRef)) {
                        val destfeeBinFile = File(Configuration.RULE_FILE_DIR, feeBinRef)
                        val srcfeeBinFile = File(getSimDir(imsi), feeBinRef)
                        FileUtils.copyFile(srcfeeBinFile, destfeeBinFile)
                    }

                    // fetch SoftSIM
                    val card = Card()
                    card.cardType = CardType.SOFTSIM
                    card.imsi = simInfo.imsi.toString()
                    val binName = simInfo.plmnBinRef
                    card.imageId = "00001" + binName.substring(binName.length - 12, binName.length)
                    card.iccId = simInfo.iccid
                    card.msisdn = simInfo.msisdn
                    card.ki = simInfo.ki
                    card.opc = simInfo.opc
                    CardRepository.fetchSoftCard(card)
                } else {
                    logd("[recover] bytes is empty")
                }
            }
        }
    }

    /**
     * 更新备份数据，没有ki，opc 需要从原来的备份数据中获取对应ki，opc填进去再备份
     */
    fun updateBackupSimInfoNoKiOpc(simInfo: SoftsimInfo) {
        val simInfoFile = getSimInfoFile(simInfo.imsi.toString())
        val infoContent = FileIOUtils.readFile2BytesByStream(simInfoFile)
        val newBuilder = simInfo.newBuilder()
        try {
            if (infoContent.isNotEmpty()) {
                val softsimInfo = SoftsimInfo.ADAPTER.decode(infoContent)
                newBuilder.ki = softsimInfo.ki
                newBuilder.opc = softsimInfo.opc
            }
            val newSoftsimInfo = newBuilder.build()
            JLog.logv("updateBackupSimInfoNoKiOpc $newSoftsimInfo")
            backupSimInfo(newSoftsimInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            JLog.logv("updateBackupSimInfoNoKiOpc $e")
        }
    }

    /**
     * 保存规则文件
     */
    fun backupRuleFile() {
        val srcPath = File(Configuration.RULE_FILE_DIR, Configuration.RULE_FILE_NAME)
        val destPath = File(RULE_FILE_PATH)
        if (srcPath.exists()) {
            FileUtils.copyFile(srcPath, destPath)
        }
    }

    /**
     * 删除规则文件的备份
     */
    fun deleteRuleBackup() {
        val ruleFile = File(RULE_FILE_PATH)
        if (ruleFile.exists()) {
            val ret = ruleFile.delete()
            if (!ret) {
                ruleFile.delete()
            }
        }
    }
}
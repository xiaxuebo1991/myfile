package com.ucloudlink.refact.product.mifi.seedUpdate.utils

import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinInfo
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType
import com.ucloudlink.framework.protocol.protobuf.ruleItem
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.Configuration.RULE_FILE_DIR
import com.ucloudlink.refact.config.Configuration.RULE_FILE_NAME
import com.ucloudlink.refact.config.Configuration.RULE_ITEM_SPLIT
import com.ucloudlink.refact.config.Configuration.RULE_ITEM_VALUE_SPLIT
import com.ucloudlink.refact.utils.FileIOUtils
import com.ucloudlink.refact.utils.FileUtils
import com.ucloudlink.refact.utils.JLog.*
import java.io.File

/**
 * Created by jiaming.liang on 2018/2/7.
 *
 * 文件存储相关的操作类
 */
object SeedFilesHelper {
    /**
     * 解析并保存已经下载的规则文件
     */
    fun parseAndSaveRuleResp(list: MutableList<ruleItem>): Boolean {
        val sb = StringBuilder()
        list.forEach {
            sb.append(it.mccList).append(RULE_ITEM_VALUE_SPLIT)
                    .append(it.plmns).append(RULE_ITEM_VALUE_SPLIT)
                    .append(it.usermode).append(RULE_ITEM_SPLIT)
        }
        if (sb.isEmpty()) return true

        val dir = File(Configuration.RULE_FILE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(Configuration.RULE_FILE_DIR, RULE_FILE_NAME)
        val ret = FileIOUtils.writeFileFromString(file, sb.toString())
        if (ret) {
            logv("parseAndSaveRuleResp $sb")
        } else {
            loge("parseAndSaveRuleResp ${file.absolutePath} failed!")
        }
        return ret
    }

    /**
     * 保存列表的bin文件
     * PLMN_LIST_BIN
     * FPLMN_BIN
     * FEE_BIN
     */
    fun parseAndSaveBin(list: List<SoftsimBinInfo>?): Boolean {
        list?.forEach {
            it.type ?: return@forEach
            val file: File
            val dirStr: String
            when (it.type) {
                SoftsimBinType.PLMN_LIST_BIN -> {
                    dirStr = Configuration.simDataDir
                    file = File(dirStr, "00001${it.binref.substring(it.binref.length - 12, it.binref.length)}.bin")
                }
                SoftsimBinType.FPLMN_BIN, SoftsimBinType.FEE_BIN -> {
                    dirStr = Configuration.RULE_FILE_DIR
                    file = File(dirStr, it.binref)
                }
            }
            if (dirStr.isNotEmpty()) {
                val dir = File(dirStr)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }

            if (FileIOUtils.writeFileFromBytesByChannel(file, it.data.toByteArray(), false, true)) {
                logd("parseAndSaveBin ${it.data} into ${file.absolutePath}")
            } else {
                loge("parseAndSaveBin ${file.absolutePath} failed!")
                return false
            }
        }
        return true
    }

    /**
     * 检查 规则文件 是否存在
     */
    fun checkRuleRefExist(): Boolean {
        val file = File(Configuration.RULE_FILE_DIR, RULE_FILE_NAME)
        return file.isFile && file.exists()
    }

    /**
     * 检查 binRef 是否存在
     */
    fun checkBinRefExist(type: SoftsimBinType, binRef: String): Boolean {
        val file: File = when (type) {
            SoftsimBinType.PLMN_LIST_BIN -> {
                File(Configuration.simDataDir, "00001${binRef.substring(binRef.length - 12, binRef.length)}.bin")
            }
            SoftsimBinType.FPLMN_BIN, SoftsimBinType.FEE_BIN -> {
                File(Configuration.RULE_FILE_DIR, binRef)
            }
        }
        return file.exists() && file.isFile && file.length() > 0
    }

    fun getFplmnListByBinRef(binRef: String): Array<String>? {
        if (TextUtils.isEmpty(binRef)) {
            loge("[getFplmnListByBinRef] binRef is empty")
            return null
        }
        val file = File(Configuration.RULE_FILE_DIR, binRef)
        val list = FileIOUtils.readFile2List(file)
        if (list != null && list.size > 0) {
            return Array(list.size, { list[it].replace(";", "") })
        }
        return null
    }

    /**
     * 删除设备的规则文件
     */
    fun deleteOldRuleFile() {
        val ret = FileUtils.deleteFilesInDirWithFilter(RULE_FILE_DIR, { file ->
            val fileName = FileUtils.getFileName(file)
            fileName == RULE_FILE_NAME
        })
        if (!ret) {
            loge("deleteOldRuleFile under $RULE_FILE_DIR failed!")
        } else {
            logd("deleteOldRuleFile success.")
        }
    }

    fun getBinType(type: Int): SoftsimBinType {
        return arrayOf(SoftsimBinType.PLMN_LIST_BIN, SoftsimBinType.FEE_BIN, SoftsimBinType.FPLMN_BIN)[type - 1]
    }
}


package com.ucloudlink.refact.product.mifi.seedUpdate.utils

import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.ExtSoftsimItem
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType
import com.ucloudlink.framework.protocol.protobuf.SoftsimInfo
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.product.mifi.ExtSoftsimDB
import com.ucloudlink.refact.product.mifi.seedUpdate.event.SEED_SIM_CARD_TYPE_PHY
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import java.util.*

object SoftSimUtils {
    /**
     * 获取当前硬、软卡列表
     */
    fun getCurrentSoftSimList(lastCard: Card?, lastSeedNetworkOperator: String?): ArrayList<ExtSoftsimItem> {
        val list = ArrayList<ExtSoftsimItem>()
        getPhyCardItem(lastCard, lastSeedNetworkOperator)?.let {
            logd("GetSimList: phyCard $it by using lastCard = $lastCard, networkOperator = $lastSeedNetworkOperator")
            list.add(it)
        }
        getSoftSimItems(lastCard)?.forEach {
            logd("GetSimList: softSim $it by using lastCard = $lastCard")
            list.add(it)
        }
        return list
    }

    /**
     * 获取本地软卡信息
     */
    private fun getSoftSimItems(lastCard: Card?): List<ExtSoftsimItem>? {
        val softsimDB = ExtSoftsimDB(ServiceManager.appContext)
        val simInfos: ArrayList<SoftsimInfo>? = softsimDB.allExtSoftsim
        simInfos ?: return null
        return SoftSimUtils.parseToExt(simInfos, lastCard)
    }

    /**
     * 获取本地物理卡信息
     */
    private fun getPhyCardItem(lastCard: Card?, lastSeedNetworkOperator: String?): ExtSoftsimItem? {
        val phyCardImsi = Configuration.internalPhyCardImsi
        val cardType = SEED_SIM_CARD_TYPE_PHY
        logd("PhyCardImsi = $phyCardImsi")
        if (phyCardImsi.isEmpty()) {
            return null
        }
        try {
            val builder = ExtSoftsimItem.Builder()
            builder.imsi = phyCardImsi.toLong()
            //该时间戳为服务器下发，物理卡为0，软卡需从数据库取出
            builder.timestamp = 0
            builder.type = cardType
            builder.reason = 0
            if (lastCard == null) {
                builder.isUsed = false
            } else {
                builder.isUsed = lastCard.cardType == CardType.PHYSICALSIM
            }
            if (builder.isUsed && !lastSeedNetworkOperator.isNullOrEmpty()) {
                builder.mcc = lastSeedNetworkOperator!!.substring(0, 3)
                builder.mnc = lastSeedNetworkOperator.substring(3, lastSeedNetworkOperator.length)
            }
            return builder.build()
        } catch (e: NumberFormatException) {
            JLog.loge("getPhyCardItem catch NumberFormatException phyCardImsi:$phyCardImsi")
            return null
        }
    }

    /**
     * 将SoftsimInfo转换为ExtSoftsimItem
     */
    fun parseToExt(allExtSoftsim: ArrayList<SoftsimInfo>?, lastCard: Card?): List<ExtSoftsimItem> {
        val list = ArrayList<ExtSoftsimItem>()
        allExtSoftsim?.forEach {
            val builder = ExtSoftsimItem.Builder()
            builder.imsi = it.imsi
            if (lastCard == null) {
                builder.isUsed = false
            } else {
                builder.isUsed = it.imsi.toString() == lastCard.imsi
                val numeric = lastCard.numeric
                if (!TextUtils.isEmpty(numeric)) {
                    builder.mcc = numeric.substring(0, 3)
                    builder.mnc = numeric.substring(3, numeric.length)
                }
            }
            builder.timestamp = it.timeStamp
            builder.type = 1 //从数据取的都是软卡
            builder.reason = 0 //未知原因值

            builder.apn = it.apn
            builder.vimei = it.virtualImei.toString()
            builder.plmnref = it.plmnBinRef
            builder.rateref = it.feeBinRef
            builder.fplmnref = it.fplmnRef

            if (builder.plmnref.isNotEmpty()) {
                builder.plmnbinExist = SeedFilesHelper.checkBinRefExist(SoftsimBinType.PLMN_LIST_BIN, it.plmnBinRef)
            }
            if (builder.rateref.isNotEmpty()) {
                builder.ratebinExist = SeedFilesHelper.checkBinRefExist(SoftsimBinType.FEE_BIN, it.feeBinRef)
            }
            if (builder.fplmnref.isNotEmpty()) {
                builder.fplmnbinExist = SeedFilesHelper.checkBinRefExist(SoftsimBinType.FPLMN_BIN, it.fplmnRef)
            }
            list.add(builder.build())
        }
        return list
    }
}
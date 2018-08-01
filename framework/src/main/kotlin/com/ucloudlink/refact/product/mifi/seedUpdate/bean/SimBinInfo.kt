package com.ucloudlink.refact.product.mifi.seedUpdate.bean

import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType

/**
 * 软卡Bin文件
 */
data class SimBinInfo(val imsi: Long, val fileType: SoftsimBinType, val binRef: String)
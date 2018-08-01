package com.ucloudlink.refact.systemapi

import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_MTK
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_QCOM
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_SPRD
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_UNKNOWN
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum
import com.ucloudlink.refact.systemapi.struct.ModelInfo

/**
 * Created by shiqianhua on 2018/1/13.
 */
object ModelConfig {
    private val HARDWARE = android.os.Build.HARDWARE!!

    private val modelList = arrayListOf(
            ModelInfo("C106-9", CHIP_QCOM, ProductTypeEnum.PHONE),
            ModelInfo("C106-7", CHIP_QCOM, ProductTypeEnum.PHONE),
            ModelInfo("G1701", CHIP_QCOM, ProductTypeEnum.PHONE),
            ModelInfo("GP1701", CHIP_QCOM, ProductTypeEnum.PHONE),
            // Device Phone Mi Max
            ModelInfo("MI MAX", CHIP_QCOM, ProductTypeEnum.PHONE),
            // Device Mifi GLMU18A01 (U3C)
            ModelInfo("GLMU18A01", CHIP_SPRD, ProductTypeEnum.MIFI),
            // Device Module M2
            ModelInfo("M2", CHIP_SPRD, ProductTypeEnum.MODULE),
            // Device Phone P2
            ModelInfo("S3P18A", CHIP_SPRD, ProductTypeEnum.PHONE),
            // device phone P3
            ModelInfo("S3P18A04", CHIP_SPRD, ProductTypeEnum.PHONE)
    )

    fun getModelInfo(modelName: String): ModelInfo {
        for (m in modelList) {
            if (modelName == m.modelName) {
                return m
            }
        }
        return ModelInfo(modelName, getDefaultChip(), ProductTypeEnum.PHONE)
    }

    private fun getDefaultChip() = when {
        HARDWARE.contains("qcom") -> CHIP_QCOM
        HARDWARE.contains("sp9850k") -> CHIP_SPRD
        HARDWARE.contains("mtk") -> CHIP_MTK
        else -> CHIP_UNKNOWN
    }
}
package com.ucloudlink.refact.systemapi

import android.content.Context
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_MTK
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_QCOM
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_SPRD
import com.ucloudlink.refact.systemapi.SystemApiConst.CHIP_UNKNOWN
import com.ucloudlink.refact.systemapi.platform.MtkSystemApiBase
import com.ucloudlink.refact.systemapi.platform.QCSystemApiBase
import com.ucloudlink.refact.systemapi.platform.SprdSystemApiBase
import com.ucloudlink.refact.systemapi.platform.UnSupportSystem
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logke

/**
 * Created by shiqianhua on 2018/1/6.
 */
object SystemApiInst {
    private val SDK_INT = android.os.Build.VERSION.SDK_INT
    private val model = android.os.Build.MODEL!!
    private var modelCfg = ModelConfig.getModelInfo(model)

    /**
     * 获取ModelApi方法
     */
    fun getModelApi(context: Context): SystemApiIf {
        return try {
            val className = "SystemApi_" + getModifyModel(model)
            logd("get class ${this.javaClass.`package`.name + ".model." + className}")
            val aClass = Class.forName(this.javaClass.`package`.name + ".model." + className)
            val con = aClass.getConstructor(Context::class.java, ModelInfo::class.java, java.lang.Integer.TYPE)
            con.newInstance(context, modelCfg, SDK_INT) as SystemApiIf
        } catch (e: ClassNotFoundException) {
            logke("Get model api failed: ${e.exception}")
            when (modelCfg.chip) {
                CHIP_QCOM -> QCSystemApiBase(context, modelCfg, SDK_INT)
                CHIP_SPRD -> SprdSystemApiBase(context, modelCfg, SDK_INT)
                CHIP_MTK -> MtkSystemApiBase(context, modelCfg, SDK_INT)
                CHIP_UNKNOWN -> UnSupportSystem(context, modelCfg, SDK_INT)
                else -> UnSupportSystem(context, modelCfg, SDK_INT)
            }
        }
    }

    /**
     * 将model里面的特殊字符替换成'_'
     */
    private fun getModifyModel(model: String): String {
        val str: CharArray = model.toCharArray()
        val modelStr = StringBuilder()
        for (i in str.indices) {
            if ((str[i] in '0'..'9') || (str[i] in 'a'..'z') || (str[i] in 'A'..'Z') || (str[i] == '_')) {
                // do nothing
            } else {
                str[i] = '_'
            }
            modelStr.append(str[i])
        }
        logd("change str $model -> $modelStr")
        return modelStr.toString()
    }
}
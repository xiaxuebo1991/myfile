package com.ucloudlink.refact.business.flow

import android.text.TextUtils
import com.ucloudlink.refact.utils.JLog

/**
 * Created by jianguo.he on 2018/4/3.
 */
class FlowParser {

    companion object {
        val TAG = "IF_FLOW_TEST"

        //[<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>]
        val testIfNameArrayBytesStr = "[200,23,250,27,160,12,210,19]"

        fun testAll(){
            JLog.logi(TAG +", testAll -> begin ===============================================")
            val listData = parseIfNameArrayBytes(testIfNameArrayBytesStr)
            if(listData!=null){
                JLog.logi(TAG +", parseIfNameArrayBytes -> listData is not empty")
                listData.forEach {
                    JLog.logi(TAG +", parseIfNameArrayBytes -> listData.x = $it")
                }
            } else {
                JLog.logi(TAG +", parseIfNameArrayBytes -> listData is null")
            }
            JLog.logi(TAG +", testAll -> end   ===============================================")
        }

        fun parseIfNameArrayBytes(strFlow: String?):  List<String>?{
            if(!TextUtils.isEmpty(strFlow)){
                val tempStrFlow = strFlow!!.replace("[","")!!.replace("]","")
                return tempStrFlow.split(",")
            }

            return null
        }

    }
}
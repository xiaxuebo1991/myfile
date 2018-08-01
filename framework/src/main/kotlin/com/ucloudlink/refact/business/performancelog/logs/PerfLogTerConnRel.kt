package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Err_info
import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_conn_rel
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by haiping.liu on 2018/3/28.
 *
 * 主板连接释放
 */

object PerfLogTerConnRel:PerfLogEventBase(){
    val ID_SOCKET_DISCONNECT = 0
    val ID_ACCESS_EVENT_STOP =1
    var ter_conn_rel:Ter_conn_rel? = null
    /**
     * @param arg1  err_type = 1; // 错误消息类型
     * @param arg2  err_code = 2; // 对应消息的错误码
     */
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogTerConnRel createMsg id=$arg1 ,errorCode= $arg2 ,reason=$any")
        when(arg1){
            ID_ACCESS_EVENT_STOP ->{
                var temp = ter_conn_rel
                if (temp!= null){
                    temp.newBuilder()
                            .err(Err_info(arg2,arg2))//todo 手机错误码和错误信息与G2等不一样，需要给运维新的
                            .build()
                    PerfUntil.saveFreqEventToList(temp)
                }
            }
            ID_SOCKET_DISCONNECT ->{
                val dataInfo = ServiceManager.cloudSimEnabler.getDataEnableInfo()
                val net = PerfUntil.getMobileNetInfo(false, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))
                ter_conn_rel = Ter_conn_rel.Builder()
                        .head(PerfUntil.getCommnoHead())
                        .errorTime((System.currentTimeMillis()/1000).toInt())
                        .net(net)
                        .imsi(ServiceManager.accessEntry.accessState.imis)
                        .build()
            }
        }
    }
}
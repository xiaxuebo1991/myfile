package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Vsim_ResAllo
import com.ucloudlink.framework.protocol.protobuf.preflog.rpc_err_type
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by haiping.liu on 2018/3/26.
 */
object PerfLogVsimResAllo : PerfLogEventBase() {

    val VSIM_RESALLO_ID_REQ = 1 //终端发起分卡请求
    val VSIM_RESALLO_ID_RSP = 2 //终端收到分卡响应
    val VSIM_RESALLO_ID_DOWNLOAD_START:Int = 3//主板从副板接收VSIM镜像文件
    val VSIM_RESALLO_ID_DOWNLOAD_END = 4  //主板从副板接收VSIM镜像文件完成

    var vsim_req = -1L
    var vsim_rsp = -1L
    var vsim_download_start = -1L
    var vsim_download_end = -1L

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogVsimResAllo createMsg id=${System.currentTimeMillis()}")
        when (arg1) {
            VSIM_RESALLO_ID_REQ -> {
                vsim_req = System.currentTimeMillis()
            }
            VSIM_RESALLO_ID_RSP -> {
                any as ResAlloinfo
                vsim_rsp = System.currentTimeMillis()

                if(any.resAlloResult != 100){
                    //分卡响应:失败，事件结束
                    endEvent( any)
                }
            }
            VSIM_RESALLO_ID_DOWNLOAD_START -> {
                vsim_download_start = System.currentTimeMillis()
            }
            VSIM_RESALLO_ID_DOWNLOAD_END -> {
                vsim_download_end = System.currentTimeMillis()
                //事件结束
                endEvent( any)
            }
        }
    }

    private fun endEvent(any: Any) {
        any as  ResAlloinfo
        val head = PerfUntil.getCommnoHead()
        val vsim_ResAllo = Vsim_ResAllo.Builder()
                .head(head)
                .resReqTime((vsim_req/1000).toInt())
                .resResTime((vsim_rsp/1000).toInt())
                .resAlloResult(rpc_err_type.fromValue(any.resAlloResult))
                .imsi(any.imsi)
                .transDelay((vsim_download_end- vsim_download_start).toInt())
                .decodeResult(rpc_err_type.fromValue(any.decodeResult))
                .build()
        PerfUntil.saveEventToList(vsim_ResAllo)
        vsim_req = -1L
        vsim_rsp = -1L
        vsim_download_start = -1L
        vsim_download_end = -1L
    }
}

data class ResAlloinfo(val resAlloResult: Int, val imsi: String?, val decodeResult: Int)
package com.ucloudlink.refact.product.mifi.seedUpdate

import android.os.Looper
import com.ucloudlink.framework.protocol.protobuf.s2c_ext_softsim_req
import com.ucloudlink.framework.protocol.protobuf.s2c_ext_softsim_update_req
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.SimBinInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.UpdateTask
import com.ucloudlink.refact.product.mifi.seedUpdate.business.DownloadNewSimInfoTask
import com.ucloudlink.refact.product.mifi.seedUpdate.business.UpdateSimInfoTask
import com.ucloudlink.refact.product.mifi.seedUpdate.event.*
import com.ucloudlink.refact.product.mifi.seedUpdate.intf.IBusinessTask
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SeedFilesHelper
import com.ucloudlink.refact.utils.JLog.*
import java.util.*

/**
 * Created by jiaming.liang on 2018/2/3.
 *
 * 开始：
 * 1,云卡第一次可用时，上报种子卡列表
 * 2，
 *
 * 退出：
 * 用户点击开始算业务开始，退出时算结束
 */
class SeedUpdateTask : IBusinessTask {
    private var handler: SeedUpdateHandler? = null
    private val lock = Any()
    private var updateSimInfoTask: UpdateSimInfoTask? = null

    /**
     * 开始监听云卡，当云卡拨号成功时，上报种子卡列表
     */
    override fun serviceStart() {
        if (handler == null) {
            synchronized(lock) {
                if (handler == null) {
                    logd("serviceStart")
                    val temp = SeedUpdateHandler(Looper.myLooper())
                    handler = temp
                    temp.sendMessage(temp.obtainMessage(SEED_EVENT_INIT))
                }
            }
        }
    }

    /**
     * 结束
     */
    override fun serviceEnd() {
        synchronized(lock) {
            logd("serviceEnd")
            val temp = handler
            temp ?: return
            temp.sendMessage(temp.obtainMessage(SEED_EVENT_END))
            updateSimInfoTask?.stopTask()
//            temp.looper.quitSafely()
            handler = null
        }
    }

    /**
     * 收到服务器s2c通知
     * 入参是请求，这里收到就新建一个任务处理
     *
     * 注意：
     * 如果服务器同事发多个更新/下载任务，可能会导致异常
     * 服务器应该一个接一个地发送，不应该一次性发送
     * 状态退出时，应主动停掉没完成的任务
     */
    override fun notifyS2C(s2c: Any?) {
        s2c ?: return loge("s2c cmd is null")
        when (s2c) {
            is s2c_ext_softsim_update_req ->
                //处理更新请求
                handlerUpdateSim(s2c)
            is s2c_ext_softsim_req ->
                //处理下载请求
                handlerDownLoadSim(s2c)
            else ->
                loge("unHandled s2c $s2c")
        }
    }

//    fun hasUploadOnce(): Boolean {
//        val handler1 = handler
//        handler1 ?: return false
//        return handler1.hasUploadOnce
//    }

    /**
     * 下载软卡
     */
    private fun handlerDownLoadSim(s2c: s2c_ext_softsim_req){
        val handler1 = handler
        handler1 ?: return loge("handler is null ,please initTask")
        logv("handlerDownLoadSim $s2c")
        val task = DownloadNewSimInfoTask(handler1.looper)
        task.taskObser.subscribe {
            logv("DownloadNewSimInfoTask :$it")
        }
        task.getNewSimInfo()
    }

    /**
     * 更新软卡
     */
    private fun handlerUpdateSim(s2c: s2c_ext_softsim_update_req) {
        val handler1 = handler
        handler1 ?: return loge("handler is null ,please initTask")
        logv("handlerUpdateSim $s2c")
        val task = UpdateSimInfoTask(handler1.looper)
        updateSimInfoTask = task
        var taskInfo: UpdateTask? = null
        when (s2c.type) {
            UPDATE_TYPE_SIM_INFO -> {
                // 更新SIM信息
                //update_info = imsi1,imsi2,...
                s2c.update_info ?: return loge("handlerUpdateSim update_info is null : $s2c")
                logv("UPDATE_TYPE_SIM_INFO: s2c.update_info:" + s2c.update_info)
                val list: Array<String>? = if(s2c.update_info.contains(",")){
                    s2c.update_info.split(",").toTypedArray()
                }else{
                    arrayOf(s2c.update_info)
                }
                taskInfo = UpdateTask(s2c.type, list)
            }
            UPDATE_TYPE_RULE_LIST -> {
                // 更新规则列表
                //update_info = ""
                logv("UPDATE_TYPE_RULE_LIST")
                taskInfo = UpdateTask(s2c.type, null)
            }
            UPDATE_TYPE_PLMN_BIN,
            UPDATE_TYPE_FPLMN_BIN,
            UPDATE_TYPE_FEE_BIN -> {
                // 更新BIN文件
                // update_info = Imsi1,ref1;imsi2,ref2;….;
                logv("UPDATE_BIN")
                s2c.update_info ?: return loge("handlerUpdateSim update_info is null : $s2c")
                val infos = s2c.update_info.split(";")
                val list = ArrayList<SimBinInfo>()
                infos.forEach {
                    val oneInfo = it.split(",")
                    list.add(SimBinInfo(oneInfo[0].toLong(), SeedFilesHelper.getBinType(s2c.type), oneInfo[1]))
                }
                taskInfo = UpdateTask(s2c.type, list)
            }
            else -> {
                loge("handlerUpdateSim unKnown type:${s2c.type}")
            }
        }
        taskInfo ?: return loge("handlerUpdateSim tasInfo is null ")
        task.taskObser.subscribe {
            logv("UpdateSimInfoTask :$it")
        }
        task.doUpdate(taskInfo)
    }
}
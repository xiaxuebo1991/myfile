package com.ucloudlink.refact.product.mifi.seedUpdate.business

import android.os.Looper
import android.os.Message
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.product.mifi.ExtSoftsimDB
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.ErrorInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.SimBinInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.UpdateTask
import com.ucloudlink.refact.product.mifi.seedUpdate.event.*
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SeedFilesHelper
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimDataBackup
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import rx.Subscription
import rx.lang.kotlin.subscribeWith
import java.util.*

/**
 * 执行更新流程，处理更新S2C
 */
class UpdateSimInfoTask(looper: Looper) : SimInfoUpdateBase(looper) {
    private var temTask: UpdateTask? = null
    private var uploadExtSoftsimStateSub: Subscription? = null

    /**
     * 0.收到服务器下发s2c,触发更新软卡的流程
     */
    fun doUpdate(task: UpdateTask) {
        temTask = task
        when (task.fileType) {
            UPDATE_TYPE_SIM_INFO -> {
                task.arg ?: return
                val IMSIs = task.arg as Array<String>
                sendMessage(obtainMessage(SEED_GET_SIM_INFO, IMSIs))
            }
            UPDATE_TYPE_RULE_LIST -> {
                addFileTask(UpdateTask(SEED_UPDATE_FILE_TYPE_RULE, null))
                sendEmptyMessage(START_HANDLE_TASK_LIST)
            }
            else -> {
                task.arg ?: return
                val simBinInfos = task.arg as ArrayList<SimBinInfo>
                addFileTask(UpdateTask(SEED_UPDATE_FILE_TYPE_BIN, simBinInfos))
                sendEmptyMessage(START_HANDLE_TASK_LIST)
            }
        }
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            SEED_GET_SIM_INFO -> {
                val IMSIs = msg.obj
                IMSIs ?: return
                doGetSimNewInfo(IMSIs as Array<String>)
            }
            SEED_UPDATE_REPORT_TO_SERVER -> {
                reportToService()
            }
            else -> {
                loge("unknown msg.what: ${msg.what}")
            }
        }
    }

    private fun doGetSimNewInfo(imsis: Array<String>) {
        logd("doGetSimNewInfo: $imsis")
        Requestor.getSimNewInfo(imsis, REQUEST_SIM_INFO_TIME_OUT).retry(retryHandle).subscribeWith {
            onSuccess {
                val softsimInfoRsp = it as GetSoftsimInfoRsp
                val softsims = softsimInfoRsp.softsims
                logd("doGetSimNewInfo success: $softsims")
                if (softsims != null) {
//                    addFileTask(UpdateTask(SEED_UPDATE_FILE_TYPE_RULE, null)) //fixme 这行有什么用？
                    newSoftSims = Array(softsims.size, { index -> softsims[index] })
                    sendEmptyMessage(SEED_EVENT_DOWNLOAD_DONE)
                }
            }
            onError {
                val errorInfo = ErrorInfo(EXT_SOFTSIM_ERROR_GET_SIMINFO)
                errorInfo.add(0, 0)
                obtainMessage(SEED_UPDATE_REPORT_FAIL, errorInfo).sendToTarget()
                loge("doGetSimNewInfo it:${it.message}")
            }
        }
    }

    /**
     * 下载完成
     * 1。如果更新规则文件，就直接写到原文件上,其他地方已经实现
     * 2。如果是simInfo ，更新数据库的simInfo,不保存ki opc
     * 3。如果是镜像文件，资费文件，fplmn文件，同时更新到simInfo的数据库中（这个不确定，是否会另外下发一个更新siminfo的s2c）
     * 镜像文件还需要做upload so的动作,upload 成功后，可以删除镜像文件
     */
    override fun doUpdateData() {
        val task = temTask
        task ?: return
        val ret: Boolean //标记是否执行更新成功
        when (task.fileType) {
            UPDATE_TYPE_SIM_INFO -> {
                // 包含ki opc
                val simInfoRsp = newSoftSims
                logd("doUpdateData UPDATE_TYPE_SIM_INFO ${simInfoRsp?.size}")
                if (simInfoRsp == null) {
                    ret = false
                } else {
                    //siminfo 更新 不更新binref
                    // 需要取旧的binref，覆盖新的,等新的bin更新了，才会更新binref。
                    // 避免出现更新了simInfo但没有对应的binref的问题出现
                    val softsimDB = ExtSoftsimDB(ServiceManager.appContext)
                    val updateSimInfo = ArrayList<SoftsimInfo>()
                    SoftSimDataBackup.backup(simInfoRsp)
                    simInfoRsp.forEach {
                        val builder = it.newBuilder()
                        val oldsimInfo = softsimDB.getSimInfoByImsi(it.imsi.toString())
                        if (oldsimInfo != null) {
                            builder.feeBinRef = oldsimInfo.feeBinRef
                            builder.fplmnRef = oldsimInfo.fplmnRef
                            builder.plmnBinRef = oldsimInfo.plmnBinRef
                            builder.ki = ""
                            builder.opc = ""
                        }
                        val softsimInfo = builder.build()
                        updateSimInfo.add(softsimInfo)
                    }
                    ret = updateSimInfo(softsimDB, updateSimInfo)
                }
            }
            UPDATE_TYPE_RULE_LIST -> {
                // 在收到rule list更新的时候进行seedRule.bin的备份
                SoftSimDataBackup.backupRuleFile()
                ret = true
            }
            else -> {
                //bin文件更新
                task.arg ?: return
                val newSimInfos = task.arg as ArrayList<SimBinInfo>
                logd("doUpdateData bin update: ${newSimInfos.size}")
                val softsimDB = ExtSoftsimDB(ServiceManager.appContext)
                val list = ArrayList<SoftsimInfo>()
                newSimInfos.forEach { newSimInfo ->
                    val oldSimInfo = softsimDB.getSimInfoByImsi(newSimInfo.imsi.toString())
                    val builder = oldSimInfo.newBuilder()
                    val binRef = newSimInfo.binRef
                    when (newSimInfo.fileType) {
                        SoftsimBinType.FEE_BIN -> {
                            builder.feeBinRef(binRef)
                        }
                        SoftsimBinType.PLMN_LIST_BIN -> {
                            val updateImsi = newSimInfo.imsi.toString()
                            builder.plmnBinRef(binRef)
                            SoftSimNative.updateSoftCardImage(updateImsi, "00001" + binRef.substring(binRef.length - 12, binRef.length))
                            ServiceManager.seedCardEnabler.notifyEventToCard(DataEnableEvent.EVENT_SOFTSIM_IMAGE_UPDATED, updateImsi)
                        }
                        SoftsimBinType.FPLMN_BIN -> {
                            builder.fplmnRef(binRef)
                        }
                    }
                    val softsimInfo = builder.build()
                    SoftSimDataBackup.updateBackupSimInfoNoKiOpc(softsimInfo)
                    list.add(softsimInfo)
                }
                ret = updateSimInfo(softsimDB, list)
            }
        }
        if (ret) {
            obtainMessage(SEED_EVENT_UPDATE_DONE).sendToTarget()
        } else {
            val errorInfo = ErrorInfo(0)
            obtainMessage(SEED_UPDATE_REPORT_FAIL, errorInfo).sendToTarget()
            loge("doUpdateData update fail! $task")
        }
    }

    override fun doReportSuccess() {
        super.doReportSuccess()
        doReclaimSim()
    }

    private fun reportToService() {
        val task = temTask
        task ?: return loge("doReportSuccess temTask==null")
        val imei = OperatorNetworkInfo.imei
        val binType = if (task.fileType <= 3) SeedFilesHelper.getBinType(task.fileType).value else task.fileType
        val updateList = ArrayList<ExtSoftsimUpdateItem>()
        when (task.fileType) {
            UPDATE_TYPE_SIM_INFO -> {
                val imsis = task.arg as Array<String>
                imsis.forEach {
                    updateList.add(ExtSoftsimUpdateItem(it.toLong(), 0))
                }
            }
            UPDATE_TYPE_PLMN_BIN, UPDATE_TYPE_FPLMN_BIN, UPDATE_TYPE_FEE_BIN -> {
                val simBinInfos = task.arg as ArrayList<SimBinInfo>
                simBinInfos.forEach {
                    updateList.add(ExtSoftsimUpdateItem(it.imsi, 0))
                }
            }
            UPDATE_TYPE_RULE_LIST ->{
                updateList.add(ExtSoftsimUpdateItem(0, 0))
            }
        }
        logd("reportToService: imei=$imei, binType=$binType, updateList=$updateList")
        uploadExtSoftsimStateSub = Requestor.uploadExtSoftsimState(imei, binType, updateList, 30).retry(retryHandle).subscribe({
            sendEmptyMessage(SEED_UPDATE_ACTION_DONE)
            if (it is ExtSoftsimUpdateRsp) {
                if (ErrorCode.RPC_RET_OK == it.errorCode) {
                    loge("reportToService uploadExtSoftsimState success $it")
                } else {
                    loge("reportToService uploadExtSoftsimState fail $it")
                }
            } else {
                loge("reportToService uploadExtSoftsimState fail $it")
            }
        }, {
            sendEmptyMessage(SEED_UPDATE_ACTION_DONE)
            loge("reportToService uploadExtSoftsimState fail $it")
        })
    }

    private fun updateSimInfo(softsimDB: ExtSoftsimDB, softsims: List<SoftsimInfo>?): Boolean {
        softsims ?: return true
        softsims.forEach {
            val ret = softsimDB.updataExtSoftsimDb(it)
            if (!ret) {
                loge("updateSimInfo fail")
                return false
            }
        }
        return true
    }

    override fun stopTask() {
        clearSub(uploadExtSoftsimStateSub)
        uploadExtSoftsimStateSub = null
    }
}
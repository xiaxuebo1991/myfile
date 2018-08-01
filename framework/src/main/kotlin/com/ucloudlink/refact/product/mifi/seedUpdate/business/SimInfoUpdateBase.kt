package com.ucloudlink.refact.product.mifi.seedUpdate.business

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.ucloudlink.framework.protocol.protobuf.ExtSoftsimRuleRsp
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimBinRsp
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinReqInfo
import com.ucloudlink.framework.protocol.protobuf.SoftsimInfo
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.business.AbortException
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.product.mifi.ExtSoftsimDB
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.ErrorInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.SimBinInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.TaskState
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.UpdateTask
import com.ucloudlink.refact.product.mifi.seedUpdate.event.*
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SeedFilesHelper
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimDataBackup
import com.ucloudlink.refact.utils.JLog.*
import rx.Single
import rx.Subscription
import rx.functions.Func1
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.subscribeWith
import java.util.*

/**
 * siminfo plmnbin 存入数据使用服务器返回的源数值
 * 但保存的文件使用这种格式截取存放{ "00001" + it.binref.substring(it.binref.length - 12, it.binref.length)}
 * 原因是so addCard 时，避免超长
 * 所以使用plmnbin或者校验对应binref是否存在时，从数据库取出时，需要转变
 */
open class SimInfoUpdateBase(looper: Looper) : Handler(looper) {
    private val DOWNLOAD_BIN_TIMEOUT: Long = 300 * 1000L
    private val REPORT_ERROR_TIMEOUT: Long = 300 * 1000L
    // 请求Soft SIM超时时间
    protected val REQUEST_SIM_INFO_TIME_OUT: Long = 300 * 1000L
    protected val UPLOAD_RECLAIM_SOFTSIM_TIMEOUT: Long = 300 * 1000L

    protected var newSoftSims: Array<SoftsimInfo>? = null
    protected var reclaimSims: Array<String>? = null
    // 重试Handler
    protected val retryHandle: (Int, Throwable) -> Boolean = { time, exception ->
        loge("Retry download $time : ${exception.message}")
        time <= MAX_RETRY_TIME && exception !is AbortException
    }
    // 最大重试次数
    private val MAX_RETRY_TIME: Int = 3
    // 当前重试次数
    protected var retryTime = 0

    private var binTaskSub: Subscription? = null
    private var reportFailSub: Subscription? = null
    private val taskList = ArrayList<UpdateTask>()
    val taskObser = BehaviorSubject<TaskState>()

    override fun handleMessage(msg: Message) {
        logv("handleMessage $msg")
        when (msg.what) {
            START_HANDLE_TASK_LIST -> {
                // 根据任务list 执行，下载完所有任务
                doHandleTaskList(taskList)
            }
            SEED_EVENT_DOWNLOAD_DONE -> {
                // 下载各种bin完成，更新卡信息到数据库
                doUpdateData()
            }
            SEED_EVENT_UPDATE_DONE -> {
                // 更新完成，校验更新是否有效能用
                doCheckUpdateInfo()
            }
            SEED_UPDATE_REPORT_SUCCESS -> {
                // 校验完成，成功就执行回收卡操作，失败就报告服务器失败
                doReportSuccess()
            }
            SEED_UPDATE_REPORT_FAIL -> {
                // 任务完成，通知服务器更新成功还是失败
                doReportFail(msg.obj as ErrorInfo)
            }
            SEED_UPDATE_ACTION_DONE -> {
                taskObser.onNext(TaskState.FINISH)
                stopTask()
            }
            SEED_UPDATE_REPORT_TO_SERVER -> {
                // 报告服务器之前备份一下规则文件
                SoftSimDataBackup.backupRuleFile()
            }
        }
    }

    /**
     * 根据任务list 执行，下载完所有任务（下载规则 或者 bin）就成功，跳到下一个流程 SEED_EVENT_DOWNLOAD_DONE
     */
    private fun doHandleTaskList(taskList: ArrayList<UpdateTask>) {
        logd("doHandleTaskList: $taskList")
        var subs: Array<Subscription?>? = null
        binTaskSub = Single.create<Boolean> { allTask ->
            subs = Array(taskList.size, { null })
            val taskDone: (UpdateTask) -> Unit = { task ->
                taskList.remove(task)
                if (taskList.isEmpty()) {
                    allTask.onSuccess(true)
                }
            }

            synchronized(this) {
                taskList.forEachIndexed { index, updateTask ->
                    val ruleSub = doRequestFile(updateTask).retry(retryHandle).subscribeWith {
                        onSuccess {
                            if (it) {
                                taskDone.invoke(updateTask)
                            } else {
                                allTask.onError(Exception("return false!"))
                            }
                        }
                        onError {
                            // 是否要重试？
                            loge("doHandleTaskList error: ${it.message}")
                            allTask.onError(it)
                        }
                    }
                    val subs1 = subs
                    if (subs1 != null) {
                        subs1[index] = ruleSub
                    }
                }
            }
        }.retry(retryHandle).doOnUnsubscribe {
            subs?.forEach { clearSub(it) }
            subs = null
        }.subscribe({
            logd("doHandleTaskList success: $it")
            sendEmptyMessage(SEED_EVENT_DOWNLOAD_DONE)
            clearSub(binTaskSub)
            binTaskSub = null
        }, {
            //有一个不成功，就放弃更新,重试
            subs?.forEach { clearSub(it) }
            subs = null
            clearSub(binTaskSub)
            binTaskSub = null
            loge("doHandleTaskList fail :${it.message}")
            obtainMessage(SEED_UPDATE_REPORT_FAIL, ErrorInfo(0)).sendToTarget()
        })
    }

    /**
     * 下载各种bin完成，更新卡信息到数据库
     */
    protected open fun doUpdateData() {}

    /**
     * 更新完成，校验更新是否有效能用
     * TODO:这里还应该有校验项：1新下的卡是否能用，2，规则文件是否有效，fplmn，资费文件是否可用)
     * 暂时不做检查处理，直接发送检查成功
     */
    private fun doCheckUpdateInfo() {
        val isSuccess = true
        logd("doCheckUpdateInfo: $isSuccess")
        if (isSuccess) {
            sendEmptyMessage(SEED_UPDATE_REPORT_SUCCESS)
        } else {
            val errorCode = 1
            val errorInfo = ErrorInfo(errorCode)
            obtainMessage(SEED_UPDATE_REPORT_FAIL, errorInfo).sendToTarget()
        }
    }

    /**
     * 校验完成，成功就执行回收卡操作，失败就报告服务器失败
     */
    protected open fun doReportSuccess() {}

    /**
     * 任务完成，通知服务器更新成功还是失败
     * todo 通知完也要走到SEED_UPDATE_ACTION_DONE
     */
    private fun doReportFail(error: ErrorInfo) {
        val imei = OperatorNetworkInfo.imei
        val user = RunningStates.getUserName()
        logd("doReportFail: imei=$imei, user=$user, error=$error")
        reportFailSub = Requestor.uploadExtSoftsimUploadError(error.reason, user, imei, error.errorList, REPORT_ERROR_TIMEOUT).retry(retryHandle).subscribeWith {
            onSuccess {
                sendEmptyMessage(SEED_UPDATE_ACTION_DONE)
            }
            onError {
                it.printStackTrace()
                loge("doReportFail ${it.message}")
            }
        }
    }

    /**
     * 更新完成，停止任务
     */
    open fun stopTask() {
        clearSub(binTaskSub)
        clearSub(reportFailSub)
        binTaskSub = null
        reportFailSub = null
    }

    /**
     * 添加任务
     */
    protected fun addFileTask(updateTask: UpdateTask) {
        synchronized(this) {
            taskList.add(updateTask)
        }
    }

    protected fun clearSub(sub: Subscription?) {
        sub ?: return
        if (!sub.isUnsubscribed) {
            sub.unsubscribe()
        }
    }

    /**
     * 根据需要下载的文件类型进行文件下载
     */
    private fun doRequestFile(updateTask: UpdateTask): Single<Boolean> {
        return when (updateTask.fileType) {
            SEED_UPDATE_FILE_TYPE_RULE -> {
                doRequestRule()
            }
            SEED_UPDATE_FILE_TYPE_BIN -> {
                doRequestBin(updateTask.arg as ArrayList<SimBinInfo>)
            }
            else -> {
                loge("unKnown fileType :${updateTask.fileType}")
                Single.just(true)
            }
        }
    }

    /**
     * 下载规则文件
     * 保存到 {Configuration.RULE_FILE_DIR} 直接复写之前的
     * 如果下发的是null，就清除原来的
     */
    private fun doRequestRule(): Single<Boolean> {
        val imei = OperatorNetworkInfo.imei
        logd("doRequestRule: imei = $imei")
        if (imei.isEmpty()) {
            return Single.just(false)
        }
        return Requestor.downloadRuleList(imei, 50).flatMap<Boolean>(Func1<Any, Single<Boolean>> {
            val ret: Boolean
            if (it is ExtSoftsimRuleRsp) {
                ret = true
                val ruleList = it.ruleList
                logd("doRequestRule success: ruleList = $ruleList")
                if (ruleList != null && ruleList.size > 0) {
                    // 下载成功，保存规则文件
                    if (!SeedFilesHelper.parseAndSaveRuleResp(ruleList)) {
                        throw Exception("doRequestRule parseAndSaveRuleResp fail")
                    }
                } else {
                    // 下载成功，但是信息为空，清除rule及备份
                    SeedFilesHelper.deleteOldRuleFile()
                    SoftSimDataBackup.deleteRuleBackup()
                }
            } else {
                loge("doRequestRule fail: ${ErrorCode.PARSE_HEADER_STR + it.toString()}")
                ret = false
            }
            return@Func1 Single.just(ret)
        })
    }

    /**
     * 下发bin文件，保存位置看{parseAndSaveBin}
     */
    private fun doRequestBin(arrayList: ArrayList<SimBinInfo>): Single<Boolean> {
        logd("doRequestBin")
        val reqInfoList = ArrayList<SoftsimBinReqInfo>()
        arrayList.forEach {
            reqInfoList.add(SoftsimBinReqInfo.Builder().type(it.fileType).binref(it.binRef).build())
        }
        logd("doRequestBin: $reqInfoList")
        return Requestor.getExtSoftsimBin(reqInfoList, DOWNLOAD_BIN_TIMEOUT).flatMap<Boolean>(Func1<Any, Single<Boolean>> {
            var ret = false
            if (it is GetSoftsimBinRsp) {
                if (ErrorCode.RPC_RET_OK == it.errorCode) {
                    logd("doRequestBin success bins = ${it.bins}")
                    ret = SeedFilesHelper.parseAndSaveBin(it.bins)
                } else {
                    loge("doRequestBin unKnown ErrorCode :${it.errorCode}")
                }
            } else {
                loge("doRequestBin unKnown rsp type ${it.javaClass}")
            }
            return@Func1 Single.just(ret)
        })
    }

    /**
     * 5.1
     * 处理回收卡
     * 删除simInfo的数据
     */
    protected fun doReclaimSim() {
        logd("doReclaimSim")
        try {
            // Backup softsim(newSoftSims)
            val softSims = newSoftSims //包含ki opc
            softSims?.let {
                SoftSimDataBackup.backup(it)
            }

            // Delete old softsimInfo(reclaimSims)
            val reclaimList = reclaimSims
            logd("reclaimList = $reclaimList")
            reclaimList ?: return run { sendEmptyMessage(SEED_UPDATE_REPORT_TO_SERVER) }
            val extSoftsimDB = ExtSoftsimDB(ServiceManager.appContext)
            extSoftsimDB.delSoftsimInfo(reclaimList)
            reclaimList.forEach {
                SoftSimNative.deleteCard(it)
            }
            SoftSimDataBackup.deleteBackup(reclaimList)
            sendEmptyMessage(SEED_UPDATE_REPORT_TO_SERVER)
        } catch (e: Throwable) {
            e.printStackTrace()
            val errorInfo = ErrorInfo(0)
            obtainMessage(SEED_UPDATE_REPORT_FAIL, errorInfo).sendToTarget()
        }
    }
}
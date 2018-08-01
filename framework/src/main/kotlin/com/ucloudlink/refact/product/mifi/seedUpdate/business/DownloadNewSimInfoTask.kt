package com.ucloudlink.refact.product.mifi.seedUpdate.business

import android.os.Looper
import android.os.Message
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.softsim.CardRepository
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.product.mifi.ExtSoftsimDB
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.ErrorInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.SimBinInfo
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.TaskState
import com.ucloudlink.refact.product.mifi.seedUpdate.bean.UpdateTask
import com.ucloudlink.refact.product.mifi.seedUpdate.event.*
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimDataBackup
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimUtils
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import rx.Subscription
import rx.lang.kotlin.subscribeWith
import java.util.*

/**
 * Created by jiaming.liang on 2018/2/6.
 *
 * 执行下载流程
 */
class DownloadNewSimInfoTask(looper: Looper) : SimInfoUpdateBase(looper) {
    private var simInfoSub: Subscription? = null
    private var uploadReclaimSub: Subscription? = null
    private var lastCard: Card? = null

    /**
     * 0.收到服务器下发s2c,触发下载新卡流程
     */
    fun getNewSimInfo() {
        sendEmptyMessage(SEED_GET_SIM_INFO)
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            SEED_GET_SIM_INFO -> {
                taskObser.onNext(TaskState.START)
                doRequestSimInfo()
            }
            SEED_UPDATE_REPORT_TO_SERVER -> {
                reportToService()
            }
            else -> {
                loge("unknown msg.what: ${msg.what}")
            }
        }
    }

    /**
     * 1.开始获取simInfo
     */
    private fun doRequestSimInfo() {
        taskObser.onNext(TaskState.RUNNING)
        val imei = OperatorNetworkInfo.imei
        if (imei.isEmpty()) {
            taskObser.onNext(TaskState.FINISH)
            loge("doRequestSimInfo imei is null")
            return
        }
        val imei_l:Long = try {
            imei.toLong()
        } catch (e:Exception) {
            -1L
        }
        if (imei_l == -1L) {
            taskObser.onNext(TaskState.FINISH)
            loge("doRequestSimInfo imei is not long $imei")
            return
        }
        val userName = RunningStates.getUserName()
        val reason = 0
        logd("doRequestSimInfo: imei=$imei, user=$userName")
        simInfoSub = Requestor.requestDownloadSoftsim(userName, imei.toLong(), reason, REQUEST_SIM_INFO_TIME_OUT).retry(retryHandle).subscribeWith {
            onSuccess {
                if (it is DispatchExtSoftsimRsp) {
                    when (it.errorCode) {
                        ErrorCode.RPC_RET_OK -> {
                            logd("doRequestSimInfo: done")
                            onGetSimInfoDone(it)
                        }
                        ErrorCode.RPC_SEED_NO_SOFTSIM -> {
                            loge("doRequestSimInfo: server no softSim")
                            obtainMessage(SEED_UPDATE_ACTION_DONE).sendToTarget()
                        }
                        else -> {
                            loge("doRequestSimInfo: unHandle error code :${it.errorCode}")
                            obtainMessage(SEED_UPDATE_ACTION_DONE).sendToTarget()
                        }
                    }
                    retryTime = 0
                } else {
                    loge("doRequestSimInfo: error:${ErrorCode.PARSE_HEADER_STR}$it")
                    retryTime++
                    if (retryTime <= 3) {
                        sendEmptyMessage(SEED_GET_SIM_INFO)
                    }
                }
            }
            onError {
                loge("doRequestSimInfo: fail: retryTime:$retryTime msg:${it.message} ")
                val errorInfo = ErrorInfo(0)
                errorInfo.add(0, 0)
                obtainMessage(SEED_UPDATE_REPORT_FAIL, errorInfo).sendToTarget()
            }
        }
    }

    /**
     * 2，获取玩simInfo ，执行规则文件，资费文件，镜像文件，fplmn文件下载（SimInfoUpdateBase.doHandleTaskList）
     */
    private fun onGetSimInfoDone(dispatchExtSoftsimRsp: DispatchExtSoftsimRsp) {
        val softSims = dispatchExtSoftsimRsp.softsims
        logd("onGetSimInfoDone softSims = $softSims")
        val reclaimList = dispatchExtSoftsimRsp.reclaimList
        logd("onGetSimInfoDone reclaimList = $reclaimList")
        if (reclaimList != null && reclaimList.isNotEmpty()) {
            reclaimSims = Array(reclaimList.size, { index -> reclaimList[index].imsi.toString() })
        }
        if (softSims != null && softSims.isNotEmpty()) {
            addFileTask(UpdateTask(SEED_UPDATE_FILE_TYPE_RULE, null))
            newSoftSims = Array(softSims.size, { index ->
                softSims[index]
            })
            val list = ArrayList<SimBinInfo>()
            softSims.forEach {
                if (it.feeBinRef.isNotEmpty()) {
                    list.add(SimBinInfo(it.imsi, SoftsimBinType.FEE_BIN, it.feeBinRef))
                }
                if (it.fplmnRef.isNotEmpty()) {
                    list.add(SimBinInfo(it.imsi, SoftsimBinType.FPLMN_BIN, it.fplmnRef))
                }
                if (it.plmnBinRef.isNotEmpty()) {
                    list.add(SimBinInfo(it.imsi, SoftsimBinType.PLMN_LIST_BIN, it.plmnBinRef))
                }
            }
            if (list.isNotEmpty()) {
                addFileTask(UpdateTask(SEED_UPDATE_FILE_TYPE_BIN, list))
            }
            sendEmptyMessage(START_HANDLE_TASK_LIST)
        }
    }

    /**
     * 3,相关文件下载完成,需要根据更新的内容处理
     * 3.1 把数据更新到数据库中
     * 3.2 把卡add到so中，（todo 如果add成功，可以删除镜像文件）
     * 把simInfo更新到数据库中，不保存ki,opc
     */
    override fun doUpdateData() {
        super.doUpdateData()
        val softSims = newSoftSims //包含ki，opc
        softSims ?: return
        val extSoftsimDB = ExtSoftsimDB(ServiceManager.appContext)
        var updateRet = false
        var failImsi = 0L
        softSims.forEach {
            updateRet = false
            val card = Card()
            card.cardType = CardType.SOFTSIM
            card.imsi = it.imsi.toString()
            val binName = it.plmnBinRef
            card.imageId = "00001" + binName.substring(binName.length - 12, binName.length)
            card.iccId = it.iccid
            card.msisdn = it.msisdn
            card.ki = it.ki
            card.opc = it.opc
            val ret = CardRepository.fetchSoftCard(card)
            if (ret) {
                val info = it.newBuilder().ki("").opc("").build()
                updateRet = extSoftsimDB.updataExtSoftsimDb(info)
                if (updateRet) {
                    logd("doUpdateData fetchSoftCard success!")
                } else {
                    failImsi = it.imsi
                    return@forEach
                }
            } else {
                loge("doUpdateData fetchSoftCard Fail!")
                failImsi = it.imsi
                return@forEach
            }
        }
        if (updateRet) {
            //4. 更新成功后，通知更新执行一下步
            obtainMessage(SEED_EVENT_UPDATE_DONE).sendToTarget()
        } else {
            // FIXME：Bug31567 需要确认，更新失败时是否还要进行回收流程
            loge("doUpdateData fail")
            val errorInfo = ErrorInfo(0)
            errorInfo.add(failImsi, 0)
            obtainMessage(SEED_UPDATE_REPORT_FAIL, errorInfo).sendToTarget()
        }
    }

    /**
     * 5,校验完成后执行，释放回收的卡
     */
    override fun doReportSuccess() {
        super.doReportSuccess()
        doReclaimSim()
    }

    /**
     * 6,通知服务器更新成功
     * 重试3次
     */
    private fun reportToService() {
        val extSoftsimDB = ExtSoftsimDB(ServiceManager.appContext)
        val imei = OperatorNetworkInfo.imei
        val localSimList = SoftSimUtils.parseToExt(extSoftsimDB.allExtSoftsim, lastCard)
        val user = RunningStates.getUserName()
        val reclaimSimsList = ArrayList<ReclaimImsi>()
        this.reclaimSims?.forEach { reclaimSimsList.add(ReclaimImsi(it.toLong())) }
        logd("reportToService: simList=$localSimList, reclaimSimList=$reclaimSimsList")
        uploadReclaimSub = Requestor.uploadReclaimExtSoftsim(reclaimSimsList, localSimList, user, imei, UPLOAD_RECLAIM_SOFTSIM_TIMEOUT).retry(retryHandle).subscribe({
            val rsp = it as ReclaimExtSoftsimRsp
            loge("reportToService success: $rsp")
            if (rsp.errorCode == ErrorCode.RPC_RET_OK) {
                retryTime = 0
                sendEmptyMessage(SEED_UPDATE_ACTION_DONE)
            }
        }, {
            loge("reportToService fail: retryTime:$retryTime msg:${it.message}")
            sendEmptyMessage(SEED_UPDATE_ACTION_DONE)
        })
    }

    override fun stopTask() {
        super.stopTask()
        clearSub(simInfoSub)
        clearSub(uploadReclaimSub)
        simInfoSub = null
        uploadReclaimSub = null
    }
}
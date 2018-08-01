package com.ucloudlink.refact.systemapi.platform

import android.content.Context
import android.os.Looper
import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.refact.business.s2ccmd.UpQxLogArgs
import com.ucloudlink.refact.business.s2ccmd.logexecutor.ILogExecutor
import com.ucloudlink.refact.business.s2ccmd.logexecutor.QCLogExecutor
import com.ucloudlink.refact.channel.enabler.simcard.CloudSimEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.SeedEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.CardController
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.IPlatformTransfer
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave
import com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.QCCloudSimEnabler
import com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.QCSeedEnabler
import com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.cardcontroller.QCCardController
import com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.cardcontroller.QCPlatFormTransfer
import com.ucloudlink.refact.platform.qcom.flow.netlimit.QCSeedCardNetFactory
import com.ucloudlink.refact.systemapi.SystemApiBase
import com.ucloudlink.refact.systemapi.struct.FileWapper
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.utils.LoadFiles
import java.util.*

/**
 * Created by shiqianhua on 2018/1/8.
 */
open class QCSystemApiBase(context: Context, modelInfo: ModelInfo, sdkInt:Int): SystemApiBase(context, modelInfo, sdkInt) {
    override fun initPlatfrom(context: Context) {
        LoadFiles.copyFiles(context, LoadFiles.qxdmData, Configuration.qxdmDataDir)
        QxdmLogSave.getInstance().controlStart()
    }

    override fun startModemLog(arg1: Int, arg2: Int, obj: Any?) {
        QxdmLogSave.getInstance().startQxLogs(arg1, arg2)
    }

    override fun stopModemLog(arg1: Int, arg2: Int, obj: Any?) {
        QxdmLogSave.getInstance().stopQxLogs(arg1)
    }

    override fun clearModemLog(arg1: Int, arg2: Int, obj: Any?) {
        QxdmLogSave.getInstance().cleanQxdmlogzipCmd()
    }

    override fun uploadLog(obj: Any?) {
        if(obj != null){
            if(obj is UpQxLogArgs){
                QxdmLogSave.getInstance().setQxLogArgs(obj)
                QxdmLogSave.getInstance().uploadQxLogs()
            }
        }
    }

    override fun getILogExecutor(): ILogExecutor {
        return QCLogExecutor
    }

    override fun getSeedEnabler(context: Context, looper: Looper): SeedEnabler2 {
        return QCSeedEnabler(context, looper)
    }

    override fun getCloudSimEnabler(context: Context, looper: Looper): CloudSimEnabler2 {
        return QCCloudSimEnabler(context,looper)
    }

    override fun getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP: Boolean, isUiSupportSeedNetLimitByUidAndIP: Boolean): ISeedCardNetCtrl {
        return QCSeedCardNetFactory.getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP,isUiSupportSeedNetLimitByUidAndIP)
    }

    override fun getCardController(): CardController {
        return QCCardController()
    }

    override fun getPlatformTransfer(): IPlatformTransfer {
        return QCPlatFormTransfer()
    }

    override fun getExtLibs(): ArrayList<FileWapper> {
        var extLibs = ArrayList<FileWapper>()
        /*if(sdk > Build.VERSION_CODES.M){
            extLibs.add(FileWapper("libc++.so","qcom/libc++.so", "libc++.so"))
        }*/

        val libs = arrayOf(
                //TODO:lib必须按照顺序载入，需要优化
                FileWapper("libucssl-sign.so","qcom/libucssl-sign.so","libucssl-sign.so"),
                FileWapper("libdyn.so","qcom/libdyn.so","libdyn.so"),
                FileWapper("libsoftsim.so","qcom/libsoftsim.so", "libsoftsim.so"),
                FileWapper("libsoftsim-adapter.so","qcom/libsoftsim-adapter.so", "libsoftsim-adapter.so"),
                FileWapper("libuc.so", "qcom/libuc.so", "libuc.so")
        )

        for(l in libs){
            extLibs.add(l)
        }

        return extLibs
    }

    override fun getSimCommFiles(): ArrayList<FileWapper> {
        val libs = arrayListOf(
                FileWapper("server.crt","qcom/server.crt","server.crt")
                //FileWapper("libdyn-common.so","qcom/libdyn-common.so","libdyn-common.so")
        )

        return libs
    }

    override fun switchSeedMode(): Int {
        return 1
    }
}
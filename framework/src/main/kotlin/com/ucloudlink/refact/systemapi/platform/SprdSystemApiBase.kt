package com.ucloudlink.refact.systemapi.platform

import android.content.Context
import android.os.Build
import android.os.Looper
import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.refact.business.s2ccmd.logexecutor.ILogExecutor
import com.ucloudlink.refact.business.s2ccmd.logexecutor.SprdLogExecutor
import com.ucloudlink.refact.channel.enabler.simcard.CloudSimEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.SeedEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.CardController
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.IPlatformTransfer
import com.ucloudlink.refact.platform.sprd.api.SprdApiInst
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.SprdCloudsimEnabler
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.SprdSeedEnabler
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.cardcontroller.SprdCardController
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.cardcontroller.SprdPlatFormTransfer
import com.ucloudlink.refact.platform.sprd.flow.netlimit.SprdSeedCardNetFactory
import com.ucloudlink.refact.systemapi.SystemApiBase
import com.ucloudlink.refact.systemapi.struct.FileWapper
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import java.util.*

/**
 * Created by shiqianhua on 2018/1/8.
 */
open class SprdSystemApiBase(context: Context, modelInfo: ModelInfo, sdkInt:Int): SystemApiBase(context, modelInfo, sdkInt){
    override fun initPlatfrom(context: Context) {

    }

    override fun startModemLog(arg1: Int, arg2: Int, obj: Any?) {

    }

    override fun stopModemLog(arg1: Int, arg2: Int, obj: Any?) {

    }

    override fun clearModemLog(arg1: Int, arg2: Int, obj: Any?) {

    }

    override fun uploadLog(obj: Any?) {

    }

    override fun getILogExecutor(): ILogExecutor {
        return SprdLogExecutor
    }

    override fun getSeedEnabler(context: Context, looper: Looper): SeedEnabler2 {
        return  SprdSeedEnabler(context,looper)
    }

    override fun getCloudSimEnabler(context: Context, looper: Looper): CloudSimEnabler2 {
        return SprdCloudsimEnabler(context, looper)
    }

    override fun getCardController(): CardController {
        return SprdCardController()
    }

    override fun getPlatformTransfer(): IPlatformTransfer {
        return SprdPlatFormTransfer()
    }

    override fun getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP: Boolean, isUiSupportSeedNetLimitByUidAndIP: Boolean): ISeedCardNetCtrl {
        return SprdSeedCardNetFactory.getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP, isUiSupportSeedNetLimitByUidAndIP)
    }

    override fun getExtLibs(): ArrayList<FileWapper> {
        val extLibs = ArrayList<FileWapper>()
        if(sdk > Build.VERSION_CODES.M){
            extLibs.add(FileWapper("libc++.so", "sprd/libc++.so", "libc++.so"))
        }
        val libs = arrayOf(
                //TODO:lib必须按照顺序载入，需要优化
                FileWapper("libucssl-sign.so", "sprd/libucssl-sign.so", "libucssl-sign.so"),
                FileWapper("libsoftsim.so","sprd/libsoftsim.so", "libsoftsim.so"),
                FileWapper("libsoftsim-adapter.so","sprd/libsoftsim-adapter.so", "libsoftsim-adapter.so"),
                FileWapper("librilutils.so","sprd/librilutils.so", "librilutils.so"),
                FileWapper("libatci.so","sprd/libatci.so","libatci.so"),
//                FileWapper("libsprd-ril.so","sprd/libsprd-ril.so","libsprd-ril.so"),
                FileWapper("libsprdApiAdapter.so","sprd/libsprdApiAdapter.so", "libsprdApiAdapter.so"),
                FileWapper("libdyn.so","sprd/libdyn.so", "libdyn.so"),
                FileWapper("libuc.so", "sprd/libuc.so", "libuc.so")
        )
        for(l in libs){
            extLibs.add(l)
        }

        return extLibs
    }

    override fun getSimCommFiles(): ArrayList<FileWapper> {
        val libs = arrayListOf(
                FileWapper("server.crt","sprd/server.crt","server.crt")
                //FileWapper("libdyn-common.so","sprd/libdyn-common.so","libdyn-common.so")
        )

        return libs
    }

    override fun switchSeedMode(): Int {
        return 2
    }

    override fun isPhySeedExist(): Boolean {
        return SprdApiInst.getInstance().mIsSeedPhyCardExist
    }

    override fun isCloudPhyCardExist(): Boolean {
        return SprdApiInst.getInstance().mIsCloudPhyCardExist
    }
}
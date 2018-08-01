package com.ucloudlink.refact.systemapi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.android.internal.telephony.IExtTelephony
import com.ucloudlink.framework.flow.ISeedCardNet
import com.ucloudlink.framework.flow.ISeedCardNetCtrl
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.FlowImpl
import com.ucloudlink.refact.business.flow.ICloudFlowCtrl
import com.ucloudlink.refact.business.flow.IFlow
import com.ucloudlink.refact.business.flow.ISeedFlowCtrl
import com.ucloudlink.refact.business.flow.netlimit.ExtraNetworkImpl
import com.ucloudlink.refact.business.flow.netlimit.IExtraNetwork
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.SeedCardNetImpl
import com.ucloudlink.refact.business.flow.netlimit.uidnet.IUidSeedCardNet
import com.ucloudlink.refact.business.flow.netlimit.uidnet.UidSeedCardNetImpl
import com.ucloudlink.refact.business.flow.protection.CloudFlowProtectionCtrlImpl
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl
import com.ucloudlink.refact.business.flow.speedlimit.INetSpeed
import com.ucloudlink.refact.business.flow.speedlimit.NetSpeedImpl
import com.ucloudlink.refact.business.s2ccmd.logexecutor.ILogExecutor
import com.ucloudlink.refact.channel.enabler.simcard.CloudSimEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.SeedEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.CardController
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.IPlatformTransfer
import com.ucloudlink.refact.model.BaseModel
import com.ucloudlink.refact.platform.qcom.flow.QcCloudFlowCtrl
import com.ucloudlink.refact.platform.qcom.flow.QcSeedFlowCtrlImpl
import com.ucloudlink.refact.product.ProductForm
import com.ucloudlink.refact.product.mifi.MifiProductForm
import com.ucloudlink.refact.product.mifi.PhyCardApn.ApnNetworkErrCB
import com.ucloudlink.refact.product.module.ModuleProductForm
import com.ucloudlink.refact.product.phone.PhoneProductForm
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum
import com.ucloudlink.refact.systemapi.struct.FileWapper
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.utils.JLog.*
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.util.*

/**
 * Created by shiqianhua on 2018/1/6.
 */
open class SystemApiBase(context: Context, modelInfo: ModelInfo, sdkInt: Int) : SystemApiIf {
    private val mModelInfo = modelInfo
    val sdk = sdkInt
    val ctx = context

    private val SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub"

    override fun isUnderDevelopMode(): Boolean = false

    override fun isUseKeyNativeApi(): Boolean = false

    override fun getDefaultDataSubId(): Int {
        if (sdk > M) {
            val getDefaultDataSubscriptionId = SubscriptionManager::class.java.getMethod("getDefaultDataSubscriptionId")
            val dds = getDefaultDataSubscriptionId.invoke(null)
            return dds as Int
        } else {
            return SubscriptionManager.getDefaultDataSubId()
        }
    }

    override fun setDefaultDataSubId(subId: Int): Int {
        val mSubscriptionManager = SubscriptionManager.from(ctx)
        mSubscriptionManager.setDefaultDataSubId(subId)
        return 0
    }

    override fun saveDefaultDataSubId(subId: Int, slot: Int): Int {
        val ret = Settings.Global.putInt(ServiceManager.appContext.contentResolver, SETTING_USER_PREF_DATA_SUB, subId)
        logv("restoreDefaultDataChoice for android orgin database ret:" + ret)
        if (ret) return 0 else return -1
    }

    override fun setDefaultSmsSubId(subId: Int, slot: Int): Int {
        val mSubscriptionManager = SubscriptionManager.from(ctx)
        mSubscriptionManager.setDefaultSmsSubId(subId)
        return 0
    }

    @SuppressLint("MissingPermission")
    override fun setVoiceSlotId(slot: Int): Int {
        val mTelecomManager = TelecomManager.from(ctx)
        logv("restoreDefaultVoiceChoice for android orgin slotIndex:" + slot)
        val phoneAccountHandles = mTelecomManager.getCallCapablePhoneAccounts()
        mTelecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccountHandles.get(slot))
        return 0
    }

    override fun setDataRoaming(subId: Int, roam: Boolean): Int {
        logv("setDataRoaming subId:$subId roam:$roam")
        val mSubscriptionManager = SubscriptionManager.from(ctx)
        val dataRoaming: Int = if (roam) 1 else 0
        return mSubscriptionManager.setDataRoaming(dataRoaming, subId)
    }

    override fun setPreferredNetworkType(slot: Int, subId: Int, networkType: Int): Int {
        val telemanager = TelephonyManager.from(ctx)
        val ret = telemanager.setPreferredNetworkType(subId, networkType)
        if (ret) return 0 else return -1
    }

    override fun resetPreferredNetworkType(slot: Int): Int {
        return -1
    }

    override fun setDefaultDataSlotId(slot: Int): Int {
        val sublist = SubscriptionManager.getSubId(slot)
        if (sublist != null && sublist.size != 0) {
            logd("set dds to subid ${sublist[0]}")
            val mSubscriptionManager = SubscriptionManager.from(ctx)
            mSubscriptionManager.setDefaultDataSubId(sublist[0])
            return 0
        }
        return -1
    }

    override fun setPreferredNetworkTypeToGlobal(): Boolean {
        val mTelephonyManager = ctx
                .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return mTelephonyManager.setPreferredNetworkTypeToGlobal();
    }

    override fun setDataEnabled(slot: Int, subId: Int, isDataEnabled: Boolean) {
        val mTelephonyManager = ctx
                .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        mTelephonyManager.setDataEnabled(subId, isDataEnabled)
    }

    @SuppressLint("NewApi")
    override fun getSimState(slot: Int): Int {
        val mTelephonyManager = ctx
                .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return mTelephonyManager.getSimState(slot)
    }

    override fun wirteVirtImei(context: Context, slot: Int, virtualImei: String): Int {
        return 0
    }

    override fun recoveryImei(context: Context, slot: Int): Int {
        return 0
    }

    override fun getDeniedReasonByState(phoneId:Int, state: ServiceState): Int {
        return 0
    }

    override fun geDeniedReasonTime(phoneId: Int): Long {
        return 0
    }

    override fun isNewReason(phoneId: Int, time: Long): Boolean {
        return true
    }

    override fun getNetTypeDelayTime(): Long {
        return 2000
    }

    override fun getSimEnableState(slot: Int): Int {
        if (sdk > M) {
            val subscriptionInfo = SubscriptionManager.from(ctx).getActiveSubscriptionInfoForSimSlotIndex(slot)
            subscriptionInfo ?: return -2
            val getLine1Number = TelephonyManager::class.java.getMethod("getLine1Number", Int::class.java)
            val lineNumber = getLine1Number.invoke(TelephonyManager.from(ctx), subscriptionInfo.subscriptionId) as String?
            return 1//if(TextUtils.isEmpty(lineNumber)) 0 else 1
        } else {
            val mExtTelephony: IExtTelephony? = IExtTelephony.Stub.asInterface(android.os.ServiceManager.getService("extphone"))

            if (slot > -1 && mExtTelephony != null) {
                return mExtTelephony.getCurrentUiccCardProvisioningStatus(slot)
            } else {
                return 1
            }
        }
    }

    override fun getNetworkOperatorForSubscription(subId: Int): String {
        if (sdk > M) {
            val telephonyManager = TelephonyManager.from(ctx)
            val getNetworkOperator = telephonyManager.javaClass.getMethod("getNetworkOperator", Int::class.java)
            val Operator = getNetworkOperator.invoke(telephonyManager, subId) as String
            return Operator
        } else {
            return TelephonyManager.from(ctx).getNetworkOperatorForSubscription(subId)
        }
    }


    override fun getIccOperatorNumericForData(subId: Int): String {
        val telephonyManager = TelephonyManager.from(ctx)
        if (telephonyManager == null) {
            loge("telephonyManager is null")
            return ""
        }
        if (sdk > M) {
            val getSimOperatorNumeric = telephonyManager.javaClass.getMethod("getSimOperatorNumeric", Int::class.java)
            val numeric = getSimOperatorNumeric.invoke(telephonyManager, subId)
            return numeric as String
        } else {
            val numeric = telephonyManager.getIccOperatorNumericForData(subId)
            if (numeric == null) {
                return ""
            }
            return numeric
        }
    }

    override fun getServiceStateForSubscriber(subId: Int): ServiceState? {
        val telephonyManager = TelephonyManager.from(ctx)
        if (telephonyManager == null) {
            loge("telephonyManager is null")
            return null
        }
        if(sdk > M){
            try {
                val getServiceState = telephonyManager.javaClass.getMethod("getServiceStateForSubscriber", Int::class.java)
                val state = getServiceState.invoke(telephonyManager, subId)
                return state as ServiceState
            }catch (e:Exception){
                e.printStackTrace()
                return null
            }
        }else{
            // todo: need check for
            return null
        }
    }

    override fun getSubscriberIdBySlot(slot: Int): String {
        val telephonyManager = TelephonyManager.from(ctx)
        if (telephonyManager == null) {
            loge("telephonyManager is null")
            return ""
        }
        try{
            if(sdk > M) {
                val getImsiBySlot = telephonyManager.javaClass.getMethod("getSubscriberIdForSlotIdx", Int::class.java)
                val imsi = getImsiBySlot.invoke(telephonyManager, slot) as String
                return imsi
            }else{
                val subList = SubscriptionManager.getSubId(slot)
                if(subList == null || subList.size == 0){
                    return ""
                }
                return telephonyManager.getSubscriberId(subList[0])
            }
        }catch (e:NoSuchMethodException){
            e.printStackTrace()
        }catch (e:IllegalAccessException){
            e.printStackTrace()
        }catch (e: InvocationTargetException){
            e.printStackTrace()
        }
        return ""
    }

    override fun getSubIdBySlotId(slot: Int): Int {
        val subList = SubscriptionManager.getSubId(slot)
        logd("SubscriptionManager.getSubId get subid by slot ($slot) subid:" + if(subList.size > 0) subList[0] else " null")
        if(subList == null || subList.size == 0){
            return -1
        }
        return subList[0]
    }

    override fun getNetworkOperator(slot: Int): String {
        val telephonyManager = TelephonyManager.from(ctx)
        val subList = SubscriptionManager.getSubId(slot)
        if(subList == null || subList.size == 0){
            return ""
        }
        if(sdk > M){
            val getNetworkOperator = telephonyManager.javaClass.getMethod("getNetworkOperator", Int::class.java)
            val imsi = getNetworkOperator.invoke(telephonyManager, subList[0]) as String
            return imsi
        }else {
            return telephonyManager.getNetworkOperatorForSubscription(subList[0])
        }
    }

    override fun isFrameworkSupportSeedNetworkLimitByUidAndIp(): Boolean {
        return SysUtils.isFrameworkSupportSeedNetworkLimitByUidAndIp()
    }

    /** ------------------------- */

    override fun getModemLogEnable(): Boolean {
        return true
    }

    override fun getTcpdumpEnable(): Boolean {
        return false
    }

    override fun getExtLibs(): ArrayList<FileWapper> {
        var extLibs = ArrayList<FileWapper>()
        if (sdk > Build.VERSION_CODES.M) {
            extLibs.add(FileWapper("libc++.so", "libc++.so", "libc++.so"))
        }

        val libs = arrayOf(
                //TODO:lib必须按照顺序载入，需要优化
                FileWapper("libucssl-sign.so", "libucssl-sign.so", "libucssl-sign.so"),
                FileWapper("libsoftsim.so", "libsoftsim.so", "libsoftsim.so"),
                FileWapper("libsoftsim-adapter.so", "libsoftsim-adapter.so", "libsoftsim-adapter.so"),
                FileWapper("libuc.so", "libuc.so", "libuc.so")
        )

        for (l in libs) {
            extLibs.add(l)
        }

        return extLibs
    }

    override fun getSimCommFiles(): ArrayList<FileWapper> {
        val libs = arrayListOf(
                FileWapper("server.crt", "server.crt", "server.crt")
                //FileWapper("libdyn-common.so","libdyn-common.so","libdyn-common.so")
        )

        return libs
    }

    override fun getModemCfgFiles(): ArrayList<FileWapper> {
        return ArrayList()
    }

    override fun getSSLVersion(): Int {
        return 0x3022001
    }

    override fun checkCardCanBeSeedsim(slot: Int): Boolean {
        return true
    }


    /****************************************************************/
    override fun getSeedEnabler(context: Context, looper: Looper): SeedEnabler2 {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun startModemLog(arg1: Int, arg2: Int, obj: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stopModemLog(arg1: Int, arg2: Int, obj: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun initPlatfrom(context: Context) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clearModemLog(arg1: Int, arg2: Int, obj: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uploadLog(obj: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCloudSimEnabler(context: Context, looper: Looper): CloudSimEnabler2 {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getISeedCardNetCtrl(isFrameworkSupportSeedNetLimitByUidAndIP: Boolean, isUiSupportSeedNetLimitByUidAndIP: Boolean): ISeedCardNetCtrl {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCardController(): CardController {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getPlatformTransfer(): IPlatformTransfer {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getILogExecutor(): ILogExecutor {
        return object : ILogExecutor {

            override fun uploadCommonLog(param: S2c_upload_log_file) {

            }

            override fun uploadQXDMLog(param: S2c_upload_log_file) {

            }

            override fun uploadUCLog(param: S2c_upload_log_file) {

            }

            override fun deleteUCLog() {

            }

            override fun deleteQXDMLog() {

            }

            override fun deleteCommonLog() {

            }

            override fun deleteRadioLog() {

            }
        }
    }

    override fun getICloudFlowCtrl(): ICloudFlowCtrl {
        return QcCloudFlowCtrl()
    }

    override fun getCloudIFlow(): IFlow {
        return FlowImpl()
    }

    override fun getSeedIFlow(): IFlow {
        return FlowImpl()
    }

    override fun getINetSpeed(): INetSpeed {
        return NetSpeedImpl()
    }

    override fun getIExtraNetwork(): IExtraNetwork {
        return ExtraNetworkImpl()
    }

    override fun getIUidSeedCardNet(): IUidSeedCardNet {
        return UidSeedCardNetImpl()
    }

    override fun getISeedCardNet(): ISeedCardNet {
        return SeedCardNetImpl()
    }

    override fun getICloudFlowProtectionCtrl(): ICloudFlowProtectionCtrl {
        return CloudFlowProtectionCtrlImpl()
    }

    override fun getISeedFlowCtrl(): ISeedFlowCtrl {
        return QcSeedFlowCtrlImpl()
    }

    override fun getCloudCardNetPreIfName(): String? {
        return "rmnet_data0"
    }

    /*************************************** product ****************************/
    override fun getProductObj(context: Context): ProductForm {
        when (mModelInfo.product) {
            ProductTypeEnum.MIFI -> {
                var product = MifiProductForm(context)
                return product
            }
            ProductTypeEnum.PHONE -> {
                var product = PhoneProductForm(context)
                return product
            }
            ProductTypeEnum.MODULE ->{
                var product = ModuleProductForm(context)
                return product
            }

        }
        var product = MifiProductForm(context)
        return product
    }


    // connectivitymanager

    override fun registerDefaultNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback): Int {
        val mConnManager = ConnectivityManager.from(ctx)
        if (sdk > M) {
            logd("logd sdk $sdk  support it")
            try {
                val registerCallback = mConnManager.javaClass.getMethod("registerDefaultNetworkCallback", ConnectivityManager.NetworkCallback::class.java)
                registerCallback.invoke(mConnManager, networkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
                return -1
            }
            return 0
        } else {
            loge("do not support!!!")
            return -1
        }
    }

    override fun unregisterNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback): Int {
        try {
            val mConnManager = ConnectivityManager.from(ctx)
            mConnManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
            loge("unregisterNetworkCallback $networkCallback failed!")
            return -1
        }
        return 0
    }

    override fun isSupportSwitchDDS(): Boolean {
        return false
    }

    override fun getModelIf(context: Context): ModelIf {
        return BaseModel(context)
    }

    override fun switchSeedMode(): Int {
        return 0
    }

    override fun isPhySeedExist(): Boolean {
        return false
    }

    override fun isCloudPhyCardExist(): Boolean {
        return false
    }

    private val networkErrCBS = ArrayList<ApnNetworkErrCB>()

    override fun registerNetworkErrCB(cb: ApnNetworkErrCB) {
        synchronized(networkErrCBS) {
            networkErrCBS.add(cb)
        }
    }

    override fun deregsiterNetworkErrCB(cb: ApnNetworkErrCB) {
        synchronized(networkErrCBS) {
            networkErrCBS.remove(cb)
        }
    }

    override fun updateNetworkErrCode(phoneId: Int, errcode: Int) {
        synchronized(networkErrCBS) {
            for (cb in networkErrCBS) {
                cb.networkErrUpdate(phoneId, errcode)
            }
        }
    }

    override fun getDnsServers(networkType: Int): ArrayList<InetAddress>? {
        val mConnManager = ConnectivityManager.from(ctx)
        val curLinkProps = mConnManager.getLinkProperties(networkType)
        if (curLinkProps == null) {
            loge("getCurLinkProperties:: LP for type$networkType is null!")
            return null
        }

        val dnses = curLinkProps!!.getDnsServers()
        if (dnses == null || dnses!!.size == 0) {
            loge("getDns::LinkProps has null dns - returning default")
            return null
        }

        return ArrayList<InetAddress>(dnses!!)
    }

    override fun isSupportSmartRecovery(): Boolean {
        return false
    }
}
package com.ucloudlink.refact.business.statebar

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.telephony.SubscriptionManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessState
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.access.restore.ServiceRestore


/**
 * Created by haiping.liu on 2017/8/27.
 */
object NoticeStatusBarServiceStatus {
    const val STATUS_STOP:Int = 1
    const val STATUS_RUNNING:Int = 2
    const val STATUS_SUCCESS:Int = 3
    var dsdsServiceStatus:Int = STATUS_STOP
    var isFirstStart = true
    var curPersent = 0
    //通知状态栏dsds server 状态改变
    fun registerCb() {
        val mAccessListener = AccessStateListener()
        val accessEntry = ServiceManager.accessEntry
        accessEntry.registerAccessStateListen(mAccessListener)

        val persents = accessEntry.accessState.systemPersent
        noticStatusBarServiceStatus(getDsdsServiceStatus(persents))
    }

    private class AccessStateListener : AccessState.AccessStateListen {
        override fun processUpdate(persent: Int) {
            noticStatusBarServiceStatus(getDsdsServiceStatus(persent))
        }

        override fun eventCloudSIMServiceStop(reason: Int, message: String?) {
        }

        override fun eventCloudsimServiceSuccess() {
        }

        override fun eventSeedState(persent: Int) {
        }

        override fun eventSeedError(code: Int, message: String?) {
        }

        override fun errorUpdate(errorCode: Int, message: String) {
        }
    }

    fun noticStatusBarServiceStatus(status: Int) {
        if (status != STATUS_STOP && status == dsdsServiceStatus){
            return
        }
        dsdsServiceStatus = status
        if (dsdsServiceStatus == STATUS_SUCCESS){
            isFirstStart = false
        }else if (dsdsServiceStatus == STATUS_STOP){
            isFirstStart = true
        }
        JLog.logd("curPersent=$curPersent ,isExceptionStart:" + ServiceRestore.isExceptionStart(ServiceManager.appContext) + ",isRecordExist:" + RunningStates.isRecordExist())
        //异常状态云卡图标不闪烁
        if (curPersent <= 5){
            if (ServiceManager.productApi.needRecovery() && ServiceRestore.isExceptionStart(ServiceManager.appContext) && RunningStates.isRecordExist()) {
                isFirstStart = false
            }
        }

        var intent = Intent("ukelink.spn.show.icon")
        intent.putExtra("cloudsimSlot", Configuration.cloudSimSlot)
        intent.putExtra("dsdsServiceStatus",status)
        intent.putExtra("isFirstStart", isFirstStart)
        ServiceManager.appContext.sendBroadcast(intent)
        JLog.logd("showspn noticStatusBarServiceStatus cloudsimSlot=${Configuration.cloudSimSlot} , dsdsServiceStatus=${status} , isfirststart=${isFirstStart}}")
    }

    fun noticFrameworkSpnName(virtImsi: String) {
        var intent = Intent("ukelink.spn.show")
        val spnName = SharedPreferencesUtils.getString(ServiceManager.appContext, "spnName", "GlocalMe")
        JLog.logd("showspn noticFrameworkSpnName virtName=${virtImsi} , virtImsi=${virtImsi} , spnName=${spnName}")
        intent.putExtra("virtName", spnName)
        intent.putExtra("virtImsi", virtImsi)
        ServiceManager.appContext.sendBroadcast(intent)
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (wifiNetworkInfo.isConnected()) {
            return true
        }
        return false
    }

    fun  getDsdsServiceStatus(persent:Int):Int{
        curPersent = persent
        if (persent == 0){
            return STATUS_STOP
        }else if (persent > 0 && persent < 90) {
            return STATUS_RUNNING
        } else if (persent == 90) {
            if (isWifiConnected(ServiceManager.appContext)){
                JLog.logd("showspn ------------------Wifi Connected & persent=90%---------------")
                return STATUS_SUCCESS
            }else{
                return STATUS_RUNNING
            }
        } else if (persent > 90 && persent < 100) {
            return STATUS_RUNNING
        } else if (persent == 100) {
            return STATUS_SUCCESS
        }
        return STATUS_STOP
    }

    fun getCloudsimSub(): Int {
        var subid:Int = -1
        val mSubscriptionManager = SubscriptionManager.from(ServiceManager.appContext)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(Configuration.cloudSimSlot)
        if(mSubscriptionInfo != null){
            subid = mSubscriptionInfo.subscriptionId
        }
        JLog.logd("showspn getCloudsimSub subid="+subid)
        return subid
    }
}
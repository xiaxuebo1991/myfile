package com.ucloudlink.refact.product.mifi.flow.protection

import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtection
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionActionType
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionXML
import com.ucloudlink.refact.product.mifi.flow.protection.entity.ProCfgResponseData
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import android.os.Looper.getMainLooper
import com.android.internal.view.WindowManagerPolicyThread.getThread



/**
 * Created by jianguo.he on 2018/4/11.
 */
class MifiCloudFlowProtectionCtrlImpl_v2:ICloudFlowProtectionCtrl {

    private val MSG_NET_STATE_CHANGE = 1
    private val MSG_UPDATE_XML = 21

    private val MSG_SET_RETRICT_WITH = 31
    private val MSG_CLEAR_RETRICT_WITH = 32

    private var curCloudIfName: String? = null
    private val retrictLock = Any()
    private val retrictLockWith = Any()
    private var isInRetrict = false
    private var hasSetRetrictWith = false

    private val mIU3CCloudFlowProtection by lazy { MifiCloudFlowProtectionImpl()}

    private val mXMLDownloadHolder by lazy { MifiCloudFlowProtectionXMLDownloadHolder() }

    // TODO 需要做XML更新时，服务器是否开启了流量防护标记，需考虑与WEB开关的兼容

    private val mHandler = object : Handler() {
        override fun handleMessage(msg: Message?) {
            JLog.logi("Here is handlemessage")
                    if (msg == null) {
                super.handleMessage(msg)
            } else {
                when (msg.what) {
                    MSG_NET_STATE_CHANGE ->{
                        if (msg.obj != null && msg.obj is HandlerMsg) {
                            handMsgNetStateChange(msg.obj as HandlerMsg)
                        }
                    }

                    MSG_UPDATE_XML -> {
                        JLog.logi("CloudFlowProtectionLog, cloudSimEnabler.netState =  " + ServiceManager.cloudSimEnabler.getNetState())
                        if (ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED) {
                            mXMLDownloadHolder.handleActionGetConfig(ServiceManager.appContext,this@MifiCloudFlowProtectionCtrlImpl_v2)
                        }
                    }

                    MSG_SET_RETRICT_WITH -> {
                        synchronized(retrictLockWith){
                            JLog.logi("CloudFlowProtectionLog, setRetrictWith() -> MSG_SET_RETRICT_WITH -> hasSetRetrictWith = $hasSetRetrictWith")
                            if(!hasSetRetrictWith){
                                if(msg.obj!=null && msg.obj is MifiCloudFlowProtectionXML){
                                    val mU3CFlowProtectionXML = msg.obj as  MifiCloudFlowProtectionXML
                                    setRetrictWith(curCloudIfName, mU3CFlowProtectionXML)
                                }
                            }
                        }

                    }

                    MSG_CLEAR_RETRICT_WITH ->{
                        synchronized(retrictLockWith){
                            this@MifiCloudFlowProtectionCtrlImpl_v2.clearAllRules("-MSG_CLEAR_RETRICT_WITH-")
                        }
                    }
                }
            }
        }
    }

    private val networkListen = CardStateMonitor.NetworkStateListen { ddsId, state, type, ifName, isExistIfNameExtra, subId ->
        JLog.logi("CloudFlowProtectionLog NetworkStateListen -> ddsId = " + ddsId + ", networkState = " + state +
                ", type = " + type + ", ifName = " + (ifName ?: "null") +
                ", isExistIfNameExtra = " + isExistIfNameExtra + ", subId = " + subId)
        mHandler.obtainMessage(MSG_NET_STATE_CHANGE, HandlerMsg(ddsId, state, type, ifName, isExistIfNameExtra, subId)).sendToTarget()
    }

    fun getLocalLooper(): Looper? {
        val sLooper: Looper
        if (Looper.myLooper() == null) {
            Looper.prepare()

            JLog.logi("This thread has no looper, create one and return it")

        }
        sLooper = Looper.myLooper()
        return sLooper
       // JLog.logi("This thread has looper already don't need to create")
        //
    }

    private fun handMsgNetStateChange(msg: HandlerMsg) {
        val isWebEnableFlowProtection = FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.webEnableFlowProtection
        JLog.logi("CloudFlowProtectionLog, curCloudIfName =  " + (if (curCloudIfName == null) "null" else curCloudIfName)
                + ", " + msg.toString() + ", isWebEnableFlowProtection = " + isWebEnableFlowProtection)
        if (msg.isExistIfNameExtra) {
            if (msg.subId > -1 && msg.subId == ServiceManager.cloudSimEnabler.getCard().subId) {
                JLog.logi("ifname is exist" )
                if (msg.state == NetworkInfo.State.CONNECTED) {
                    JLog.logi("network is connected")
                    SharedPreferencesUtils.putInt(ServiceManager.appContext, "pro_data_first_state", 0);
                    mHandler.removeMessages(MSG_UPDATE_XML)
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_XML, (60 * 1000).toLong())
                    SharedPreferencesUtils.putInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_SYNC_SIGNAL, 0)
                    JLog.logi("judgement")
                    val isFlowProtectionStatusChange = SharedPreferencesUtils.getInt(ServiceManager.appContext,MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_DATA_STATUS, 1)
                    JLog.logi("CloudFlowProtectionLog, curCloudIfName =  " + (if (curCloudIfName == null) "null" else curCloudIfName)
                            + ", " + msg.toString() + ", isStatusChange = " + isFlowProtectionStatusChange)
                    if (!TextUtils.isEmpty(curCloudIfName)) {// 不为空，已经处于限制状态
                        if (TextUtils.isEmpty(msg.ifName)) {// 清除限制
                            this@MifiCloudFlowProtectionCtrlImpl_v2.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(1)")
                            curCloudIfName = msg.ifName
                        } else if (curCloudIfName != msg.ifName) {
                            SharedPreferencesUtils.putInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_SYNC_SIGNAL, 1)
                            this@MifiCloudFlowProtectionCtrlImpl_v2.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(2)")
                            curCloudIfName = msg.ifName
                         //   if (isWebEnableFlowProtection) {
                            if (isFlowProtectionStatusChange == 1) {
                                this@MifiCloudFlowProtectionCtrlImpl_v2.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(3)")
                            }
                        } else {
                            SharedPreferencesUtils.putInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_SYNC_SIGNAL, 1)
                            this@MifiCloudFlowProtectionCtrlImpl_v2.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(4)")
                         //   if (isWebEnableFlowProtection) {
                            if (isFlowProtectionStatusChange == 1) {
                                this@MifiCloudFlowProtectionCtrlImpl_v2.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(5)")
                            }
                        }
                    } else {
                        curCloudIfName = msg.ifName
                        if (!TextUtils.isEmpty(curCloudIfName)) {// 设置限制
                            SharedPreferencesUtils.putInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_SYNC_SIGNAL, 1)
                    //        if (isWebEnableFlowProtection) {
                            if (isFlowProtectionStatusChange == 1) {
                                this@MifiCloudFlowProtectionCtrlImpl_v2.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(6)")
                            }
                        }
                    }

                } else if (msg.state == NetworkInfo.State.DISCONNECTED) {
                    if (!TextUtils.isEmpty(curCloudIfName)) {
                        this@MifiCloudFlowProtectionCtrlImpl_v2.clearRetrict("NetworkStateListen-DISCONNECTED-isNotEmpty(7)")
                    }
                    curCloudIfName = null
                }

            } else {
                if (msg.state == NetworkInfo.State.CONNECTED) {
                    if (!TextUtils.isEmpty(curCloudIfName) && !TextUtils.isEmpty(msg.ifName) && curCloudIfName == msg.ifName) {
                        // 清除
                        this@MifiCloudFlowProtectionCtrlImpl_v2.clearRetrict("NetworkStateListen-CONNECTED-equals(8)")
                        curCloudIfName = null
                    }
                } else if (msg.state == NetworkInfo.State.DISCONNECTED) {

                }
            }
        }
    }
    override fun getICloudFlowProtection(): ICloudFlowProtection {
        return mIU3CCloudFlowProtection
    }

    override fun init(cloudSimEnabler: IDataEnabler) {
        JLog.logi("CloudFlowProtectionLog", "init() ++ ")
        ServiceManager.simMonitor.addNetworkStateListen(networkListen)
        //注释说明：为了优化启动速度，把mU3CFlowProtectionXML改成属性，并且在使用时（get()）再初始化
//        var mU3CFlowProtectionXML = MifiXMLUtils.readFromSP()
//        if (mU3CFlowProtectionXML == null) {
//            mU3CFlowProtectionXML = MifiXMLUtils.readDefaultFile()
//        }
//
//        FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mU3CFlowProtectionXML = mU3CFlowProtectionXML
        clearAllRules("-init")
//        JLog.logi("CloudFlowProtectionLog", "init() -> " + if (mU3CFlowProtectionXML == null) "MifiCloudFlowProtectionXML = NULL" else mU3CFlowProtectionXML.toString().length)
    }

     override fun setRetrict(tag: String?) {
        synchronized(retrictLock) {
            isInRetrict = true
            val mXMLU3CFlowProtection = FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mU3CFlowProtectionXML
            if (mXMLU3CFlowProtection == null) {
                JLog.logi("CloudFlowProtectionLog", "setRetrict() -> tag = " + (tag ?: "null") + ", getXMLU3CFlowProtection() return NULL")
            } else {
                JLog.logi("CloudFlowProtectionLog", "setRetrict() -> tag = " + (tag ?: "null") + ", curCloudIfName = " + if (curCloudIfName == null) "null" else curCloudIfName)
                SharedPreferencesUtils.putString(ServiceManager.appContext, MifiXMLUtils.SPRD_U3C_CUR_UCLOUD_IFNAME_SP_KEY, curCloudIfName)
                val mU3CFlowProtectionXML = FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.copyU3CFlowProtectionXML()
                MifiXMLUtils.saveToSP(mU3CFlowProtectionXML)
                getICloudFlowProtection().enableFlowfilter()
                //setRetrictWith(curCloudIfName, mU3CFlowProtectionXML)
                if(!hasSetRetrictWith){
                    mHandler.removeMessages(MSG_SET_RETRICT_WITH)
                    mHandler.obtainMessage(MSG_SET_RETRICT_WITH, mU3CFlowProtectionXML).sendToTarget()
                }
            }
        }
    }


    private fun setRetrictWith(ifName: String?, mU3CFlowProtectionXML: MifiCloudFlowProtectionXML?) {
        JLog.logi("CloudFlowProtectionLog", "setRetrictWith() -> ifName = " + (ifName ?: "null")
                + ", " + (mU3CFlowProtectionXML?.toString()?.length ?: "mU3CFlowProtectionXML=null"))
        if (!TextUtils.isEmpty(ifName) && mU3CFlowProtectionXML != null && mU3CFlowProtectionXML.listItem != null) {
            setFlagRetrictWith(true)
            for (item in mU3CFlowProtectionXML.listItem) {
                if (item == null) {
                    continue
                }

                if (item.actiontype == MifiCloudFlowProtectionActionType.DNS_BLOCK.type) {
                    if (!TextUtils.isEmpty(item.dnsreq)) {
                        val protocol = if (TextUtils.isEmpty(item.protocol_type)) MifiXMLUtils.U3C_XML_DEF_PROTOCOL else item.protocol_type
                        val dport = if (item.dport == MifiXMLUtils.U3C_XML_PARSE_INT_ERROR_VALUE) MifiXMLUtils.U3C_XML_DEF_DPORT else item.dport
                        getICloudFlowProtection().setFlowfilterDomainRule(item.dnsreq, protocol, dport, item.cmpmode, false)
                    }
                } else if (item.actiontype == MifiCloudFlowProtectionActionType.IP_BLOCK.type) {
                    if (!TextUtils.isEmpty(item.dip)) {
                        getICloudFlowProtection().setFlowfilterAddrRule(item.dip, false)
                    }
                }
            }
        }
    }

    override fun clearRetrict(tag: String?) {
        synchronized(retrictLock) {
            isInRetrict = false
            JLog.logi("CloudFlowProtectionLog, clearRetrict() -> tag = " + (tag ?: "null") + ", curCloudIfName = " + if (curCloudIfName == null) "null" else curCloudIfName)
            SharedPreferencesUtils.putString(ServiceManager.appContext, MifiXMLUtils.SPRD_U3C_CUR_UCLOUD_IFNAME_SP_KEY, "")
            MifiXMLUtils.saveToSP(null)
            getICloudFlowProtection().disableFlowfilter()
            //clearRetrictWith(curCloudIfName, FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().copyU3CFlowProtectionXML());
            //getICloudFlowProtection().clearAllRules()//
            //mHandler.obtainMessage(MSG_CLEAR_RETRICT_WITH).sendToTarget()
        }
    }


    private @Synchronized fun setFlagRetrictWith(flag: Boolean){
        hasSetRetrictWith = flag
    }

    fun clearAllRules(tag: String){
        JLog.logi("CloudFlowProtectionLog, clearAllRules() -> tag = $tag")
        setFlagRetrictWith(false)
        getICloudFlowProtection().clearAllRules()
    }

    private fun clearRetrictWith(ifName: String?, mU3CFlowProtectionXML: MifiCloudFlowProtectionXML?) {
        JLog.logi("CloudFlowProtectionLog", "clearRetrictWith() -> ifName = " + (ifName ?: "null")
                + ", " + (mU3CFlowProtectionXML?.toString()?.length ?: "mU3CFlowProtectionXML=null"))
        if (!TextUtils.isEmpty(ifName) && mU3CFlowProtectionXML != null && mU3CFlowProtectionXML.listItem != null) {
            setFlagRetrictWith(false)
            for (item in mU3CFlowProtectionXML.listItem) {
                if (item == null) {
                    continue
                }
                if (item.actiontype == MifiCloudFlowProtectionActionType.DNS_BLOCK.type) {
                    if (!TextUtils.isEmpty(item.dnsreq)) {
                        val protocol = if (TextUtils.isEmpty(item.protocol_type)) MifiXMLUtils.U3C_XML_DEF_PROTOCOL else item.protocol_type
                        val dport = if (item.dport == MifiXMLUtils.U3C_XML_PARSE_INT_ERROR_VALUE) MifiXMLUtils.U3C_XML_DEF_DPORT else item.dport
                        getICloudFlowProtection().setFlowfilterDomainRule(item.dnsreq, protocol, dport, item.cmpmode, true)
                    }
                } else if (item.actiontype == MifiCloudFlowProtectionActionType.IP_BLOCK.type) {
                    if (!TextUtils.isEmpty(item.dnsreq)) {
                        getICloudFlowProtection().setFlowfilterAddrRule(item.dip, true)
                    }
                }
            }
            getICloudFlowProtection().disableFlowfilter()
        }
    }

    override fun updateXML(xml: String) {
        val mMifiCloudFlowProtectionXML = MifiXMLUtils.getU3CFlowProtectionXML(xml)
        JLog.logi("CloudFlowProtectionLog", "updateXML() -> isInRetrict = " + isInRetrict
                + ", mMifiCloudFlowProtectionXML = " + (mMifiCloudFlowProtectionXML?.toString()?.length ?: "null"))
        if (mMifiCloudFlowProtectionXML != null) {
            FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mU3CFlowProtectionXML = mMifiCloudFlowProtectionXML
            MifiXMLUtils.saveToSP(mMifiCloudFlowProtectionXML)
            if (isInRetrict) {
                this@MifiCloudFlowProtectionCtrlImpl_v2.clearRetrict("--by updateXML--")
                this@MifiCloudFlowProtectionCtrlImpl_v2.clearAllRules("--by updateXML--")
                this@MifiCloudFlowProtectionCtrlImpl_v2.setRetrict("--by updateXML--")
            }
        }
    }

    //--------------------------
    private inner class HandlerMsg(var ddsId: Int, var state: NetworkInfo.State, var type: Int, var ifName: String, var isExistIfNameExtra: Boolean, var subId: Int) {

        override fun toString(): String {
            return "HandlerMsg{" +
                    "ddsId=" + ddsId +
                    ", state=" + state +
                    ", type=" + type +
                    ", ifName='" + ifName + '\'' +
                    ", isExistIfNameExtra=" + isExistIfNameExtra +
                    ", subId=" + subId +
                    '}'
        }
    }
}
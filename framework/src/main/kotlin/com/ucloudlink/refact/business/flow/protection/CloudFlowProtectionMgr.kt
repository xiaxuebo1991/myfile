package com.ucloudlink.refact.business.flow.protection

import android.net.NetworkInfo
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.product.mifi.flow.protection.MifiCloudFlowProtectionXMLDownloadHolder
import com.ucloudlink.refact.product.mifi.flow.protection.MifiXMLUtils
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionXML
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.SharedPreferencesUtils

/**
 * Created by jianguo.he on 2018/3/1.
 */
class CloudFlowProtectionMgr {

    // useed by handlerServiceEnableFlowProtection()
    var serviceEnableFlowProtection: Boolean
        set(value){
            SharedPreferencesUtils.putInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_DATA_SERVER_STATE, if(value) 1 else 0)
        }
        get(){
            return (SharedPreferencesUtils.getInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_DATA_SERVER_STATE
                    , if(DEF_SERVICE_FLOW_PROTECTION_ENABLE) 1 else 0))== 1
        }

    var webEnableFlowProtection: Boolean
        set(value){
            SharedPreferencesUtils.putBoolean(ServiceManager.appContext, WEB_FLOW_PROTECTION_ENABLE_SP_KEY, value)
        }
        get(){
            return SharedPreferencesUtils.getBoolean(ServiceManager.appContext, WEB_FLOW_PROTECTION_ENABLE_SP_KEY, DEF_WEB_FLOW_PROTECTION_ENABLE)
        }

    var mICloudFlowProtectionCtrl: ICloudFlowProtectionCtrl? = null

    //优化启动速度，这里改成属性，使用时再初始化
    var mU3CFlowProtectionXML: MifiCloudFlowProtectionXML? = null
        get() {
            if (field == null) {
                field = MifiXMLUtils.readFromSP()
                if (field == null) {
                    field = MifiXMLUtils.readDefaultFile()
                }
            }
            return field
        }

    fun copyU3CFlowProtectionXML(): MifiCloudFlowProtectionXML? {
        return if (mU3CFlowProtectionXML == null) null else mU3CFlowProtectionXML!!.copyOf()
    }

    companion object {

        val DEF_SERVICE_FLOW_PROTECTION_ENABLE = true

        val DEF_WEB_FLOW_PROTECTION_ENABLE = true
        val WEB_FLOW_PROTECTION_ENABLE_SP_KEY = "web_flow_protection_enable"


        fun isFlowfilterEnabled(): Boolean {
            var ret = FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.webEnableFlowProtection
            if(ServiceManager.cloudSimEnabler.getNetState()!=NetworkInfo.State.CONNECTED){
                return ret
            }
            var mICloudFlowProtection: ICloudFlowProtection? = null
            if(FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mICloudFlowProtectionCtrl==null){
                var mICtrl : ICloudFlowProtectionCtrl? = null
                if(ServiceManager.systemApi!=null){
                    mICtrl = ServiceManager.systemApi.getICloudFlowProtectionCtrl()
                }
                if(mICtrl!=null){
                    mICloudFlowProtection = mICtrl.getICloudFlowProtection()
                }
            }

            if(mICloudFlowProtection!=null){
                ret = mICloudFlowProtection.isFlowfilterEnabled()
            }
            return ret

        }

        fun handlerWebEnableFlowProtection(){
            val userState_get = SharedPreferencesUtils.getBoolean(ServiceManager.appContext, "web_flow_protection_enable", true)
            JLog.logi("webenable is :"+ userState_get)
            if(!FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.webEnableFlowProtection){
                FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mICloudFlowProtectionCtrl?.clearRetrict("web-enable-flow-protection")
            } else {
                // 云卡在网络没连接时，ifName=null, 导致条件不成立，不设置流量防护
                if(ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED){
                    FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mICloudFlowProtectionCtrl?.setRetrict("web-enable-flow-protection")
                }
            }
        }

        fun handlerServiceEnableFlowProtection(){
            // TODO 需要确定WEB命令，跟Service命令的优先级。
            // WEB命令默认打开流量防护，如果为WEB命令=false表示用户从WEB关闭了流量防护，所以不处理Service下发的命令
            if(FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.serviceEnableFlowProtection){

            } else {

            }
        }

    }
}
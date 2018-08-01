package com.ucloudlink.refact.product.mifi.flow.protection;

import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtection;
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl;
import com.ucloudlink.refact.channel.enabler.IDataEnabler;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionActionType;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionItem;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionXML;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import org.jetbrains.annotations.NotNull;


/**
 * Created by jianguo.he on 2018/2/5.
 */

public class MifiCloudFlowProtectionCtrlImpl implements ICloudFlowProtectionCtrl {

    private static final int MSG_NET_STATE_CHANGE = 1;
    private static final int MSG_UPDATE_XML = 21;

    private String curCloudIfName;
    private byte[] retrictLock = new byte[0];
    private boolean isInRetrict = false;

    private ICloudFlowProtection mIU3CCloudFlowProtection = new MifiCloudFlowProtectionImpl();

    private MifiCloudFlowProtectionXMLDownloadHolder mXMLDownloadHolder;

    // TODO 需要做XML更新时，服务器是否开启了流量防护标记，需考虑与WEB开关的兼容

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg==null){
                super.handleMessage(msg);
            } else {
                switch (msg.what){
                    case MSG_NET_STATE_CHANGE:
                        if(msg.obj!=null && msg.obj instanceof HandlerMsg){
                            handMsgNetStateChange((HandlerMsg)msg.obj);
                        }
                        break;
                    case MSG_UPDATE_XML:
                        JLog.logi("CloudFlowProtectionLog, cloudSimEnabler.netState =  " + (ServiceManager.cloudSimEnabler.getNetState()));
                        if(ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED){
                            if(mXMLDownloadHolder == null){
                                mXMLDownloadHolder = new MifiCloudFlowProtectionXMLDownloadHolder();
                            }
                            mXMLDownloadHolder.handleActionGetConfig(ServiceManager.appContext,MifiCloudFlowProtectionCtrlImpl.this);
                        }
                        break;
                }
            }
        }
    };

    private CardStateMonitor.NetworkStateListen networkListen = new CardStateMonitor.NetworkStateListen(){

        @Override
        public void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
            JLog.logi("CloudFlowProtectionLog NetworkStateListen -> ddsId = "+ddsId+", networkState = " + state +
                    ", type = "+type+", ifName = "+(ifName==null?"null":ifName)+
                    ", isExistIfNameExtra = "+isExistIfNameExtra+", subId = "+subId);
            mHandler.obtainMessage(MSG_NET_STATE_CHANGE, new HandlerMsg(ddsId, state, type, ifName, isExistIfNameExtra, subId)).sendToTarget();
        }
    };

    private void handMsgNetStateChange(HandlerMsg msg){
        boolean isWebEnableFlowProtection = FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().getWebEnableFlowProtection();
        JLog.logi("CloudFlowProtectionLog, curCloudIfName =  " + (curCloudIfName==null?"null":curCloudIfName)
                + ", "+msg.toString() + ", isWebEnableFlowProtection = "+ isWebEnableFlowProtection);

        if (msg.isExistIfNameExtra) {
            if(msg.subId > -1 && msg.subId == ServiceManager.cloudSimEnabler.getCard().getSubId()){
                if(msg.state == NetworkInfo.State.CONNECTED){
                    mHandler.removeMessages(MSG_UPDATE_XML);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_XML, 60 * 1000);

                    if(!TextUtils.isEmpty(curCloudIfName)){// 不为空，已经处于限制状态
                        if(TextUtils.isEmpty(msg.ifName)){// 清除限制
                            MifiCloudFlowProtectionCtrlImpl.this.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(1)");
                            curCloudIfName = msg.ifName;
                        } else if(!curCloudIfName.equals(msg.ifName)){
                            MifiCloudFlowProtectionCtrlImpl.this.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(2)");
                            curCloudIfName = msg.ifName;
                            if(isWebEnableFlowProtection){
                                MifiCloudFlowProtectionCtrlImpl.this.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(3)");
                            }
                        } else {
                            MifiCloudFlowProtectionCtrlImpl.this.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(4)");
                            if(isWebEnableFlowProtection){
                                MifiCloudFlowProtectionCtrlImpl.this.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(5)");
                            }
                        }
                    } else {
                        curCloudIfName = msg.ifName;
                        if(!TextUtils.isEmpty(curCloudIfName)){// 设置限制
                            if(isWebEnableFlowProtection){
                                MifiCloudFlowProtectionCtrlImpl.this.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(6)");
                            }
                        }
                    }

                } else if(msg.state == NetworkInfo.State.DISCONNECTED) {
                    if(!TextUtils.isEmpty(curCloudIfName)){
                        MifiCloudFlowProtectionCtrlImpl.this.clearRetrict("NetworkStateListen-DISCONNECTED-isNotEmpty(7)");
                    }
                    curCloudIfName = null;
                }

            } else {
                if(msg.state == NetworkInfo.State.CONNECTED){
                    if(!TextUtils.isEmpty(curCloudIfName) && !TextUtils.isEmpty(msg.ifName) && curCloudIfName.equals(msg.ifName)){
                        // 清除
                        MifiCloudFlowProtectionCtrlImpl.this.clearRetrict("NetworkStateListen-CONNECTED-equals(8)");
                        curCloudIfName = null;
                    }
                } else if(msg.state == NetworkInfo.State.DISCONNECTED){

                }
            }
        }
    }

    @NotNull
    @Override
    public ICloudFlowProtection getICloudFlowProtection() {
        return mIU3CCloudFlowProtection;
    }

    @Override
    public void init(IDataEnabler cloudSimEnabler) {
        JLog.logi("CloudFlowProtectionLog", "init() ++ ");
        ServiceManager.simMonitor.addNetworkStateListen(networkListen);

        MifiCloudFlowProtectionXML mU3CFlowProtectionXML = MifiXMLUtils.readFromSP();
        if(mU3CFlowProtectionXML==null){
            mU3CFlowProtectionXML = MifiXMLUtils.readDefaultFile();
        }

        FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().setMU3CFlowProtectionXML(mU3CFlowProtectionXML);
        JLog.logi("CloudFlowProtectionLog", "init() -> "+(mU3CFlowProtectionXML==null?"MifiCloudFlowProtectionXML = NULL":mU3CFlowProtectionXML.toString().length()));
    }

    @Override
    public void setRetrict(String tag) {
        synchronized (retrictLock){
            isInRetrict = true;
            MifiCloudFlowProtectionXML mXMLU3CFlowProtection = FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().getMU3CFlowProtectionXML();
            if(mXMLU3CFlowProtection==null){
                JLog.logi("CloudFlowProtectionLog", "setRetrict() -> tag = " + (tag==null?"null":tag) + ", getXMLU3CFlowProtection() return NULL");
            } else {
                JLog.logi("CloudFlowProtectionLog", "setRetrict() -> tag = " + (tag==null?"null":tag) + ", curCloudIfName = "+(curCloudIfName==null?"null":curCloudIfName));
                SharedPreferencesUtils.putString(ServiceManager.appContext, MifiXMLUtils.SPRD_U3C_CUR_UCLOUD_IFNAME_SP_KEY, curCloudIfName);
                MifiCloudFlowProtectionXML mU3CFlowProtectionXML = FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().copyU3CFlowProtectionXML();
                MifiXMLUtils.saveToSP(mU3CFlowProtectionXML);
                mIU3CCloudFlowProtection.enableFlowfilter();
                setRetrictWith(curCloudIfName, mU3CFlowProtectionXML);
            }
        }
    }

    private void setRetrictWith(String ifName, MifiCloudFlowProtectionXML mU3CFlowProtectionXML){
        JLog.logi("CloudFlowProtectionLog", "setRetrictWith() -> ifName = "+(ifName==null?"null":ifName)
                +", "+(mU3CFlowProtectionXML==null?"mU3CFlowProtectionXML=null":mU3CFlowProtectionXML.toString().length()));
        if(!TextUtils.isEmpty(ifName) && mU3CFlowProtectionXML != null && mU3CFlowProtectionXML.listItem!=null){
            for(MifiCloudFlowProtectionItem item : mU3CFlowProtectionXML.listItem){
                if(item==null){
                    continue;
                }

                if(item.actiontype == MifiCloudFlowProtectionActionType.DNS_BLOCK.type){
                    if(!TextUtils.isEmpty(item.dnsreq)){
                        String protocol = TextUtils.isEmpty(item.protocol_type) ? MifiXMLUtils.U3C_XML_DEF_PROTOCOL : item.protocol_type;
                        int dport = item.dport== MifiXMLUtils.U3C_XML_PARSE_INT_ERROR_VALUE ? MifiXMLUtils.U3C_XML_DEF_DPORT : item.dport;
                        mIU3CCloudFlowProtection.setFlowfilterDomainRule(item.dnsreq, protocol, dport, item.cmpmode, false);
                    }
                } else if(item.actiontype == MifiCloudFlowProtectionActionType.IP_BLOCK.type){
                    if(!TextUtils.isEmpty(item.dip)){
                        mIU3CCloudFlowProtection.setFlowfilterAddrRule(item.dip, false);
                    }
                }
            }
        }
    }

    @Override
    public void clearRetrict(String tag) {
        synchronized (retrictLock){
            isInRetrict = false;
            JLog.logi("CloudFlowProtectionLog", "clearRetrict() -> tag = " + (tag==null?"null":tag) + ", curCloudIfName = "+(curCloudIfName==null?"null":curCloudIfName));
            SharedPreferencesUtils.putString(ServiceManager.appContext, MifiXMLUtils.SPRD_U3C_CUR_UCLOUD_IFNAME_SP_KEY, "");
            MifiXMLUtils.saveToSP(null);
            mIU3CCloudFlowProtection.disableFlowfilter();
            //clearRetrictWith(curCloudIfName, FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().copyU3CFlowProtectionXML());
            mIU3CCloudFlowProtection.clearAllRules();
        }
    }

    private void clearRetrictWith(String ifName, MifiCloudFlowProtectionXML mU3CFlowProtectionXML){
        JLog.logi("CloudFlowProtectionLog", "clearRetrictWith() -> ifName = "+(ifName==null?"null":ifName)
                +", "+(mU3CFlowProtectionXML==null?"mU3CFlowProtectionXML=null":mU3CFlowProtectionXML.toString().length()));
        if(!TextUtils.isEmpty(ifName) && mU3CFlowProtectionXML != null && mU3CFlowProtectionXML.listItem!=null){
            for(MifiCloudFlowProtectionItem item : mU3CFlowProtectionXML.listItem){
                if(item==null){
                    continue;
                }
                if(item.actiontype == MifiCloudFlowProtectionActionType.DNS_BLOCK.type){
                    if(!TextUtils.isEmpty(item.dnsreq)){
                        String protocol = TextUtils.isEmpty(item.protocol_type) ? MifiXMLUtils.U3C_XML_DEF_PROTOCOL : item.protocol_type;
                        int dport = item.dport== MifiXMLUtils.U3C_XML_PARSE_INT_ERROR_VALUE ? MifiXMLUtils.U3C_XML_DEF_DPORT : item.dport;
                        mIU3CCloudFlowProtection.setFlowfilterDomainRule(item.dnsreq, protocol, dport, item.cmpmode, true);
                    }
                } else if(item.actiontype == MifiCloudFlowProtectionActionType.IP_BLOCK.type){
                    if(!TextUtils.isEmpty(item.dnsreq)){
                        mIU3CCloudFlowProtection.setFlowfilterAddrRule(item.dip, true);
                    }
                }
            }
            mIU3CCloudFlowProtection.disableFlowfilter();
        }
    }

    @Override
    public void updateXML(@NotNull String xml) {
        MifiCloudFlowProtectionXML mMifiCloudFlowProtectionXML = MifiXMLUtils.getU3CFlowProtectionXML(xml);
        JLog.logi("CloudFlowProtectionLog", "updateXML() -> isInRetrict = " + isInRetrict
                + ", mMifiCloudFlowProtectionXML = "+(mMifiCloudFlowProtectionXML==null?"null":mMifiCloudFlowProtectionXML.toString().length()));
        if(mMifiCloudFlowProtectionXML!=null){
            FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().setMU3CFlowProtectionXML(mMifiCloudFlowProtectionXML);
            MifiXMLUtils.saveToSP(mMifiCloudFlowProtectionXML);
            if(isInRetrict){
                MifiCloudFlowProtectionCtrlImpl.this.clearRetrict("--by updateXML--");
                MifiCloudFlowProtectionCtrlImpl.this.setRetrict("--by updateXML--");
            }
        }
    }

    //--------------------------
    private class HandlerMsg{
        public int ddsId;
        public NetworkInfo.State state;
        public int type;
        public String ifName;
        public boolean isExistIfNameExtra;
        public int subId;

        public HandlerMsg(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId){
            this.ddsId = ddsId;
            this.state = state;
            this.type = type;
            this.ifName = ifName;
            this.isExistIfNameExtra = isExistIfNameExtra;
            this.subId = subId;
        }

        @Override
        public String toString() {
            return "HandlerMsg{" +
                    "ddsId=" + ddsId +
                    ", state=" + state +
                    ", type=" + type +
                    ", ifName='" + ifName + '\'' +
                    ", isExistIfNameExtra=" + isExistIfNameExtra +
                    ", subId=" + subId +
                    '}';
        }
    }

}

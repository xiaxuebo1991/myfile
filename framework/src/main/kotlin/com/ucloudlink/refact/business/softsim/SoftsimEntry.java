package com.ucloudlink.refact.business.softsim;

import android.content.Context;
import android.os.RemoteException;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.framework.ui.IUcloudDownloadSoftsimCallback;
import com.ucloudlink.refact.access.AccessEntry;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.business.statebar.NoticeStatusBarServiceStatus;
import com.ucloudlink.refact.business.softsim.download.SoftsimDownloadState;
import com.ucloudlink.refact.business.softsim.download.SoftsimUpdateManager;
import com.ucloudlink.refact.business.softsim.manager.SoftsimManager;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logk;

/**
 * Created by shiqianhua on 2016/12/1.
 */

public class SoftsimEntry {
    private String TAG = "SoftsimEntry";
    private String username;
    public SoftsimDownloadState downloadState;
    private SoftsimManager softsimManager;
    private ArrayList<IUcloudDownloadSoftsimCallback> eventCbList = new ArrayList<>();
    public SoftsimUpdateManager softsimUpdateManager;

    public SoftsimEntry(Context ctx, AccessEntry accessEntry){
        softsimManager = new SoftsimManager(ctx);
        softsimUpdateManager  = new SoftsimUpdateManager(ctx, accessEntry, this);
        downloadState = new SoftsimDownloadState(ctx, softsimManager){
            @Override
            public void downloadSucc(String username, String order, OrderInfo orderInfo) {
                super.downloadSucc(username, order, orderInfo);
                updateDownloadResult(order, ErrorCode.INSTANCE.getSOFTSIM_DL_SUCC());
            }

            @Override
            public void downloadFail(String username, String order, int errcode, Throwable t) {
                super.downloadFail(username, order, errcode, t);
                updateDownloadResult(order, errcode, t.getMessage());
            }

            @Override
            public void softsimUpdateOver(int result, String msg){
                super.softsimUpdateOver(result, msg);
                softsimUpdateManager.updateSoftsimOver(result, msg);
            }
        };
    }

    /**
     * 开始下载软卡,UI调用
     * @param username
     * @param passwd
     * @param orders
     * @return 0 succ, 其他 错误
     */
    public int startDownloadSoftsim(String username, String passwd, List<FlowOrder> orders){
        logk("startDownloadSoftsim: " + username + " ," + orders);
        if(username == null || passwd == null || orders == null){
            JLog.loge(TAG, "startDownloadSoftsim: param error" );
            for(FlowOrder order: orders) {
                updateDownloadResult(order.getOrderId(), ErrorCode.INSTANCE.getSOFTSIM_DL_PARAM_ERR());
            }
            return -1;
        }

        Boolean isOrderActiveLeft = false;
        ArrayList<OrderInfo> orderInfos = softsimManager.getSoftsimOrderlistByUser(username);
        if (orderInfos != null) {
            for (OrderInfo o : orderInfos) {
                if (!o.isOutOfDate()) {
                    isOrderActiveLeft = true;
                    break;
                }
            }
        }

        ArrayList<FlowOrder> needDownloadOrders = new ArrayList<>();
        if(orderInfos != null) {
            for (FlowOrder o : orders) {
                boolean isFind = false;
                for (OrderInfo i : orderInfos) {
                    if (o.getOrderId().equals(i.getOrderId())) {
                        logd("order:" + o.getOrderId() + " is already in! do not download");
                        isFind = true;
                        updateDownloadResult(o.getOrderId(), ErrorCode.INSTANCE.getSOFTSIM_DL_SUCC());
                        break;
                    }
                }
                if (!isFind) {
                    needDownloadOrders.add(o);
                }
            }

            if (needDownloadOrders.size() == 0) {
                logd("no orders need download");
                return 0;
            }
        }else {
            for(FlowOrder o:orders) {
                needDownloadOrders.add(o);
            }
        }

        Boolean isSoftsimInDevice = false;
        if (isOrderActiveLeft) {
            ArrayList<SoftsimLocalInfo> list = softsimManager.getSoftsimInfoListByUser(username);
            isSoftsimInDevice = (list != null && list.size() != 0);
        }

        ArrayList<FlowOrder> waitList = new ArrayList<>();
        for (FlowOrder order : needDownloadOrders) {
            if (order.getOrderId() == null || order.getOrderId().length() == 0) {
                updateDownloadResult(order.getOrderId(), ErrorCode.INSTANCE.getSOFTSIM_DL_PARAM_ERR());
            } else if (order.getSeedSimPolicy() == 2 || isSoftsimInDevice) {
                // no need to download softsim
                OrderInfo info = new OrderInfo(order.getOrderId(), null, order.getCreateTime(), order.getActivatePeriod(), order.getMccLists(), order.getSeedSimPolicy());
                softsimManager.updateUserOrderInfo(username, info);
                updateDownloadResult(order.getOrderId(), ErrorCode.INSTANCE.getSOFTSIM_DL_SUCC());
            } else {
                waitList.add(order);
            }
        }
        if(waitList.size() > 0){
            this.username = username;
            downloadState.startDownloadSoftsim(username, passwd, waitList);
        }
        return 0;
    }

    /**
     * 停止下载软卡, (暂时无人使用)
     * @param username
     * @param order
     * @return
     */
    public int stopDownloadSoftsim(String username, String order){
        if(!username.equals(this.username)){
            JLog.loge(TAG, "Not the match user!");
            return -1;
        }

        downloadState.stopDownloadSoftsim(order);
        return 0;
    }

    /**
     * 停止下载当前所有软卡
     * @return
     */
    public int stopDownloadAll(){

        downloadState.stopDownloadAllSoftsim();

        username = null;

        return 0;
    }

    /**
     * 通知下载状态机接口
     * @param what
     * @param arg0
     * @param arg1
     * @param o
     */
    public void notifyMsg(int what, int arg0, int arg1, Object o){
        downloadState.sendMessage(what, arg0, arg1, o);
    }

    /**
     * 注册callback,下载相关接口
     * @param cb
     */
    public void registerCb(IUcloudDownloadSoftsimCallback cb){
        JLog.logd(TAG, "registerCb: IUcloudDownloadSoftsimCallback:" +cb);
        if(cb == null){
            JLog.loge(TAG, "registerCb: IUcloudDownloadSoftsimCallback is null");
            return;

        }
        for(IUcloudDownloadSoftsimCallback c: eventCbList){
            if(c == cb){
                return;
            }
        }
        eventCbList.add(cb);
    }

    public void unregisterCb(IUcloudDownloadSoftsimCallback cb){
        eventCbList.remove(cb);
    }

    private void updateDownloadResult(String order, int errcode){
        logk("updateDownloadResult: " + order + " " + errcode);
        ArrayList<IUcloudDownloadSoftsimCallback> removeList = new ArrayList<>();
        for(IUcloudDownloadSoftsimCallback cb: eventCbList){
            try {
                cb.eventSoftsimDownloadResult(order, errcode);
            }catch (RemoteException e){
                e.printStackTrace();
                removeList.add(cb);
            }
        }
        for(IUcloudDownloadSoftsimCallback cb: removeList){
            eventCbList.remove(cb);
        }
    }

    private void updateDownloadResult(String order, int errcode, String msg){
        logk("updateDownloadResult: " + order + " " + errcode + " " + msg);
        ArrayList<IUcloudDownloadSoftsimCallback> removeList = new ArrayList<>();
        for(IUcloudDownloadSoftsimCallback cb: eventCbList){
            try {
                cb.eventSoftsimDownloadResult(order, errcode);
            }catch (RemoteException e){
                e.printStackTrace();
                removeList.add(cb);
            }
        }
        for(IUcloudDownloadSoftsimCallback cb: removeList){
            eventCbList.remove(cb);
        }
    }

    public void updateStartSeedNetworkResult(String order, int errcode, String msg){
        logk("updateStartSeedNetworkResult: " + order + " " + errcode + " " + msg);
        ArrayList<IUcloudDownloadSoftsimCallback> removeList = new ArrayList<>();

        if (errcode != 0){
            NoticeStatusBarServiceStatus.INSTANCE.noticStatusBarServiceStatus(NoticeStatusBarServiceStatus.STATUS_STOP);
        }

        for(IUcloudDownloadSoftsimCallback cb: eventCbList){
            try {
                cb.eventStartSeedSimResult(order, errcode);
            }catch (RemoteException e){
                e.printStackTrace();
                removeList.add(cb);
            }
        }
        for(IUcloudDownloadSoftsimCallback cb: removeList){
            eventCbList.remove(cb);
        }
    }


    public ArrayList<SoftsimLocalInfo> getSoftsimListByOrderInfo(String username, OrderInfo info){
        ArrayList<SoftsimLocalInfo> list =  softsimManager.getSoftsimListByOrderInfo(info);
        if(list == null || list.isEmpty()){
            return softsimManager.getSoftsimInfoListByUser(username);
        }

        return list;
    }

    public ArrayList<SoftsimLocalInfo> getSoftsimListByOrderInfo(OrderInfo info){
        return softsimManager.getSoftsimListByOrderInfo(info);
    }

    public OrderInfo getOrderInfoByUserOrderId(String username, String order){
        JLog.logd(TAG, "getOrderInfoByUserOrderId: " + username + " " + order);
        return softsimManager.getOrderInfoByUserOrder(username, order);
    }

    public SoftsimLocalInfo getSoftsimByImsi(String imsi){
        return softsimManager.getSoftsimInfoByImsi(imsi);
    }

    public void updateSoftsimUnusable(String imsi, int errcode, int suberr, String mcc, String mnc){
        softsimManager.softsimUpdateResult(imsi, errcode, suberr, mcc, mnc);
    }

    public void regLocalCb(SoftsimDownloadState.SoftsimEventCb cb){
        downloadState.SoftsimEventCbReg(cb);
    }

    public void startUpdateAllSoftsim(String curImsi, String mcc, String mnc){
        downloadState.startUpdateSoftsim(curImsi, mcc, mnc);
    }

    public void stopUpdateSoftsim(){
        downloadState.stopUpdateSoftsim();
    }

    public boolean isBinRefAlreadyInDevice(String binRef){
        return softsimManager.getSoftsimBinRefIn(binRef);
    }

    public void updateUserOrderInfo(String username, OrderInfo info){
        softsimManager.updateUserOrderInfo(username, info);
    }

    public int activateUserOrder(String username, String order, long activateTime, long deadlineTime){
        return softsimManager.activateUserOrder(username, order, activateTime, deadlineTime);
    }

    public void updateOrderOutOfDate(long curTime){
        softsimManager.updateOrderOutOfDate(curTime);
    }

    public void startUploadSoftsimFlow(){
        //downloadState.startUploadSoftsimFlow();
        startUploadSoftsimFlow(0);
    }

    public void startUploadSoftsimFlow(long delayMillis){
        downloadState.startUploadSoftsimFlow(delayMillis);
    }

    public void addNewSoftsimStateInfo(SoftsimFlowStateInfo info){
        if(info == null){
            return;
        }
        softsimManager.addSoftsimInfo(info);
    }

    /*just for debug*/
    public void startSocket(){
        try {
            Method method = downloadState.getClass().getMethod("startCreateSocket");
            method.invoke(downloadState);
        }catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException e){
            e.printStackTrace();
        }
    }
    /*just for debug*/
    public void stopSocket(){
        try {
            Method method = downloadState.getClass().getMethod("stopSocket");
            method.invoke(downloadState);
        }catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException e){
            e.printStackTrace();
        }
    }
    public void setDonotLogout(boolean value){
        downloadState.setDonotLogout(value);
    }
    public void forLogoutMsg(){
        downloadState.forLogoutMsg();
    }
    public void startUploadSoftsimFlowTest(){
        downloadState.startUploadSoftsimFlowTest();
    }

    public void logoutReq(int module) {
        downloadState.sendMessage(SoftsimDownloadState.SOFTSIM_FLOW_LOGOUT_REQ);
    }
}

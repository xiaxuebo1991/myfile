package com.ucloudlink.refact.business.softsim.manager;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType;
import com.ucloudlink.refact.business.realtime.RealTimeManager;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimBinLocalFile;
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimUnusable;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by shiqianhua on 2016/12/6.
 */
public class SoftsimManager {
    private String TAG = "SoftsimManager";
    private SoftsimDB softsimDB;

    public SoftsimManager(Context ctx){
        softsimDB =  new SoftsimDB(ctx);
    }

    public OrderInfo getOrderInfoByUserOrder(String username, String order){
        if(TextUtils.isEmpty(username) || TextUtils.isEmpty(order)){
            JLog.loge(TAG, "getOrderInfoByUserOrder: usename or order is null" + username + " " + order);
            return null;
        }
        return softsimDB.getOrderInfoByUsernameOrderid(username, order);
    }

    public ArrayList<String> getSoftsimListByUserOrder(String username, String order){
        OrderInfo orderInfo = getOrderInfoByUserOrder(username, order);

        if(orderInfo != null){
            return orderInfo.getSofsimList();
        }
        return null;
    }

    public ArrayList<String> getSoftsimListByUser(String username){
        ArrayList<String> imsis = new ArrayList<String>();
        boolean isFind = false;
        ArrayList<OrderInfo> orderInfos = softsimDB.getUserOrderInfoListByUsername(username);

        if(orderInfos == null){
            return null;
        }

        for(OrderInfo order: orderInfos){
            if(order.getSofsimList() == null){
                continue;
            }
            for(String imsi: order.getSofsimList()){
                isFind = false;
                for(String tmp: imsis){
                    if(tmp.equals(imsi)){
                        isFind = true;
                        break;
                    }
                }
                if(!isFind){
                    imsis.add(imsi);
                }
            }
        }

        return imsis;
    }

    public SoftsimLocalInfo getSoftsimInfoByImsi(String imsi){
        SoftsimLocalInfo info = softsimDB.getSoftsimInfoByImsi(imsi);

        return info;
    }

    public ArrayList<OrderInfo> getSoftsimOrderlistByUser(String username) {
        return softsimDB.getUserOrderInfoListByUsername(username);
    }

    private ArrayList<SoftsimLocalInfo> getSoftsimInfoListBySoftsimList(ArrayList<String> imsis){
        ArrayList<SoftsimLocalInfo> softsimLocalInfos = new ArrayList<SoftsimLocalInfo>();
        SoftsimLocalInfo info = null;
        if(imsis == null){
            return null;
        }
        for(String imsi:imsis){
            info = softsimDB.getSoftsimInfoByImsi(imsi);
            if(info == null){
                JLog.loge(TAG, "get softsim " + imsi + " is null");
            }else {
                softsimLocalInfos.add(info);
            }
        }
        return softsimLocalInfos;
    }

    public ArrayList<SoftsimLocalInfo> getSoftsimInfoListByUserOrder(String username, String order){
        ArrayList<String> simList = getSoftsimListByUserOrder(username, order);

        if(simList == null){
            return null;
        }

        return  getSoftsimInfoListBySoftsimList(simList);
    }

    public ArrayList<SoftsimLocalInfo> getSoftsimListByOrderInfo(OrderInfo orderInfo){
        return getSoftsimInfoListBySoftsimList(orderInfo.getSofsimList());
    }

    public ArrayList<SoftsimLocalInfo> getSoftsimInfoListByUser(String username){
        ArrayList<String> imsis = getSoftsimListByUser(username);
        SoftsimLocalInfo info = null;
        ArrayList<SoftsimLocalInfo> softsimLocalInfos = new ArrayList<SoftsimLocalInfo>();

        if(imsis == null){
            return  null;
        }

        for(String imsi:imsis){
            info = softsimDB.getSoftsimInfoByImsi(imsi);
            if(info == null){
                JLog.loge(TAG, "get softsim " + imsi + " is null");
            }else {
                softsimLocalInfos.add(info);
            }
        }

        return softsimLocalInfos;
    }

    public ArrayList<String> getSoftsimByOrderList(ArrayList<OrderInfo> orderInfos){
        ArrayList<String> imsis = new ArrayList<String>();
        boolean isFind = false;

        if(orderInfos == null || orderInfos.isEmpty()){
            return null;
        }

        for(OrderInfo order: orderInfos){
            for(String imsi: order.getSofsimList()){
                isFind = false;
                for(String tmp: imsis){
                    if(tmp.equals(imsi)){
                        isFind = true;
                        break;
                    }
                }
                if(!isFind){
                    imsis.add(imsi);
                }
            }
        }

        return imsis;
    }


    public ArrayList<String> getUserLocalSoftsimByOrderId(String username, String orderId){
        OrderInfo info = getOrderInfoByUserOrder(username, orderId);

        return info.getSofsimList();
    }

    public ArrayList<SoftsimLocalInfo> getUserLocalSoftsimInfoByOrderId(String username, String orderId) {
        ArrayList<String> imsis = getUserLocalSoftsimByOrderId(username, orderId);
        ArrayList<SoftsimLocalInfo> softsimLocalInfos = new ArrayList<SoftsimLocalInfo>();
        SoftsimLocalInfo info = null;

        if(imsis == null || imsis.isEmpty() || TextUtils.isEmpty(orderId)){
            return  null;
        }

        for(String imsi: imsis){
            info = softsimDB.getSoftsimInfoByImsi(imsi);
            if(info == null){
                JLog.loge(TAG, "getUserLocalSoftsimInfo: imsi " + imsi + " info is nulll!" );
                continue;
            }else {
                softsimLocalInfos.add(info);
            }
        }

        return softsimLocalInfos;
    }

    public void updateUserOrderInfo(String username, OrderInfo orderInfo){
        softsimDB.updateUserOrderInfo(username, orderInfo);
    }

    public void updateSoftsimInfo(SoftsimLocalInfo info){
        softsimDB.updateSoftsimInfo(info);
    }

    public void updateSoftsimBinFile(SoftsimBinLocalFile binFile){
        if(binFile.getType() == SoftsimBinType.PLMN_LIST_BIN.getValue()){
            // todo: save to file

        }else {
            // // TODO: 2017/5/27 save fee file
        }
        softsimDB.updateSoftsimBinData(binFile.getRef(), binFile.getType(), binFile.getData());
    }

    public byte[] getSoftsimBinByRef(String ref){
        return softsimDB.getSoftsimBinByRef(ref);
    }

    public boolean getSoftsimBinRefIn(String ref){
        return softsimDB.isSoftsimBinRefIn(ref);
    }

    public int activateUserOrder(String username, String order, long activateTime, long deadlineTime){
        long curTime = RealTimeManager.INSTANCE.getRealTime();
        OrderInfo info =  getOrderInfoByUserOrder(username, order);
        if(info == null){
            JLog.loge(TAG, "activateUserOrder: " + username  + " order id " + order +  " is null!" );
            return  -1;
        }

        info.setActivatTime(activateTime);
        info.setDeadlineTime(deadlineTime);
        info.setActivate(true);
        if(deadlineTime < curTime){
            info.setOutOfDate(true);
        }else {
            info.setOutOfDate(false);
        }

        updateUserOrderInfo(username, info);

        return 0;
    }

    private boolean updateSingleOrderInfo(long curTime, OrderInfo info){
        if(info.isOutOfDate()){
            return false;
        }
        if(info.getActivatePeriod() == -1 || info.getDeadlineTime() == -1){
            JLog.logd(TAG, "updateSingleOrderInfo: ");
            return false;
        }
        if(info.isActivate()){
            if(info.getDeadlineTime() + TimeUnit.HOURS.toMillis(1) < curTime){   //多增加1小时，保证用户能够及时更换套餐
                JLog.loge(TAG, "updateSingleOrderInfo: order outof date after activate," + info );
                info.setOutOfDate(true);
                return true;
            }
        }else {
            if(info.getCreateTime() + TimeUnit.DAYS.toMillis(info.getActivatePeriod()) + TimeUnit.HOURS.toMillis(1) < curTime){
                JLog.loge(TAG, "updateSingleOrderInfo: order out of date before activate," + info );
                info.setOutOfDate(true);
                return true;
            }
        }
        return false;
    }

    public void updateOrderOutOfDate(final long curTime){
        JLog.logd(TAG, "updateOrderOutOfDate: start to ----------" + curTime);

        softsimDB.iterAllOrderList(new SoftsimDB.iterHandler() {
            @Override
            public int callSingleObj(Object param, String idx, Object o) {
                String username = idx;
                boolean update = false;
                OrderInfo info = (OrderInfo)o;

                boolean re = updateSingleOrderInfo(curTime, info);
                if(re){
                    softsimDB.updateUserOrderInfo(username, info);
                }

                return 0;
            }
        }, "");


    }

    public int softsimUpdateResult(String imsi, int errcode, int subErr, String mcc, String mnc){
        JLog.logd(TAG, "softsimStartupResult: " + imsi + " " + errcode + " " + subErr + " " + mcc + " " + mnc);
        SoftsimLocalInfo softsim = getSoftsimInfoByImsi(imsi);
        if(softsim == null){
            JLog.loge(TAG, "softsimStartupResultTest: cannot find imsi!" + imsi);
            return -1;
        }
        softsim.addLocalUnusableReason(new SoftsimUnusable(mcc,mnc, System.currentTimeMillis(), errcode, subErr));
        updateSoftsimInfo(softsim);
        return 0;
    }

    public ArrayList getFirstSoftsimState(){
        return softsimDB.getFirstSoftsimFlowInfo();
    }

    public void addSoftsimInfo(SoftsimFlowStateInfo info){
        softsimDB.addSoftsimFlowInfo(info);
    }

    public void delSoftsimStateById(int id){
        softsimDB.delSoftsimFlowInfoById(id);
    }
}

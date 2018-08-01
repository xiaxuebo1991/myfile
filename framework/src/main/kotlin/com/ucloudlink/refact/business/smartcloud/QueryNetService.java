package com.ucloudlink.refact.business.smartcloud;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by zhanlin.ma on 2018/4/13.
 */

public class QueryNetService extends Service{
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
//
//    private static final String TAG = "smartcloud QueryNetService";
//    private int mPhoneId = -1;
//    private NetQueryService     mNetQueryService;
//
//    private QcRilHook         mQcRilHook = null;
//    private QcRilHookCallback mQcRilHookCallback = new QcRilHookCallback() {
//        @Override
//        public void onQcRilHookReady() {
//            if (mQcRilHook == null){
//                JLog.logi(TAG,"onQcRilHookReady mQcRilHook is null.");
//                return;
//            }
//            if (mPhoneId == -1){
//                JLog.logi(TAG,"onQcRilHookReady invalid phoneId");
//                return;
//            }
//            JLog.logi(TAG,"onQcRilHookReady qcRilPerformIncrManualScan phoneId:"+mPhoneId);
//            boolean success = mQcRilHook.qcRilPerformIncrManualScan(mPhoneId);
//            if (!success && PhoneFactory.getPhone(mPhoneId) == null) {
//                JLog.logi(TAG," qcRilPerformIncrManualScan error phone = null");
//                return;
//            }
//        }
//
//        @Override
//        public void onQcRilHookDisconnected() {
//            mQcRilHook.dispose();
//            mQcRilHook = null;
//            mPhoneId = -1;
//        }
//    };
//
//    /**
//     * 搜网
//     *
//     * @param subId
//     */
//    public void startNetworkQuery(int subId) {
//        int phoneId = SubscriptionManager.getPhoneId(subId);
//        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
//            JLog.logi(TAG," startNetworkQuery error: not ValidPhoneId");
//            mPhoneId = -1;
//            return;
//        }
//        mPhoneId = phoneId;
//
//        if (mQcRilHook == null) {
//            mQcRilHook = new QcRilHook(this, mQcRilHookCallback);
//            JLog.logi(TAG,"startNetworkQuery wait mQcRilHook ready.");
//            return;
//        }
//
//        JLog.logi(TAG,"startNetworkQuery subId:"+subId+",phoneId:"+phoneId);
//        boolean success = mQcRilHook.qcRilPerformIncrManualScan(phoneId);
//        if (!success && PhoneFactory.getPhone(phoneId) == null) {
//            JLog.logi(TAG," startNetworkQuery error phone = null");
//            return;
//        }
//
//        return;
//    }
//
//    @Override
//    public IBinder onBind(Intent arg0) {
//        JLog.logi(TAG,"onBind");
//        return mNetQueryService;
//    }
//
//    @Override
//    public void onCreate() {
//        JLog.logi(TAG,"onCreate");
//        mNetQueryService = new NetQueryService();
//        mQcRilHook = new QcRilHook(this, mQcRilHookCallback);
//        super.onCreate();
//    }
//
//    private class NetQueryService extends INetQueryInterface.Stub{
//        @Override
//        public void scanSimNetwork(int subId) throws RemoteException {
//            JLog.logi(TAG,"NetQueryService:scanCloudSimNetwork subId=" + subId);
//            startNetworkQuery(subId);
//        }
//
//        @Override
//        public void registerNetQueryCallback(INetQueryCallback cb) throws RemoteException {
//        }
//
//        @Override
//        public void unregisterNetQueryCallback(INetQueryCallback cb) throws RemoteException {
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        mQcRilHook.dispose();
//        mQcRilHook = null;
//        mPhoneId = -1;
//    }
}

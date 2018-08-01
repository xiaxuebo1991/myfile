/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ucloudlink.refact.platform.qcom.duncall;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEventId;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logk;

/**
 * Manages the DUN network connectivity
 */
public class DunNetworkManager{
    
    private static final String TAG = "UcDunNetworkManager";
    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;
    // Wait timeout for this class, a little bit longer than the above timeout
    // to make sure we don't bail prematurely
    private static final int NETWORK_ACQUIRE_TIMEOUT_MILLIS = 12 * 1000;
//            NETWORK_REQUEST_TIMEOUT_MILLIS + (5 * 1000);
    private final long  MINIMUM_INTERVAL_TIME=8*1000L;//最少间隔时间
    private final long  MINIMUM_RELEASE_TIME=5*1000L;//最少等待释放时间
    private long lastRequestTime;
    private long lastReleaseTime;
    private final Context mContext;
    private HandlerThread handlerThread=new HandlerThread("release HandlerThread");
    // The requested DUN {@link android.net.Network} we are holding
    // We need this when we unbind from it. This is also used to indicate if the
    // DUN network is available.
    private Network mPinnedNetwork;
    // The current count of DUN requests that require the DUN network
    // If mDunRequestCount is 0, we should release the DUN network.
    private int mDunRequestCount;
    // This is really just for using the capability
    private final NetworkRequest mNetworkRequest;
    // The callback to register when we request DUN network
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private volatile ConnectivityManager mConnectivityManager;

    // The SIM ID which we use to connect
    private final int mSubId;
    private int RELEASE_TASK=100001;

    public DunNetworkManager(Context context, int subId) {
        mContext = context;
        mNetworkCallback = null;
        mPinnedNetwork = null;
        JLog.logd(TAG, "DunNetworkManager init");
        mDunRequestCount = 0;
        mConnectivityManager = null;
        handlerThread.start();
        mHandler=new Handler(handlerThread.getLooper()){
            @Override
            public void handleMessage(Message msg) {
                synchronized (this) {
                    if (msg.what == RELEASE_TASK) {
                        releaseTask.run();
                    }
                }
            }
        };
        mSubId = subId;
        mNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                .setNetworkSpecifier(Integer.toString(mSubId))
                .build();
    }

    public long getLastReleaseTime() {
        return lastReleaseTime;
    }

    public  long getMinimumReleaseTime() {
        return MINIMUM_RELEASE_TIME;
    }

    /**
     * Acquire the DUN network
     *
     */
    public Long acquireNetwork()  { // throws DunNetworkException
        synchronized (this) {
            mDunRequestCount += 1;
            if (mDunRequestCount>1){
                mDunRequestCount=1;
                return  -1L;
            }
            if (mPinnedNetwork != null) {
                // Already available
                JLog.logd(TAG, "DunNetworkManager: already available");
                return  -1L;
            }
           
            JLog.logd(TAG, "DunNetworkManager: start new network request count=" + mDunRequestCount);
            // Not available, so start a new request
            if (mHandler.hasMessages(RELEASE_TASK)) {
                logd("has a task waiting release so do not request again");
                mHandler.removeMessages(RELEASE_TASK);
            }else {
                logk("do onDemandPsCall");
                sendBroadcast("doCall");
                Configuration.INSTANCE.setDoingPsCall(true);
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_START_PS_CALL);
                lastRequestTime = SystemClock.elapsedRealtime();//标记最后一次拨号的时间
                lastReleaseTime = 0;
                newRequest();
            }
//            final long shouldEnd = SystemClock.elapsedRealtime() + NETWORK_ACQUIRE_TIMEOUT_MILLIS;
//            long waitTime = NETWORK_ACQUIRE_TIMEOUT_MILLIS;
//            while (waitTime > 0) {
//                try {
//                    this.wait(waitTime);
//                } catch (InterruptedException e) {
//                    Log.w(TAG, "DunNetworkManager: acquire network wait interrupted");
//                }
//                if (mPinnedNetwork != null) {
//                    // Success
//                    return waitTime;
//                }
//                // Calculate remaining waiting time to make sure we wait the full timeout period
//                waitTime = shouldEnd - SystemClock.elapsedRealtime();
//            }
            // Timed out, so release the request and fail
//            JLog.logd(TAG, "DunNetworkManager: timed out");
//            releaseRequestLocked(mNetworkCallback);
//            throw new DunNetworkException("Acquiring network timed out");
            return 0L;
        }
    }

    private void sendBroadcast(String msg) {
        //发送广播
        Intent intent=new Intent("com.ucloudlink.ucapp.dun");
        intent.putExtra("action",msg);
        mContext.sendBroadcast(intent);
    }

    private Handler mHandler;
    /**
     * Release the DUN network when nobody is holding on to it.
     */
    public long releaseNetwork() {
        synchronized (this) {
            long currentTime = SystemClock.elapsedRealtime();
            long pastedTime = currentTime - lastRequestTime;
            long delayTime= pastedTime > MINIMUM_INTERVAL_TIME? 0:MINIMUM_INTERVAL_TIME-pastedTime;//释放延迟时间
            JLog.logd(TAG, "releaseNetwork: mDunRequestCount:"+mDunRequestCount);
            if (mDunRequestCount > 0) {
                mDunRequestCount -= 1;
                JLog.logd(TAG, "DunNetworkManager: release, count=" + mDunRequestCount);
                if (mDunRequestCount < 1) {
                    mHandler.sendEmptyMessageDelayed(RELEASE_TASK,delayTime);
                    return delayTime;
                }
            }
            return delayTime;
        }
    }
    
    private Runnable releaseTask=new Runnable() {
        @Override
        public int hashCode() {
            return 111;
        }

        @Override
        public void run() {
            logk("do undoOnDemandPsCall");
            releaseRequestLocked(mNetworkCallback);
            resetLocked(mPinnedNetwork,true);
        }
    };

    /**
     * Start a new {@link android.net.NetworkRequest} for DUN
     */
    private void newRequest() {
        final ConnectivityManager connectivityManager = getConnectivityManager();
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                JLog.logd(TAG, "NetworkCallbackListener.onAvailable: network=" + network);
                synchronized (DunNetworkManager.this) {
                    if (mConnectivityManager.getBoundNetworkForProcess() == null && mPinnedNetwork == null&&mDunRequestCount >0) {
                        boolean retBind = mConnectivityManager.bindProcessToNetwork(network);
                        JLog.logd(TAG, "dun Bind retBind:" + retBind + ",network:" + network);
                        if(retBind) {
                            mPinnedNetwork = network;
                            DunNetworkManager.this.notifyAll();
                            saveDunStr();
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_PS_CALL_SUCC);
                        }else {
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_BIND_DUN_FAIL);
                        }
                    }
                }
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                JLog.logd(TAG, "NetworkCallbackListener.onLost: network=" + network);
                synchronized (DunNetworkManager.this) {
//                    releaseRequestLocked(this);
//                    resetLocked(network,true);
                    mPinnedNetwork = null;
                    Boolean retUnBind = mConnectivityManager.bindProcessToNetwork(null);
                    DunNetworkManager.this.notifyAll();
//                    if (mDunRequestCount >0){
//                        //自动重连
//                        newRequest();
//                    }
                }
            }
            @Override
            public void onUnavailable() {
                super.onUnavailable();
                JLog.logd(TAG, "NetworkCallbackListener.onUnavailable");
                synchronized (DunNetworkManager.this) {
//                    releaseRequestLocked(this);
                    DunNetworkManager.this.notifyAll();
                }
            }

        };
        connectivityManager.requestNetwork(
                mNetworkRequest, mNetworkCallback, NETWORK_REQUEST_TIMEOUT_MILLIS);
    }

    private void saveDunStr() {
        //保存当前可用的dun Apn字符串
        String temp_dunStr = Configuration.INSTANCE.getTemp_dunStr();
        String temp_dun_numericStr = Configuration.INSTANCE.getTemp_dun_numericStr();
        if (TextUtils.isEmpty(temp_dunStr)||TextUtils.isEmpty(temp_dun_numericStr)){
            return;
        }
        SharedPreferencesUtils.putString(mContext,"dunConfig",temp_dun_numericStr,temp_dunStr);
        Configuration.INSTANCE.setTemp_dun_numericStr("");
        Configuration.INSTANCE.setTemp_dunStr("");
    }

    /**
     * Release the current {@link android.net.NetworkRequest} for DUN
     *
     * @param callback the {@link android.net.ConnectivityManager.NetworkCallback} to unregister
     */
    private void releaseRequestLocked(ConnectivityManager.NetworkCallback callback) {
        Log.d(TAG, "releaseRequestLocked: "+callback);
        if (callback != null) {
            Configuration.INSTANCE.setDoingPsCall(false);
            lastReleaseTime=SystemClock.elapsedRealtime();
            final ConnectivityManager connectivityManager = getConnectivityManager();
            connectivityManager.unregisterNetworkCallback(callback);
        }
    }

    /**
     * Reset the state
     */
    private void resetLocked(Network network,boolean isCleanCount) {
        mNetworkCallback = null; // 当发送onDemand PS Call后，在available之前releaseNetwork置空callback，确保不会执行bind网络
        if (network==null) {
            return;
        }

        if (network.equals(mPinnedNetwork) && network.equals(mConnectivityManager.getBoundNetworkForProcess())) {
            Boolean retUnBind = mConnectivityManager.bindProcessToNetwork(null);
            JLog.logd(TAG, "dun unBind retUnBind:" + retUnBind);
        }
        else
        {
            JLog.logd(TAG, "resetLocked: current network: "+ network);
        }
        mPinnedNetwork = null;
        if (isCleanCount) {
            JLog.logd(TAG, "resetLocked: mDunRequestCount:"+mDunRequestCount);
            mDunRequestCount = 0;
        }
    }

    private static final InetAddress[] EMPTY_ADDRESS_ARRAY = new InetAddress[0];

    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        Network network = null;
        synchronized (this) {
            if (mPinnedNetwork == null) {
                return EMPTY_ADDRESS_ARRAY;
            }
            network = mPinnedNetwork;
        }
        return network.getAllByName(host);
    }

    private ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }

    /**
     * Get the APN name for the active network
     *
     * @return The APN name if available, otherwise null
     */
    public String getApnName() {
        Network network = null;
        synchronized (this) {
            if (mPinnedNetwork == null) {
                JLog.logd(TAG, "DunNetworkManager: getApnName: network not available");
                return null;
            }
            network = mPinnedNetwork;
        }
        String apnName = null;
        final ConnectivityManager connectivityManager = getConnectivityManager();
        NetworkInfo dunNetworkInfo = connectivityManager.getNetworkInfo(network);
        if (dunNetworkInfo != null) {
            apnName = dunNetworkInfo.getExtraInfo();
        }
        JLog.logd(TAG, "DunNetworkManager: getApnName: " + apnName);
        return apnName;
    }
}

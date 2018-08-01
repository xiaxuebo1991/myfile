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
import android.net.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Manages the Default network connectivity
 */
public class DefaultNetworkManager{

    private static final String TAG = "UcDefaultNetworkManager";

    private final Context mContext;

    // The requested Default {@link android.net.Network} we are holding
    // We need this when we unbind from it. This is also used to indicate if the
    // DUN network is available.
    private Network mNetwork;

    // This is really just for using the capability
    private final NetworkRequest mNetworkRequest;
    // The callback to register when we request Default network
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private volatile ConnectivityManager mConnectivityManager;

    // The SIM ID which we use to connect
    private final int mSubId;

    public DefaultNetworkManager(Context context, int subId) {
        mContext = context;
        mNetworkCallback = null;
        mNetwork = null;

        mConnectivityManager = null;

        mSubId = subId;
        mNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)//NET_CAPABILITY_NOT_RESTRICTED
                .setNetworkSpecifier(Integer.toString(mSubId))
                .build();

        mConnectivityManager = getConnectivityManager();

        mNetworkCallback = new NetworkRequestCallback();
    }

    /**
     * Network callback for our network request
     */
    private class NetworkRequestCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            logd( "NetworkCallbackListener.onAvailable: network=" + network);
            synchronized (DefaultNetworkManager.this) {

                if (mConnectivityManager.getBoundNetworkForProcess() == null && mNetwork == null) {
                    mConnectivityManager.bindProcessToNetwork(network);
                    mNetwork = network;
                    logd( "NetworkCallbackListener enable on network");
                }
                DefaultNetworkManager.this.notifyAll();
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            logd( "NetworkCallbackListener.onLost: network=" + network);
            synchronized (DefaultNetworkManager.this) {
                if (network.equals(mNetwork) && network.equals(mConnectivityManager.getBoundNetworkForProcess())) {
                    mConnectivityManager.bindProcessToNetwork(null);
                    mNetwork = null;
                    logd( "NetworkCallbackListener disable on network");
                }
                DefaultNetworkManager.this.notifyAll();
            }
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            logd( "NetworkCallbackListener.onUnavailable");
            synchronized (DefaultNetworkManager.this) {
                DefaultNetworkManager.this.notifyAll();
            }
        }
    }

    private static final InetAddress[] EMPTY_ADDRESS_ARRAY = new InetAddress[0];

    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        Network network = null;
        synchronized (this) {
            if (mNetwork == null) {
                return EMPTY_ADDRESS_ARRAY;
            }
            network = mNetwork;
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

    public boolean isDefaultNetworkConnected() {
        final ConnectivityManager cm = getConnectivityManager();
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                logd( "isDefaultNetworkConnected: mobile type %d" + ni.getType());
                return ni.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }

    /**
     * Get the APN name for the active network
     *
     * @return The APN name if available, otherwise null
     */
    public String getApnName() {
        Network network = null;
        synchronized (this) {
            if (mNetwork == null) {
                logd( "DefaultNetworkManager: getApnName: network not available");
                return null;
            }
            network = mNetwork;
        }
        String apnName = null;
        final ConnectivityManager connectivityManager = getConnectivityManager();
        NetworkInfo defaultNetworkInfo = connectivityManager.getNetworkInfo(network);
        if (defaultNetworkInfo != null) {
            apnName = defaultNetworkInfo.getExtraInfo();
        }
        logd( "DefaultNetworkManager: getApnName: " + apnName);
        return apnName;
    }
}

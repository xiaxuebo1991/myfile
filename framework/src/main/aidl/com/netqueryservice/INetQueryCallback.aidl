// INetQueryCallback.aidl
package com.netqueryservice;

// Declare any non-default types here with import statements

interface INetQueryCallback {
    void scanSimNetworkResult(in int phoneId,in String mccmnc,in int rat,in int signalStrength);
}

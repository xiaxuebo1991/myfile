// INetQueryInterface.aidl
package com.netqueryservice;

import com.netqueryservice.INetQueryCallback;
// Declare any non-default types here with import statements

interface INetQueryInterface {
    //主动搜网
    void scanSimNetwork(int subId);

    void registerNetQueryCallback(in INetQueryCallback cb);
    void unregisterNetQueryCallback(in INetQueryCallback cb);
}

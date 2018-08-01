package com.ucloudlink.refact.platform.sprd.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.sprd.vsiminterface.IVSIMCallback;
import com.sprd.vsiminterface.IVSIMInterface;
import com.ucloudlink.refact.platform.sprd.nativeapi.SprdNativeIntf;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * Created by shiqianhua on 2017/12/4.
 */

public class SprdIntfService {
    private static final String TAG = "SprdIntfService";
    private static final int MAX_SLOT = 2;
    private Context mContext;
    private IVSIMInterface sprdIntf;
    private IVSIMCallback[] ServiceCallback = {new SprdVsimCb(), new SprdVsimCb()};//服务回调
    private SprdNativeIntf[] ifcbArray = new SprdNativeIntf[MAX_SLOT];
    private final Object lock = new Object();
    private static final int SERVICE_WAIT_TIME = 2000; // ms

    SprdIntfService(Context context){
        mContext = context;
        connectService();
        synchronized (lock){
            try {
                if(sprdIntf!=null){
                    //有开发人员测试发现进锁之前就绑定成功避免进锁之前绑定成功导致等待两秒，理论上是可能的，linux内核调度方式不一样
                    return;
                }
                lock.wait(SERVICE_WAIT_TIME);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("sprd intf service connected");
            synchronized (lock) {
                sprdIntf = IVSIMInterface.Stub.asInterface(service);
                lock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("sprd intf service onServiceDisconnected");
            sprdIntf = null;
            connectService();
        }
    };

    class SprdVsimCb extends IVSIMCallback.Stub{
        @Override
        public byte[] uploadAPDU(int slot, byte[] apdu_req, int apdu_len) throws RemoteException {
            logd("recv uploadAPDU slot" + slot);
            if(slot >= MAX_SLOT || slot < 0){
                Log.e(TAG, "uploadAPDU: slot invalie " + slot );
                return null;
            }
            if(ifcbArray[slot] == null){
                Log.e(TAG, "uploadAPDU: ifcb is null");
                return null;
            }
            return ifcbArray[slot].cb(slot, apdu_req);
        }
    }

    private void connectService(){
        logd("connectService start");
        Intent intent = new Intent("com.sprd.vsiminterface.IVSIMInterface");
        intent.setPackage("com.sprd.vsimservice");
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    protected int vsimInit(int phoneId, SprdNativeIntf cb, int mode){
        int ret;
        if(sprdIntf == null){
            loge("sprdIntf is null, server not initial");
            return  -1;
        }
        ifcbArray[phoneId] = cb;
        try {
            ret = sprdIntf.vsimInit(phoneId, mode, ServiceCallback[phoneId]);
        }catch (RemoteException e){
            e.printStackTrace();
            return -1;
        }
        return ret;
    }

    protected IVSIMInterface getSprdIntfApi(){
        return sprdIntf;
    }
}

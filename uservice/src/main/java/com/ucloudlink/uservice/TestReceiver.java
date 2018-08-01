package com.ucloudlink.uservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.StateMessageId;
import com.ucloudlink.refact.access.restore.RunningStates;
import com.ucloudlink.refact.access.struct.LoginInfo;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.product.mifi.downloadcfg.DownCfgUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Objects;

/**
 * Created by shiqianhua on 2016/12/19.
 */

public class TestReceiver extends BroadcastReceiver {
    public static final String TAG = "TestReceiver";
    File file = new File("/productinfo/info.obj");

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: action=" + intent.getAction());
        String ACTION_LOGOUT = "com.ucloudlink.cmd.logout";
        String ACTION_SET_FACTORY_IP = "com.ucloudlink.cmd.set.factory.ip";
        String ACTION_LOGIN = "com.ucloudlink.cmd.login";
        String ACTION_CHANGE_MODE = "com.ucloudlink.cmd.change.mode";
        String ACTION_CTRL_GPS = "com.ucloudlink.cmd.ctrl.gps";
        String ACTION_TRACEROUTE = "com.ucloudlink.cmd.traceroute";
        if (intent.getAction().equals(ACTION_LOGIN)) {
            try {
                ServiceManager.accessEntry.notifyEvent(StateMessageId.USER_LOGIN_REQ_CMD);
            } catch (Exception e) {
                Log.e(TAG, "onReceive: error:" + e);
            }
        } else if (intent.getAction().equals(ACTION_CHANGE_MODE)) {
            String mode = intent.getStringExtra("mode");
            Log.d(TAG, "onReceive: ACTION_CHANGE_MODE, mode=" + mode);
            if (mode != null) {
                try {
                    ServerRouter.INSTANCE.setIpMode(Integer.parseInt(mode));
                    RunningStates.saveAssServerMode(Integer.parseInt(mode));
                    switch (Integer.parseInt(mode)){
                        case 100:
                            DownCfgUtil.DEFAULT_URL = "https://saas.ucloudlink.com";
                            break;
                        case 101:
                            DownCfgUtil.DEFAULT_URL = "https://saas2.ukelink.com";
                            break;
                        case 102:
                            DownCfgUtil.DEFAULT_URL = "https://saas3.ukelink.com";
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onReceive: error:" + e);
                }
            }
        } else if (intent.getAction().equals(ACTION_LOGOUT)) {
            try {
                ServiceManager.accessEntry.logoutReq(4);
            } catch (Exception e) {
                Log.e(TAG, "onReceive: error:" + e);
            }
        } else if (intent.getAction().equals(ACTION_SET_FACTORY_IP)) {
            String ip = intent.getStringExtra("factory_ip");
            Log.d(TAG, "onReceive: ACTION_SET_FACTORY_IP, ip=" + ip);
            if (ip != null && ip.length() > 0) {
                ServerRouter.INSTANCE.setFactoryIP(ip);
            }
        } else if (intent.getAction().equals(ACTION_CTRL_GPS)) {
            int type = intent.getIntExtra("type", 0);
            if (type == 0) {
                ServiceManager.productApi.setGpsConfig(false, false);
            }
            if (type == 1) {
                ServiceManager.productApi.setGpsConfig(false, true);
            }
            if (type == 2) {
                ServiceManager.productApi.setGpsConfig(true, false);
            }
            if (type == 3) {
                ServiceManager.productApi.setGpsConfig(true, true);
            }
        } else if (intent.getAction().equals(ACTION_TRACEROUTE)) {
            try {
                ServiceManager.accessEntry.notifyEvent(StateMessageId.TRACEROUTE_EVENT);
            } catch (Exception e) {
                Log.e(TAG, "onReceive: error:" + e);
            }
        }
    }
}

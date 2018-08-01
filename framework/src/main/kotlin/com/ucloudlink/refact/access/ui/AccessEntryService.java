package com.ucloudlink.refact.access.ui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.ucloudlink.refact.Framework;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.ui.struct.NotificationParam;
import com.ucloudlink.refact.business.keepalive.JobSchedulerCtrl;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.utils.JLog;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by shiqianhua on 2016/10/20.
 * 说明：如果要配置IP，在绑定服务的时候在Intent中传入以下参数
 */
public class AccessEntryService extends Service {
    private final static int GRAY_SERVICE_ID = 1001;

    private AccessEntryAdapter mEntryAdapter;
    private JobSchedulerCtrl mJobSchedulerCtrl = new JobSchedulerCtrl();

    //注意！正常不应该把context赋值到一个静态变量上，因为可能会导致内存泄漏
    private static AccessEntryService myInstance = null;

    /**
     * 使用这个myInstance必须谨慎，只有这个Service内部的变量才能使用，否则不要引用！
     * @return AccessEntryService
     */
    public static AccessEntryService getMyInstance() {
        return myInstance;
    }

    @Override
    public void onCreate() {
        logd("onCreate");
        myInstance = this;
        mEntryAdapter = Framework.INSTANCE.environmentInit(this.getApplicationContext());
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mEntryAdapter;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mJobSchedulerCtrl.onStartCommand(intent, AccessEntryService.this);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            JLog.logi("keepLive, Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ");
            Intent innerIntent = new Intent(this, GrayInnerService.class);
            this.startService(innerIntent);
            this.startForeground(GRAY_SERVICE_ID, new Notification());
        } else {
            JLog.logi("keepLive, Build.VERSION.SDK_INT > Build.VERSION_CODES.M ");
        }

        logd("onStartCommand:" + intent + " flag: " + flags + " startId: " + startId);
        if (intent != null) {
            int ASSMode = intent.getIntExtra("ASSMode", -1);
            logd("onStartCommand: ASSMode " + ASSMode);
            if (ASSMode != -1) {
                ServerRouter.INSTANCE.setIpMode(ASSMode);
            }
        }
        return Service.START_STICKY;
//        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        logd("BinderService onDestroy");
        super.onDestroy();
        mJobSchedulerCtrl.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

//    //ֹͣservice
//    public void stopService() {
//        stopSelf();
//    }

    /**
     * 给 API >= 18 的平台上用的灰色保活手段
     */
    public static class GrayInnerService extends Service {// Note: 不能移除该内部类到外部，否则会在通知栏上有异常通知"xxx正在运行"

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(GRAY_SERVICE_ID, new Notification());
            stopForeground(true);
            stopSelf();
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}

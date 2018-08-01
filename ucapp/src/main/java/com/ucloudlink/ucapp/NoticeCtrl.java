package com.ucloudlink.ucapp;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

import java.util.ArrayList;
import java.util.List;
/**
 * Created by jiaming.liang on 2016/12/14.
 */

public class NoticeCtrl {
    private static Context mContext;
    public static final int OFF     = 0;
    public static final int RUNNING = 1;
    public static final int ON      = 2;
    public static final int CANCEL  = 3;

    private static int currentMode=-1;
    
    private static int           LIFE_ID   = 100;
    private static Object        lock      = new Object();
    private        List<Integer> showingID = new ArrayList<>();

    private boolean isRunning = false;

    private static NoticeCtrl instance;

    private NoticeCtrl() {
    }

    /**
     * 获取NoticeCtrl实例
     *
     * @return
     * @throws Exception 如果没有初始化,抛出异常
     */
    public static NoticeCtrl getInstance() throws Exception {
        if (mContext == null) {
            throw new Exception("please invoke init(Context) first");
        }
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new NoticeCtrl();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化,一般在application中初始化
     *
     * @param context
     */
    public static void init(Context context) {
        mContext = context;
    }

    /**
     * 设置显示模式
     *
     * @param mode public static final int OFF     = 0;
     *             public static final int RUNNING = 1;
     *             public static final int ON      = 2;
     */
    public void setMode(int mode) {
        if (currentMode==mode) {
            return;
        }
        currentMode=mode;
        switch (mode) {
            case OFF:
                isRunning = false;
                setOFFNotic();
                break;
            case RUNNING:
                isRunning = true;
                setRunningNotic();
                break;
            case ON:
                isRunning = false;
                setONNotic();
                break;
            case CANCEL:
                isRunning = false;
                cancelNotify(LIFE_ID);
                break;
        }
    }

    /**
     * 取消通知
     *
     * @param id
     */
    private void cancelNotify(int id) {
        NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(id);
        showingID.remove(id);
    }

    private void setONNotic() {
        //        showRunStateNotify(run2icon);
        showNotify(LIFE_ID, run2icon, "", "Enjoy Glocalme service", run2icon, "Enjoy Glocalme service");
    }

    private final Icon run1icon = Icon.createWithResource(mContext.getResources(), R.mipmap.icon_run_13);
    private final Icon run2icon = Icon.createWithResource(mContext.getResources(), R.mipmap.icon_run_2);
    private final Icon offIcon  = Icon.createWithResource(mContext.getResources(), R.mipmap.icon_lost2);

    private void setRunningNotic() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Icon showedIcon = run1icon;
                Notification notify = showNotify(LIFE_ID, showedIcon, "", "Glocalme service start", run2icon, "");
                while (isRunning) {
                    try {
                        changeNotify(showedIcon, notify, LIFE_ID);
                        showedIcon = showedIcon == run1icon ? run2icon : run1icon;
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void setOFFNotic() {
        showNotify(LIFE_ID, offIcon, "", "Glocalme service stop", run2icon, "Glocalme service stop");
    }

    private Notification showNotify(int id, Icon smallicon, CharSequence contentTitle, CharSequence contentText, Icon largeIcon, CharSequence ticker) {
        NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (showingID.contains(id)) {
            mNotificationManager.cancel(id);
        } else {
            showingID.add(id);
        }
        Intent notificationIntent = new Intent(mContext,InitActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);
        Notification.Builder builder = new Notification.Builder(mContext).setContentTitle(contentTitle).setContentText(contentText).setSmallIcon(smallicon).setLargeIcon(largeIcon).setTicker(ticker).setWhen(System.currentTimeMillis()).setContentIntent(contentIntent);
        //        notify.setSmallIcon(smallicon);
        Notification Noti = builder.build();
                Noti.flags|= Notification.FLAG_NO_CLEAR;
        mNotificationManager.notify(id, Noti);
        return Noti;
    }

    private void changeNotify(Icon smallicon, Notification Noti, int id) {
        NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Noti.setSmallIcon(smallicon);
        mNotificationManager.notify(id, Noti);
    }
}

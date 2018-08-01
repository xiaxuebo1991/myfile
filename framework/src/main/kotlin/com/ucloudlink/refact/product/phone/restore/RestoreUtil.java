package com.ucloudlink.refact.product.phone.restore;

import android.os.AsyncTask;

import com.ucloudlink.refact.ServiceManager;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * Created by chunjiao.li on 2017/1/13.
 */

public class RestoreUtil {
    private MobileRestore mobileRestore;
    public RestoreUtil(MobileRestore mobileRestore) {
        this.mobileRestore = mobileRestore;
    }

    /**
     * 还原用户手机配置检测
     */
    public void restoreMobileSettingsCheck(OnTaskListener listener) {
        new RestoreCheckTask(listener).execute();
    }

    /**
     * 还原用户手机配置检测任务
     */
    class RestoreCheckTask extends AsyncTask<Void, Integer, Boolean> {
        private OnTaskListener listener;

        public RestoreCheckTask(OnTaskListener listener) {
            this.listener = listener;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // 检测恢复状态是否完成
                int waitTime = 0;
                while (waitTime <= 15 && !mobileRestore.checkeRestore()) {
                    Thread.sleep(1000);
                    waitTime++;
                }

                if(mobileRestore.checkeRestore()){
                    return true;
                }else {
                    return false;
                }
            } catch (Exception e) {
                logd(e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                listener.onSuccess();
            } else {
                listener.onFailure();
            }
        }
    }

    /**
     * 还原用户手机配置
     */
    public void restoreMobileSettings() {
        new RestoreTask().execute();
    }

    /**
     * 还原用户手机配置检测任务
     */
    class RestoreTask extends AsyncTask<Void, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                //执行恢复操作后则返回true
                //小米手机要切两次，不然首选网络切不过去
                logv("RestoreUtil RestoreTask start");
                mobileRestore.setStartRestore(true);
                mobileRestore.setRestoreDone(false);
                mobileRestore.restoreMobileUserSettings();
                mobileRestore.restoreMobileUserSettings();
                mobileRestore.setRestoreDone(true);
                logv("RestoreUtil RestoreTask end");
                return true;
            } catch (Exception e) {
                logd(e);
                return false;
            }
        }
    }

    /**
     * 回调接口
     */
    public interface OnTaskListener {
        //在该回调里面执行登陆操作，也就是恢复设置完成后执行登陆
        void onSuccess();

        void onFailure();
    }
}

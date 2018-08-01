package com.ucloudlink.refact.utils;

import android.util.Log;


public class JLog {

    private static final int LEVEL_VERBOSE = 0;
    private static final int LEVEL_DEBUG = 1;
    private static final int LEVEL_INFO = 2;
    private static final int LEVEL_ERROR = 4;
    private static final int LEVEL_KEY = 5;

    private static int LEVEL = LEVEL_VERBOSE; //log 打印级别，默认全部打印
    public static Boolean isEncryptLog = false;

    private static final String TAG = "[JLog]";

    public static void logv(Object obj) {
        if ((LEVEL <= LEVEL_VERBOSE) && (obj != null)) {
            if (isEncryptLog) {
                Log.v(TAG, Rc4.encrypt((getClassName() + ": " + trimStr(obj.toString()))));
            } else {
                Log.v(getClassName(), obj.toString());
            }
        }
    }

    public static void logv(String filter, Object obj) {
        if ((LEVEL <= LEVEL_VERBOSE) && (obj != null)) {
            if (isEncryptLog) {
                Log.v(TAG, Rc4.encrypt(getClassName() + ": " + filter + " " + trimStr(obj.toString())));
            } else {
                Log.v(getClassName(), filter + ":" + obj.toString());
            }
        }
    }

    public static void logd(Object obj) {
        if ((LEVEL <= LEVEL_DEBUG) && (obj != null)) {
            if (isEncryptLog) {
                Log.d(TAG, Rc4.encrypt(getClassName() + ": " + trimStr(obj.toString())));
            } else {
                Log.d(getClassName(), obj.toString());
            }
        }
    }

    public static void logd(String filter, Object obj) {
        if ((LEVEL <= LEVEL_DEBUG) && (obj != null)) {
            if (isEncryptLog) {
                Log.d(TAG, Rc4.encrypt(getClassName() + ": " + filter + " " + trimStr(obj.toString())));
            } else {
                Log.d(getClassName(), filter + ":" + obj.toString());
            }
        }
    }

    public static void logi(Object obj) {
        if ((LEVEL <= LEVEL_INFO) && (obj != null)) {
            if (isEncryptLog) {
                Log.i(TAG, Rc4.encrypt(getClassName() + ": " + trimStr(obj.toString())));
            } else {
                Log.i(getClassName(), obj.toString());
            }
        }
    }

    public static void logi(String filter, Object obj) {
        if ((LEVEL <= LEVEL_INFO) && (obj != null)) {
            if (isEncryptLog) {
                Log.i(TAG, Rc4.encrypt(getClassName() + ": " + filter + " " + trimStr(obj.toString())));
            } else {
                Log.i(getClassName(), filter + ":" + obj.toString());
            }
        }
    }

    public static void loge(Object obj) {
        if ((LEVEL <= LEVEL_ERROR) && (obj != null)) {
            if (isEncryptLog) {
                Log.e(TAG, Rc4.encrypt(getClassName() + ": " + trimStr(obj.toString())));
            } else {
                Log.e(getClassName(), obj.toString());
            }
        }
    }

    public static void loge(String filter, Object obj) {
        if ((LEVEL <= LEVEL_ERROR) && (obj != null)) {
            if (isEncryptLog) {
                Log.e(TAG, Rc4.encrypt(getClassName() + ": " + filter + " " + trimStr(obj.toString())));
            } else {
                Log.e(getClassName(), filter + ":" + obj.toString());
            }
        }
    }

    /**
     * 关键打印 [Key Step]
     */
    public static void logk(Object obj) {
        if ((LEVEL <= LEVEL_KEY) && (obj != null)) {
            if (isEncryptLog) {
                Log.i(TAG, Rc4.encrypt(getClassName() + ": " + "[Key Step] " + trimStr(obj.toString())));
            } else {
                Log.i(getClassName(), "[Key Step] " + obj.toString());
            }
        }
    }

    /**
     * 关键错误打印 [Key Exception]
     */
    public static void logke(Object obj) {
        if ((LEVEL <= LEVEL_KEY) && (obj != null)) {
            if (isEncryptLog) {
                Log.e(TAG, Rc4.encrypt(getClassName() + ": " + "[Key Exception] " + trimStr(obj.toString())));
            } else {
                Log.e(getClassName(), "[Key Exception] " + obj.toString());
            }
        }
    }

    private static String getClassName() {
        String result;
        StackTraceElement[] trace = new Exception().getStackTrace();
        if (trace == null || trace.length < 2)
            return "NullClassName";

        result = trace[2].getClassName();
        int lastIndex = result.lastIndexOf(".");
        result = result.substring(lastIndex + 1, result.length());
        String[] temp = result.split("\\$");
        if (temp.length >= 1) {
            result = temp[0];
        }
        return result;
    }

    private static String trimStr(String str) {
        if (str.length() <= 4000) {
            return str;
        } else {
            return str.substring(0, 4000);
        }
    }
}

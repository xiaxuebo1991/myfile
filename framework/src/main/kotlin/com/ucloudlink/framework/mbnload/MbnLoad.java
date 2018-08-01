package com.ucloudlink.framework.mbnload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;

/**
 * Created by wangliang on 2016/5/23.
 */
public class MbnLoad {
//    private static final String TAG = "MbnLoad";
//    private boolean mQcRilHookReady = false;
//    private QcRilHook mQcRilHook;
//    public static int MBN_TYPE_SW = 0;
//    public static int MBN_TYPE_HW = 1;
//    private final int EVENT_QCRIL_HOOK_READY = 1;
//    private final int EVENT_DISMISS_REBOOT_DIALOG = 2;
//    static final String PROPERTY_RADIO_MBN_UPDATE = "persist.radio.sw_mbn_update";
//    private RegistrantList mQcRilHookReadyRegistrants = new RegistrantList();
//
//    private static int LOAD_ONLY = 0;
//    private static int LOAD_SUB0 = 1;
//    private static int LOAD_SUB1 = 2;
//    private static int LOAD_SUB0_AND_SUB1 = 3;
//
//    private static int SW_MBN_TYPE = 0;
//    private static int HW_MBN_TYPE = 1;
//    public int currMBN_type = -1;
//
//    volatile private boolean LOAD_HWMBN_RESULT = false;
//    volatile private boolean SELECT_HWMBN_RESULT = false;
//    volatile private boolean ACTIVE_HWMBN_RESULT = false;
//    volatile private boolean LOAD_SWMBN_RESULT = false;
//    volatile private boolean SELECT_SWMBN_RESULT = false;
//    volatile private boolean ACTIVE_SWMBN_RESULT = false;
//
//    public int RETRY_TIMES = 5;
//
//    private Context mMContext;
//    private mbnLoadReceiver mMbnLoadReceiver;
//
//    public static final String STEP_START = "STEP_START";
//    public static final String STEP_LOAD_HW = "STEP_LOAD_HW";
//    public static final String STEP_LOAD_HW_OK = "STEP_LOAD_HW_OK";
//    public static final String STEP_DISABLE_MBNSELECTION = "STEP_DISABLE_MBNSELECTION";
//    public static final String STEP_SELECT_HW = "STEP_SELECT_HW";
//    public static final String STEP_SELECT_HW_OK = "STEP_SELECT_HW_OK";
//    public static final String STEP_LOAD_SW = "STEP_LOAD_SW";
//    public static final String STEP_LOAD_SW_OK = "STEP_LOAD_SW_OK";
//    public static final String STEP_SELECT_SW = "STEP_SELECT_SW";
//    public static final String STEP_SELECT_SW_OK = "STEP_SELECT_SW_OK";
//    private static final String STEP_ACTIVATE_HW = "STEP_ACTIVATE_HW";
//    public String currStep = STEP_START;
//    private static final String STEP_LOAD_SW_FAIL = "STEP_LOAD_SW_FAIL";
//    private static final String STEP_ACTIVATE_SW = "STEP_ACTIVATE_SW";
//
//    public MbnLoad(Context mContext) {
//        mMContext = mContext;
//        mQcRilHook = new QcRilHook(mContext, mQcRilHookCallback);
//        registerQcRilHookReady(mHandler, EVENT_QCRIL_HOOK_READY, null);
//    }
//
//    public void enterMbnLoad() {
//        if (mQcRilHookReady) {
//            logd( "mQcRilHook is Ready ");
//            IntentFilter filter = new IntentFilter();
//            filter.addAction(MbnTestUtils.PDC_DATA_ACTION);
//            mMbnLoadReceiver = new mbnLoadReceiver();
//            mMContext.registerReceiver(mMbnLoadReceiver, filter);
//
////            String CurhwMBNConfigsub0 = getMbnConfig(0, HW_MBN_TYPE);
////            String CurhwMetasub0 = getMetaInfoForConfig(CurhwMBNConfigsub0, HW_MBN_TYPE);
////
////            String CurhwMBNConfigsub1 = getMbnConfig(1, HW_MBN_TYPE);
////            String CurhwMetasub1 = getMetaInfoForConfig(CurhwMBNConfigsub1, HW_MBN_TYPE);
////
////            String CurswMBNConfigsub0 = getMbnConfig(0, SW_MBN_TYPE);
////            String CurswMeta0 = getMetaInfoForConfig(CurswMBNConfigsub0, SW_MBN_TYPE);
////
////            String CurswMBNConfigsub1 = getMbnConfig(1, SW_MBN_TYPE);
////            String CurswMeta1 = getMetaInfoForConfig(CurswMBNConfigsub1, SW_MBN_TYPE);
////
////            logd( "CurhwMBNConfigsub0:" + CurhwMBNConfigsub0 + " CurhwMBNConfigsub1:" + CurhwMBNConfigsub1 + " CurswMBNConfigsub0:" + CurswMBNConfigsub0 + " CurswMBNConfigsub1:" + CurswMBNConfigsub1);
////            logd( "CurhwMetasub0:" + CurhwMetasub0 + " CurhwMetasub1:" + CurhwMetasub1 + " CurswMeta0:" + CurswMeta0 + " CurswMeta1:" + CurswMeta1);
//
//            boolean deactivateRet = deactivateAllMBN();
//            logd( "enterMbnLoad: deactivateRet:" + deactivateRet);
//            loadActivateHwMbn();
//        } else {
//            loge( "mQcRilHook is not Ready");
//        }
//    }
//
//    private boolean deactivateAllMBN() {
//        boolean retdeactivate = deactivateAllConfigs();
//        boolean retcleanUp = cleanUpConfigs();
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return retdeactivate && retcleanUp;
//    }
//
//    public void exitMbnLoad() {
////        enableAPPSMbnSelection();
//        try {
//            if (mMContext != null) {
//                mMContext.unregisterReceiver(mMbnLoadReceiver);
//            }
//        } catch (IllegalArgumentException e) {
//            e.printStackTrace();
//        }
//
//    }
//
//    public void setStep(String step) {
//        logi( "MbnLoad======>previous step:[" + currStep + "]>>>current Step:[" + step + "]");
//        currStep = step;
//    }
//
//    public void disableAPPSMbnSelection() {
//        SystemProperties.set(PROPERTY_RADIO_MBN_UPDATE, "0");
//        logd( "disable APPSMbn Selection");
//        setStep(STEP_DISABLE_MBNSELECTION);
//        //SystemProperties.set(MbnTestUtils.PROPERTY_MBN_TYPE, MbnTestUtils.TEST_MBN);
//    }
//
//    public void enableAPPSMbnSelection() {
//        logi( "enable APPSMbn Selection");
//        SystemProperties.set(PROPERTY_RADIO_MBN_UPDATE, "1");
//    }
//
//    public boolean loadActivateHwMbn() {
//        //HW_MBN_RADIO
//        boolean ret;
//        currMBN_type = HW_MBN_TYPE;
//        String file = MbnTestUtils.HW_MBN_RADIO;
//        String config = MbnTestUtils.HW_MBN_RADIO;
//        setStep(STEP_LOAD_HW);
//
//        int i = 0;
//        do {
//            if (i > 0) {
//                sleep(200);
//            }
//            ret = mQcRilHook.qcRilSetConfig(file, config, LOAD_SUB0, HW_MBN_TYPE);
//        } while (!ret && i++ < RETRY_TIMES);
//        LOAD_HWMBN_RESULT = ret;
//        logd( "loadActivateHwMbn qcRilSetConfig ret:" + ret + "param file=" + file + "param config=" + config);
//        return ret;
//    }
//
//    private void sleep(int Millisecond) {
//        try {
//            Thread.sleep(Millisecond);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public boolean loadActivateSwMbn() {
//        boolean ret;
//        setStep(STEP_LOAD_SW);
//        currMBN_type = SW_MBN_TYPE;
//        String file = MbnTestUtils.SW_MBN_RADIO;
//        String config = MbnTestUtils.SW_MBN_RADIO;
//
//        int i = 0;
//        do {
//            if (i > 0) {
//                sleep(200);
//            }
//            ret = mQcRilHook.qcRilSetConfig(file, config, LOAD_SUB0_AND_SUB1, SW_MBN_TYPE);
//        } while (!ret && i++ < RETRY_TIMES);
//
//        logd( "loadActivateSwMbn qcRilSetConfig ret:" + ret + " param file=" + file + "param config=" + config);
//        return ret;
//    }
//
//    public boolean setupMbnConfig(String fileName, String config, int subMask) {
//        logd( "setupMbnConfig, mQcRilHookReady:" + mQcRilHookReady +
//                " fileName:" + fileName + " Config:" + config + " Mask" + subMask);
//        if (!mQcRilHookReady || config == null || fileName == null) {
//            return false;
//        }
//        int mbnType = MbnTestUtils.isCurrentSwMbnMode() ? MBN_TYPE_SW : MBN_TYPE_HW;
//        return mQcRilHook.qcRilSetConfig(fileName, config, subMask, mbnType);
//    }
//
//    public boolean selectConfig(String config, int subMask) {
//        logd( "selectConfig, mQcRilHookReady:" + mQcRilHookReady +
//                " Config:" + config + " Mask:" + subMask);
//        if (!mQcRilHookReady) {
//            return false;
//        }
//        int mbnType = MbnTestUtils.isCurrentSwMbnMode() ? MBN_TYPE_SW : MBN_TYPE_HW;
//        //deactivateConfigs();
//        return mQcRilHook.qcRilSelectConfig(config, subMask, mbnType);
//    }
//
//    public String getMbnConfig(int sub, int mbnType) {
//        logd( "getMbnConfig, mQcRilHookReady:" + mQcRilHookReady + " Sub:" + sub
//                + " mbnType:" + mbnType);
//        if (!mQcRilHookReady) {
//            return null;
//        }
//        return mQcRilHook.qcRilGetConfig(sub, mbnType);
//    }
//
//    public String getMetaInfoForConfig(String config, int mbnType) {
//        logd( "getMetaInfoForConfig, mQcRilHookReady:" + mQcRilHookReady +
//                " config:" + config);
//        if (!mQcRilHookReady || config == null) {
//            return null;
//        }
//        return mQcRilHook.qcRilGetMetaInfoForConfig(config, mbnType);
//    }
//
//    private boolean deactivateAllConfigs() {
//        logd( "deactivateAllConfigs, mQcRilHookReady:" + mQcRilHookReady);
//        if (!mQcRilHookReady) {
//            return false;
//        }
//        return (mQcRilHook.qcRilDeactivateConfigs(MBN_TYPE_SW)
//                ? mQcRilHook.qcRilDeactivateConfigs(MBN_TYPE_HW) : false);
//    }
//
//    private boolean cleanUpConfigs() {
//        logd( "cleanUpConfigs, mQcRilHookReady:" + mQcRilHookReady);
//        if (!mQcRilHookReady) {
//            return false;
//        }
//
//        return mQcRilHook.qcRilCleanupConfigs();
//    }
//
//    // QcRilHook Callback
//    private QcRilHookCallback mQcRilHookCallback = new QcRilHookCallback() {
//        @Override
//        public void onQcRilHookReady() {
//            logd( "onQcRilHookReady QcRilHook is ready threadid:" + Thread.currentThread().getId());
//            synchronized (mQcRilHookReadyRegistrants) {
//                mQcRilHookReady = true;
//                mQcRilHookReadyRegistrants.notifyRegistrants();
//                logd( "onQcRilHookReady: " + mQcRilHookReady);
//            }
//        }
//
//        public void onQcRilHookDisconnected() {
//            // TODO: Handle onQcRilHookDisconnected
//            loge( "onQcRilHookDisconnected");
//        }
//    };
//
//    // Register for QcRilHook Ready
//    private void registerQcRilHookReady(Handler handler, int what, Object obj) {
//        Registrant r = new Registrant(handler, what, obj);
//        synchronized (mQcRilHookReadyRegistrants) {
//            mQcRilHookReadyRegistrants.add(r);
//            if (mQcRilHookReady) {
//                r.notifyRegistrant();
//            }
//        }
//    }
//
//    // unRegister for QcRilHook Ready
//    public void unregisterQcRilHookReady(Handler handler) {
//        synchronized (mQcRilHookReadyRegistrants) {
//            mQcRilHookReadyRegistrants.remove(handler);
//        }
//    }
//
//
//    private Handler mHandler = new Handler() {
//        @Override
//        public void dispatchMessage(Message msg) {
//            switch (msg.what) {
//                case EVENT_QCRIL_HOOK_READY:
//                    logd( "EVENT_QCRIL_HOOK_READY threadid:" + Thread.currentThread().getId());
//                    //                    MbnAppGlobals.getInstance().unregisterQcRilHookReady(mHandler);
//                    //                    setActivityView();
//                    //enterMbnLoad();
//                    //TODO need set View here.
//                    break;
//                case EVENT_DISMISS_REBOOT_DIALOG:
//                    logd( "EVENT_DISMISS_REBOOT_DIALOG");
//                    //Only set config when activating successfully.
//                    //                    if (msg.arg1 == NEED_REBOOT) {
//                    //                        String carrier = mMbnMetaInfoList.get(mMetaInfoChoice).getCarrier().toLowerCase();
//                    //                        new Thread(new PostSettingThread(carrier)).start();
//                    //                    } else {
//                    //                        mProgressDialog.dismiss();
//                    //                        MbnTestUtils.showToast(mContext, mContext.getString(R.string.fail_to_activate_mbn));
//                    //                    }
//                    break;
//                default:
//                    break;
//            }
//        }
//    };
//
//    private class mbnLoadReceiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
////            String action = intent.getAction();
////            if (action.equals(MbnTestUtils.PDC_DATA_ACTION)) {
////                logd( "get broadcase action= " + action + ",intent" + intent.toString());
////                byte[] result = intent.getByteArrayExtra(MbnTestUtils.PDC_ACTIVATED);
////                ByteBuffer payload = ByteBuffer.wrap(result);
////                payload.order(ByteOrder.nativeOrder());
////                int error = payload.get();
////                int sub = intent.getIntExtra(MbnTestUtils.SUB_ID, MbnTestUtils.DEFAULT_SUB);
////                logd( "broadcase onReceive :Sub:" + sub + " activated:" + new String(result) + " error code:" + error + "currMBN_type:" + currMBN_type);
////                if (error == MbnTestUtils.LOAD_MBN_SUCCESS) {
////                    logd( "onReceive: qcRilSetConfig success");
////                    boolean ret;
////                    if (currMBN_type == HW_MBN_TYPE) {
////                        //try active hw mbn
////                        tryActiveHwMbn();
////                        LOAD_SWMBN_RESULT = loadActivateSwMbn();
////                    } else if (currMBN_type == SW_MBN_TYPE) {
////                        //selectConfig
////                        if (currStep.equals(STEP_LOAD_SW)) {
////                            //try active sw mbn
////                            tryActiveSwMbn();
////                        }
////                    }
////                }
////            } else {
////                logd( "onReceive: qcRilSetConfig fail");
////            }
//        }
//    }
//
//    private boolean tryActiveHwMbn() {
//        boolean ret;
//        setStep(STEP_SELECT_HW);
//        String config = MbnTestUtils.HW_MBN_RADIO;
//        int i = 0;
//        do {
//            if (i > 0) {
//                sleep(200);
//            }
//            ret = mQcRilHook.qcRilSelectConfig(config, LOAD_SUB0, HW_MBN_TYPE);
//        } while (!ret && i++ < RETRY_TIMES);
//        logd( "loadActivateHwMbn qcRilSelectConfig ret:" + ret + " param:config:" + config);
//        setStep(STEP_ACTIVATE_HW);
//        SELECT_HWMBN_RESULT = ret;
//
//        if (ret) {
//            i = 0;
//            do {
//                if (i > 0) {
//                    sleep(200);
//                }
//                ret = mQcRilHook.qcRilActivateConfig(0, HW_MBN_TYPE); // sub0
//            } while (!ret && i++ < RETRY_TIMES);
//
//            logd( "loadActivateHwMbn qcRilActivateConfig ret:" + ret);
//        }
//        ACTIVE_HWMBN_RESULT = ret;
//
//        return ret;
//    }
//
//    private boolean tryActiveSwMbn() {
//        boolean ret;
//        setStep(STEP_SELECT_SW);
//        String config = MbnTestUtils.SW_MBN_RADIO;
//        int i = 0;
//        do {
//            if (i > 0) {
//                sleep(200);
//            }
//            ret = mQcRilHook.qcRilSelectConfig(config, LOAD_SUB0_AND_SUB1, SW_MBN_TYPE);
//        } while (!ret && i++ < RETRY_TIMES);
//
//        logd( "loadActivateSwMbn qcRilSelectConfig ret:" + ret + " param:config:" + config);
//        setStep(STEP_ACTIVATE_SW);
//        SELECT_SWMBN_RESULT = ret;
//
//        if (ret) {
//            i = 0;
//            do {
//                if (i > 0) {
//                    sleep(200);
//                }
//                ret = mQcRilHook.qcRilActivateConfig(0, SW_MBN_TYPE); // sub0
//            } while (!ret && i++ < RETRY_TIMES);
//
//            logd( "sub0 loadActivateSwMbn qcRilActivateConfig ret:" + ret);
//        }
//
//        if (ret) {
//            i = 0;
//            do {
//                if (i > 0) {
//                    sleep(200);
//                }
//                ret = mQcRilHook.qcRilActivateConfig(1, SW_MBN_TYPE); // sub1
//            } while (!ret && i++ < RETRY_TIMES);
//
//            logd( "sub1 loadActivateSwMbn qcRilActivateConfig ret:" + ret);
//        }
//        ACTIVE_SWMBN_RESULT = ret;
//
//        if (ret) {
//            disableAPPSMbnSelection();
//        }
//
//        return ret;
//    }
//
//    public boolean checkResult() {
//        return LOAD_HWMBN_RESULT && SELECT_HWMBN_RESULT && ACTIVE_HWMBN_RESULT
//                && LOAD_SWMBN_RESULT && SELECT_SWMBN_RESULT && ACTIVE_SWMBN_RESULT;
//    }
}


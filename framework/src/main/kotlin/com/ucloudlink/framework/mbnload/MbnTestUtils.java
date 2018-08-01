/**
 * Copyright (c) 2014-2015 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 **/

package com.ucloudlink.framework.mbnload;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.widget.Toast;

import static com.ucloudlink.refact.utils.JLog.logd;

public class MbnTestUtils {
    private static final String TAG = "MbnUtils";
    //public static final String FIRMWARE_PARTITION_PATH = "/firmware/image";
    public static final String FIRMWARE_PARTITION_PATH =
            "/firmware/image/modem_pr/mcfg/configs/mcfg_sw/generic";
    public static final String HW_MBN_BASE_PATH =
            "/firmware/image/modem_pr/mcfg/configs/mcfg_hw/generic/common";
    public static final String GOLDEN_MBN_DIR = FIRMWARE_PARTITION_PATH;
    public static final String MBN_TO_GO_DIR = "/data/misc/radio/";


    //ROOT/Carrier/Device Type/MultiMode/
    public static final int MBN_PATH_NORMAL_MAX_LEVEL = 3;
    public static final int MBN_PATH_ABNORMAL_MAX_LEVEL = 2;
    public static final int HW_MBN_PATH_NORMAL_MAX_LEVEL = 4;
    public static final int HW_MBN_PATH_ABNORMAL_MAX_LEVEL = 5;

    //TODO why need so many broadcasts?
    public static final String PDC_DATA_ACTION =
            "qualcomm.intent.action.ACTION_PDC_DATA_RECEIVED";
    public static final String PDC_CONFIG_CLEARED_ACTION =
            "qualcomm.intent.action.ACTION_PDC_CONFIGS_CLEARED";
    public static final String PDC_CONFIGS_VALIDATION_ACTION =
            "qualcomm.intent.action.ACTION_PDC_CONFIGS_VALIDATION";

    public static final String SUB_ID = "sub_id";
    public static final String PDC_ACTIVATED = "active";
    public static final String PDC_ERROR_CODE = "error";

    public static final int LOAD_MBN_SUCCESS = 0;
    public static final int LOAD_MBN_FAILED = -1;
    public static final int LOAD_MBN_NEED_CLEANUP = -2;

    public static final int CLEANUP_SUCCESS = 0;
    public static final int CLEANUP_FAILED = -1;
    public static final int CLEANUP_ALREADY = -3;

    public static final int SUB0 = 0;
    public static final int SUB1 = 1;
    public static final int SUB2 = 2;
    public static final int DEFAULT_SUB = SUB0;

    public static final int MBN_FROM_UNKNOWN = 0;
    public static final int MBN_FROM_APP = 1;
    public static final int MBN_FROM_GOLDEN = 2;
    public static final int MBN_FROM_PC = 3;
    public static final String APP_MBN_ID_PREFIX = "MbnApp_";
    public static final String GOLDEN_MBN_ID_PREFIX = "GOLDEN_";
    public static final String MBN_FILE_SUFFIX = ".mbn";

    public static final int DEFAULT_COLOR = 0xFFCCCCFF;

    public final static String PROPERTY_MBN_TYPE = "persist.radio.mbn_type";
    public final static String COMMERCIAL_MBN = "commercial";
    public final static String TEST_MBN = "test";

    public final static String PROPERTY_GPS_TARGET = "persist.radio.sglte_target";

    public static boolean mIsSwMbnMode = true;

    public static final String HW_MBN_FIRMWARE = "/firmware/image/modem_pr/mcfg/configs/mcfg_hw/generic/common/msm8952/la/7+1_mode/sr_dsds/mcfg_hw.mbn"; //system
    public static final String SW_MBN_FIRMWARE = "/firmware/image/modem_pr/mcfg/configs/mcfg_sw/generic/common/row/gen_3gpp/mcfg_sw.mbn";
    //mbn文件原始路径,
    public static final String HW_MBN_RADIO_ORG = "/data/misc/radio/modem_config/mcfg_hw/generic/common/remotesi/la/7+5_remo/sr_dsds/mcfg_hw.mbn";
    public static final String SW_MBN_RADIO_ORG = "/data/misc/radio/modem_config/mcfg_sw/generic/common/row/gen_3gpp/mcfg_sw.mbn";
    //将原始mbn先拷贝到/data/misc/radio/目录下，之后进行load-->activity
    public static final String HW_MBN_RADIO = "/data/misc/radio/mcfg_hw.mbn";
    public static final String SW_MBN_RADIO = "/data/misc/radio/mcfg_sw.mbn";
    public static final String HW_MBN_SYSTEM_CONFIG = APP_MBN_ID_PREFIX + HW_MBN_FIRMWARE;
    public static final String SW_MBN_SYSTEM_CONFIG = APP_MBN_ID_PREFIX + SW_MBN_FIRMWARE;
    //Config:MbnApp_/firmware/image/modem_pr/mcfg/configs/mcfg_hw/generic/common/msm8952/la/7+5_mode/sr_dsds/mcfg_hw.mbn

    public static String getGeneralMbnPath() {
        String value = isCurrentSwMbnMode() ? getPropertyValue("persist.radio.app_mbn_path") :
                getPropertyValue("persist.radio.app_hw_mbn_path");
        logd( "MbnTest_ persist.radio.app_mbn_path or persist.radio.app_hw_mbn_path is |"
                + value + "|");
        if (TextUtils.isEmpty(value)) {
            return isCurrentSwMbnMode() ? FIRMWARE_PARTITION_PATH : getDefaultHwMbnPath();
        } else {
            return value;
        }
    }

    private static String getDefaultHwMbnPath() {
        //return base path as per new requirement, user to select correct dir
        return HW_MBN_BASE_PATH;
    }

    private static String getDeviceDirName(String device) {
        boolean isQrd = MbnFileManager.isHwTypeQrd();
        if (isQrd) {
            return "QRD" + device.substring(3);
        } else {
            return "MSM" + device.substring(3);
        }
    }

    public static String getGoldenMbnPath() {
        String value = getPropertyValue("persist.radio.golden_mbn_path");
        logd( "MbnTest_ persist.radio.golden_mbn_path, |" + value + "|");
        if (TextUtils.isEmpty(value)) {
            return GOLDEN_MBN_DIR;
        } else {
            return value;
        }
    }

    public static boolean isCurrentSwMbnMode() {
        return mIsSwMbnMode;
    }

    public static void setSwMbnMode(boolean isSwMbnMode) {
        mIsSwMbnMode = isSwMbnMode;
    }

    public static boolean mbnNeedToGo() {
        String value = getPropertyValue("persist.radio.mbn_to_go");
        //logd( "MbnTest_ persist.radio.mbn_to_go, |" + value + "|");
        if (value != null && value.toLowerCase().equals("true")) {
            return true;
        } else {
            return true;
        }
    }

    public static boolean mbnValidationShowAll() {
        String value = getPropertyValue("persist.radio.mbn_show_all");
        logd( "MbnTest_ Validation show all:" + value);
        if (value != null && value.toLowerCase().equals("true")) {
            return true;
        } else {
            return false;
        }
    }

    // return Multi-Mode according to ss/da/ds
    public static String getMultiSimMode(String mode) {
        if (mode != null) {
            if (mode.toLowerCase().contains("ss") || mode.toLowerCase().contains("singlesim")) {
                return "ssss";
            } else if (mode.toLowerCase().contains("dsda")) {
                return "dsda";
            } else if (mode.toLowerCase().contains("dsds")) {
                return "dsds";
            }
        }
        return "";
    }

//    public static void setupData(Context context, boolean enable) {
//        boolean disableFlag = true;
//        if (disableFlag == true) {
//            return;
//        }
//
//        logd( "MbnTest_ set mobile data, roaming data " + enable);
//        TelephonyManager telephoneManager = (TelephonyManager) context.getSystemService(
//                Context.TELEPHONY_SERVICE);
//        telephoneManager.setDataEnabled(enable);
//        for (int i = 0; i < MAX_PHONE_COUNT_TRI_SIM; i++) {
//            Settings.Global.putInt(context.getContentResolver(),
//                    Settings.Global.MOBILE_DATA + i, enable ? 1 : 0);
//            Settings.Global.putInt(context.getContentResolver(),
//                    Settings.Global.DATA_ROAMING + i, enable ? 1 : 0);
//        }
//    }

    public static String getPropertyValue(String property) {
        return SystemProperties.get(property);
    }

    public static boolean isMultiSimConfigure(String mode) {
        return mode.toLowerCase().contains("ds") || mode.toLowerCase().contains("da");
    }

    public static void rebootSystem(Context context) {
        Intent i = new Intent(Intent.ACTION_REBOOT);
        i.putExtra("nowait", 1);
        i.putExtra("interval", 1);
        i.putExtra("window", 0);
        context.sendBroadcast(i);
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}

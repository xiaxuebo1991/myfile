package com.ucloudlink.refact.config

/**
 * Created by jiaming.liang on 2017/4/19.
 */
//常量定义 用下面的方式
const val ACCESS_CLEAR_CARD = "clear card"
const val VSIM_APN_PREFIX = "VSim_Apn_"
const val REASON_CHANGE = "DATA_ENABLER_CHANGE"
const val REASON_MONITOR_RESTART = "monitor restart service"
const val REASON_MCC_CHANGE = "mcc change"
const val USER_LOGOUT = "USER LOGOUT"

const val SYSTEM_SP_KEY_LAST_MCC_LIST = "LAST_MCC_LIST"
const val CLOUDSIM_IMSI_STATE_SLOT = "_LAST_IMSI_SLOT"
const val SYSTEM_SP_KEY_LAST_SESSION_AND_TIME = "SESSION_TIME"
const val SYSTEM_SP_KEY_LAST_CLOUDSIM_JSON = "CLOUDSIM_JSON_"

const val ENV_MCC_CHANGED = "ENV_MCC_CHANGED"
//android O notification importance index:
const val IMPORTANCE_UNSPECIFIED = -1000

/**
 * A notification with no importance: does not show in the shade.
 */
const val IMPORTANCE_NONE = 0

/**
 * Min notification importance: only shows in the shade, below the fold.
 */
const val IMPORTANCE_MIN = 1

/**
 * Low notification importance: shows everywhere, but is not intrusive.
 */
const val IMPORTANCE_LOW = 2

/**
 * Default notification importance: shows everywhere, makes noise, but does not visually
 * intrude.
 */
const val IMPORTANCE_DEFAULT = 3

/**
 * Higher notification importance: shows everywhere, makes noise and peeks. May use full screen
 * intents.
 */
const val IMPORTANCE_HIGH = 4

/**
 * Unused.
 */
const val IMPORTANCE_MAX = 5
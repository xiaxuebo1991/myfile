package com.ucloudlink.refact.product.mifi.seedUpdate.event

/**
 * Created by jiaming.liang on 2018/2/3.
 */
//种子卡信息上报的事件
const val SEED_EVENT_INIT = 1
const val SEED_EVENT_END = 2
const val SEED_DO_UPLOAD = 3
const val SEED_BIND_WITH_SERVER = 20

//种子卡下载，更新的任务事件
const val SEED_GET_SIM_INFO = 4
const val SEED_GET_RULE_LIST_BIN = 5
const val START_HANDLE_TASK_LIST = 6
const val SEED_EVENT_DOWNLOAD_DONE = 7
const val SEED_EVENT_UPDATE_DONE = 8

const val SEED_UPDATE_REPORT_SUCCESS = 9
const val SEED_UPDATE_REPORT_FAIL = 10
const val SEED_UPDATE_REPORT_TO_SERVER = 11 //执行发送成功到服务器
const val SEED_UPDATE_ACTION_DONE = 12   //流程结束


//下载的文件类型
const val SEED_UPDATE_FILE_TYPE_RULE = 7
const val SEED_UPDATE_FILE_TYPE_BIN = 8

//种子配置更新类型 ,不要修改这几个对应的值，这些值对应S2C种子卡更新的类型的值
const val UPDATE_TYPE_PLMN_BIN = 1
const val UPDATE_TYPE_FEE_BIN = 2
const val UPDATE_TYPE_FPLMN_BIN = 3
const val UPDATE_TYPE_RULE_LIST = 4
const val UPDATE_TYPE_SIM_INFO = 5

//种子卡上报时，上报的卡类型常量值
const val SEED_SIM_CARD_TYPE_SOFT_SIM = 1
const val SEED_SIM_CARD_TYPE_PHY = 2

//种子卡上报的请求tag
const val TAG_UPLOAD_SOFTSIM_LIST = "TAG_UPLOAD_SOFTSIM_LIST"

//上报错误给服务的错误码值
const val EXT_SOFTSIM_SUCCESS = 0
const val EXT_SOFTSIM_ERROR_QUERY_LIST = 1
const val EXT_SOFTSIM_ERROR_UPLOAD_LIST = 2
const val EXT_SOFTSIM_ERROR_GET_SIMINFO = 3
const val EXT_SOFTSIM_ERROR_GET_RULE = 4
const val EXT_SOFTSIM_ERROR_GET_BIN = 5
const val EXT_SOFTSIM_ERROR_UPLOAD_RECLAIM_LIST = 6
const val EXT_SOFTSIM_ERROR_TRX_ADDLIST = 7
const val EXT_SOFTSIM_ERROR_TRX_DELLIST = 8
const val EXT_SOFTSIM_ERROR_TRX_SIMINFO = 9
const val EXT_SOFTSIM_ERROR_TRX_SIMIMG = 10
const val EXT_SOFTSIM_ERROR_TRX_RATE = 11
const val EXT_SOFTSIM_ERROR_TRX_FPLMN = 12
const val EXT_SOFTSIM_ERROR_APPLY = 13
const val EXT_SOFTSIM_ERROR_CONFIRM = 14
const val EXT_SOFTSIM_ERROR_UPDATE_SIMINFO = 15
const val EXT_SOFTSIM_ERROR_UPDATE_BIN = 16
const val EXT_SOFTSIM_ERROR_UPDATE_RULE = 17
const val EXT_SOFTSIM_ERROR_UPDATE_APPLY = 18
const val EXT_SOFTSIM_ERROR_UPDATE_CONFIRM = 19
const val EXT_SOFTSIM_ERROR_TIMEOUT = 20

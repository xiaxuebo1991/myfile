package com.ucloudlink.refact.utils

import java.util.*

/**
 * Created by wuzhun on 2016/12/29.
 */
/**
 * Glocalme业务步骤的枚举
 * */
enum class ProcessState{
    UC_PROCESS_INIT,
    UI_RESET_REQ,
    AUTO_RUN_START,
    SEED_ENABLE,
    SEED_REG_OK,
    SEED_CONNECTED,
    SEED_TRY_SOCKET,
    SEED_SOCKET_OK,
    LOGIN_START,
    LOGIN_OK,
    CLOUD_DOWNLOAD_OK,
    CLOUD_SIM_START,
    CLOUD_AUTH_REQ,
    CLOUD_AUTH_FAIL,
    CLOUD_AUTH_RSP,
    CLOUD_REG_OK,
    CLOUD_CONNECTED,
    CLOUD_AUTH2_START,
    CLOUD_AUTH2_OK,
    SEED_ENABLE_2,
    SEED_CONNNECTED_2
}

data class ProcessRecords(var processState: ProcessState, var happenTime : Date)
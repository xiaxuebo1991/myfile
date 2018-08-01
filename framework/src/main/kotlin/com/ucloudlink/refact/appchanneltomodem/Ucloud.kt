package com.ucloudlink.refact.card

/**
 * Created by ucloudlink on 2017/9/28.
 */

/*data class UcloudServiceEvent,用于app上层逻辑与socket通信*/
data class UcloudServiceEvent(val slot: Int = UCLOUDLINK_SLOT0,
                               val event: Int,
                               val eventByte: ByteArray,
                               val subEvent:Int = 0,
                               val subEventString:String = "",
                               val errorCode: Int = UCLOUDLINK_ERROR_NONE,
                               val eventTimeout: Int = DEFAULT_UCLOUDLINK_TIMEOUT) {
    companion object {
        /*定义app与底层交互事件*/
        const val UCLOUDLINK_SERVICE_EVENT_NONE = 0
        const val UCLOUDLINK_CONNECT_SOCKET = 1
        const val UCLOUDLINK_DISCONNECT_SOCKET = 2
        const val UCLOUDLINK_SET_PLMNLINTBIN_TO_MODEM = 3
        const val UCLOUDLINK_SET_NETWORK_PREF = 4
        const val UCLOUDLINK_GET_MCFG_REFRESH_VALUE = 5

        const val UCLOUDLINK_SLOT0 = 0;
        const val UCLOUDLINK_SLOT1 = 1;
        const val UCLOUDLINK_SLOT2 = 2;

        //      This param will be non-zero only for UIM_REMOTE_CARD_ERROR event
        const val UCLOUDLINK_ERROR_NONE = 0;
        const val UCLOUDLINK_ERROR_UNKNOWN = 1;
        const val UCLOUDLINK_ERROR_NO_LINK_EST = 2;
        const val UCLOUDLINK_ERROR_CMD_TIMEOUT = 3;
        const val UCLOUDLINK_ERROR_POWER_DOWN = 4;

        /* @return*/
        const val UCLOUDLINK_SUCCESS = 0
        const val UCLOUDLINK_ERROR = 1

        const val DEFAULT_UCLOUDLINK_TIMEOUT = 14000 //in miliseconds

        const val UCLOUDLINK_EXCHANGE_SUCCESS = 0
        const val UCLOUDLINK_EXCHANGE_FAILURE = 1
    }
}
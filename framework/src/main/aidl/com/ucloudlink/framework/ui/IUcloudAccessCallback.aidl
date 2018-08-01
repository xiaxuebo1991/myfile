package com.ucloudlink.framework.ui;

import com.ucloudlink.framework.ui.PlmnInfo;

interface IUcloudAccessCallback {

    /**上报普通错误（流程会重试不退出）*/
    void errorUpdate(int errorCode,String message);

    /**上报打开云卡进度*/
    void processUpdate(int persent);

    /**上报云卡云卡业务停止事件（出现包含错误）*/
    void eventCloudsimServiceStop(int reason,String message);

    /**上报 云卡启动成功*/
    void eventCloudsimServiceSuccess();

    /**进入异常状态*/
    void enterExceptionState();

    /**退出异常状态*/
    void exitExceptionState();

    /**种子卡plmn*/
    void updateSeedPlmn(in PlmnInfo plmn);
    
    /**进入恢复状态*/
    void enterRecoveryState();

    /**退出恢复状态*/
    void exitRecoveryState();

    /**上报一些状态或事件*/
    /*
    * UPDATE_SOFTSIM_START = 1
    * UPDATE_SOFTSIM_OVER = 2   "succ"  "fail:code"
    * SPEED_LIMIT_START = 3   "up:xxx,down:xxx,display:(true|false)"
    * SPEED_LIMIT_STOP = 4      ""
    * WIFI_STATE_CHANGE = 5    "true" "false"
    * EXCEPTION_EVENT_START = 6    exceptionid
    * EXCEPTION_EVENT_STOP  = 7   exceptionid
    * MANUAL_UPDATE_SOFTSIM_RESULT = 9
    */
    void updateCommMessage(int code, String msg);
}

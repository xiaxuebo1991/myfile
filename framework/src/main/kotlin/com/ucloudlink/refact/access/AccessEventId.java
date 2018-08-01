package com.ucloudlink.refact.access;

/**
 * Created by shiqianhua on 2016/10/19.
 * 事件码规则定义,事件码分三级,
 * 第一级类为EVENT_BASE,
 * 第二级类为自定义的大类,如EVENT_NET_BASE,由EVENT_BASE+100-1000组成
 * 第三级类为具体事件ID,有第二级类+id组成,如如EVENT_NET_BASE+1-100
 * <p/>
 * 示例
 * private static final int EVENT_TEST=EVENT_BASE+100;//自定义大类
 * public static final int EVENT_TEST_EVENTID = EVENT_TEST + 1;//大类下具体事件ID
 */
public class AccessEventId {
    public static final int EVENT_BASE = 50000;

    private static final int EVENT_NET                   = EVENT_BASE + 100;
    public static final  int EVENT_NET_EXCEPTION         = EVENT_NET + 1; //socket重连接失败,提示网络异常
    public static final  int EVENT_NET_JAM               = EVENT_NET + 2; //socket重连接失败,提示网络堵塞
    public static final  int EVENT_NET_SOCKET_CONNECTED  = EVENT_NET + 3;
    public static final  int EVENT_NET_SOCKET_DISCONNECT = EVENT_NET + 4;
    public static final  int EVENT_NET_MCC_CHANGE        = EVENT_NET + 5;
    public static final  int EVENT_NET_SOFTSIM_CONNECTED         = EVENT_NET + 6;
    public static final  int EVENT_NET_PHYSIM_CONNECTED       = EVENT_NET + 7;
    //种子卡相关事件 ↓↓↓↓
    private static final int EVENT_SEEDSIM                        = EVENT_BASE + 200;
    public static final  int EVENT_SEEDSIM_CARD_LOST              = EVENT_SEEDSIM + 1;//种子卡不在位 50201
    public static final  int EVENT_EXCEPTION_DATA_NOT_ENABLED     = EVENT_SEEDSIM + 2;//数据开关没打开 50202
    public static final  int EVENT_SEEDSIM_ROAMING_NOT_ENABLED    = EVENT_SEEDSIM + 3;//需要漫游时,漫游开关没打开 50203
    public static final  int EVENT_SEEDSIM_START_PS_CALL          = EVENT_SEEDSIM + 4;// 种子卡开始dun拨号 50204
    public static final  int EVENT_SEEDSIM_PS_CALL_SUCC           = EVENT_SEEDSIM + 5;// 种子卡dun拨号成功 50205
    public static final  int EVENT_SEEDSIM_SOFTSIM_DEFAULT_ENABLE = EVENT_SEEDSIM + 6;// 种子卡软卡使能
    public static final  int EVENT_SEEDSIM_SOFTSIM_DUN_DISABLE    = EVENT_SEEDSIM + 7;// 种子卡软卡DUN去使能
    public static final  int EVENT_SEEDSIM_ENABLE                 = EVENT_SEEDSIM + 8;// 种子卡使能
    public static final  int EVENT_SEEDSIM_DISABLE                = EVENT_SEEDSIM + 9;// 种子卡去使能

    public static final int EVENT_SEEDSIM_INSERT               = EVENT_SEEDSIM + 10;// 种子卡插入
    public static final int EVENT_SEEDSIM_READY                = EVENT_SEEDSIM + 11;// 种子卡ready
    public static final int EVENT_SEEDSIM_IN_SERVICE           = EVENT_SEEDSIM + 12;// 种子卡注册上ps
    public static final int EVENT_SEEDSIM_OUT_OF_SERVICE       = EVENT_SEEDSIM + 13;// 种f子卡失去ps注册
    public static final int EVENT_SEEDSIM_ADD_TIMEOUT          = EVENT_SEEDSIM + 14;// 种子卡添加到库超时
    public static final int EVENT_SEEDSIM_INSERT_TIMEOUT       = EVENT_SEEDSIM + 15;// 种子卡插入超时
    public static final int EVENT_SEEDSIM_READY_TIMEOUT        = EVENT_SEEDSIM + 16;// 种子卡ready超时
    public static final int EVENT_SEEDSIM_INSERVICE_TIMEOUT    = EVENT_SEEDSIM + 17;// 种子卡注册超时
    public static final int EVENT_SEEDSIM_CONNECT_TIMEOUT      = EVENT_SEEDSIM + 18;// 种子卡拨号超时
    public static final int EVENT_SEEDSIM_CARD_NOT_READY       = EVENT_SEEDSIM + 19;// 种子卡被禁用
    public static final int EVENT_SEEDSIM_CARD_TIME_OUT        = EVENT_SEEDSIM + 20;// 种子卡被禁用超时
    public static final int EVENT_SEEDSIM_START                = EVENT_SEEDSIM + 21;
    public static final int EVENT_SEEDSIM_STOP                 = EVENT_SEEDSIM + 22;
    public static final int EVENT_SEEDSIM_DATA_CONNECT         = EVENT_SEEDSIM + 23;// 种子卡网络可用
    public static final int EVENT_SEEDSIM_DATA_DISCONNECT      = EVENT_SEEDSIM + 24;// 种子卡网络不可用
    public static final int EVENT_SEEDSIM_PHYCARD_DEFAULT_FAIL = EVENT_SEEDSIM + 25;// 物理卡作为种子卡时,default网络不可用
    public static final int EVENT_SEEDSIM_CRASH                = EVENT_SEEDSIM + 26;// 种子卡crash
    public static final int EVENT_SEEDSIM_RESET_CLOUD_SIM      = EVENT_SEEDSIM + 27;// 种子卡需要reset云卡,插拔

    public static final int EVENT_SEEDSIM_ENABLE_FAIL          = EVENT_SEEDSIM + 28;// 种子卡种子卡使能失败
    public static final int EVENT_SOFTSIM_ON                   = EVENT_SEEDSIM + 29;// 软卡在位
    public static final int EVENT_SOFTSIM_OFF                  = EVENT_SEEDSIM + 30;// 软卡不在位

    //云卡相关事件 ↓↓↓↓
    private static final int EVENT_CLOUDSIM                   = EVENT_BASE + 300;
    public static final  int EVENT_CLOUDSIM_DATA_ENABLED      = EVENT_CLOUDSIM + 1;//云卡数据业务可用了
    public static final  int EVENT_CLOUDSIM_DATA_LOST         = EVENT_CLOUDSIM + 2;//云卡数据业务不可用了
    public static final  int EVENT_CLOUDSIM_NEED_AUTH         = EVENT_CLOUDSIM + 3;//鉴权包
    public static final  int EVENT_CLOUDSIM_AUTH_REPLIED      = EVENT_CLOUDSIM + 4;//鉴权包回复
    public static final  int EVENT_CLOUDSIM_REGISTER_NETWORK  = EVENT_CLOUDSIM + 5;//注册网络成功
    public static final  int EVENT_CLOUDSIM_CARD_READY        = EVENT_CLOUDSIM + 6;//云卡ready
    public static final  int EVENT_CLOUDSIM_ADD_TIMEOUT       = EVENT_CLOUDSIM + 7;//云卡加数据库超时
    public static final  int EVENT_CLOUDSIM_INSERT_TIMEOUT    = EVENT_CLOUDSIM + 8;//云卡插入数据库超时
    public static final  int EVENT_CLOUDSIM_READY_TIMEOUT     = EVENT_CLOUDSIM + 9;//Radio 连接 云卡超时
    public static final  int EVENT_CLOUDSIM_INSERVICE_TIMEOUT = EVENT_CLOUDSIM + 10;//云卡注册超时
    public static final  int EVENT_CLOUDSIM_CONNECT_TIMEOUT   = EVENT_CLOUDSIM + 11;//云卡拨号超时
    public static final  int EVENT_CLOUDSIM_CARD_NOT_READY    = EVENT_CLOUDSIM + 12;//云卡被禁用
    public static final  int EVENT_CLOUDSIM_CARD_TIME_OUT     = EVENT_CLOUDSIM + 13;//云卡被禁用超时
    public static final  int EVENT_CLOUDSIM_DISABLE           = EVENT_CLOUDSIM + 14;//云卡被关闭
    public static final  int EVENT_CLOUDSIM_CRASH             = EVENT_CLOUDSIM + 15;//云卡crash
    public static final  int EVENT_CLOUDSIM_APDU_INVALID      = EVENT_CLOUDSIM + 16;//服务器返回的鉴权包无效，应触发换卡
    public static final  int EVENT_CLOUDSIM_OUT_OF_SERVICE    = EVENT_CLOUDSIM + 17; //云卡注册掉了

    //C2S命令相关事件 ↓↓↓↓
    private static final int EVENT_C2SCMD                  = EVENT_BASE + 400;
    public static final  int EVENT_C2SCMD_UPLOAD_FLOW_FAIL = EVENT_C2SCMD + 1; //流量上报任务失败
    public static final  int EVENT_C2SCMD_UPLOAD_FLOW_SUCC = EVENT_C2SCMD + 2; //流量上报任务成功
    

    private static final int EVENT_REMOTE_UIM                  = EVENT_BASE + 500;
    public static final  int EVENT_REMOTE_UIM_RESET_INDICATION = EVENT_REMOTE_UIM + 1; // 热换卡成功事件

    // 异常事件都加这里！！！
    private static final int EVENT_EXCEPTION_BASE                = EVENT_BASE + 600;
    public static final  int EVENT_EXCEPTION_AIRMODE_10MIN        = EVENT_EXCEPTION_BASE + 1; //已经废弃,在飞行模式下已经6分钟，退出服务
    //    public static final  int EVENT_EXCEPTION_WIFI_OFF            = EVENT_EXCEPTION_BASE + 2; // WiFi开关关闭
    //    public static final  int EVENT_EXCEPTION_WIFI_ON_NOT_HOTSPOT = EVENT_EXCEPTION_BASE + 3; // WiFi开关打开,没连接热点
    //    public static final  int EVENT_EXCEPTION_WIFI_ON_HOTSPOT     = EVENT_EXCEPTION_BASE + 4; // WiFi连接上热点
    //    public static final  int EVENT_EXCEPTION_WIFI_HOTSPOT_LOST   = EVENT_EXCEPTION_BASE + 5; // WiFi失去热点
    public static final  int EVENT_EXCEPTION_SET_DDS_ILLEGALITY  = EVENT_EXCEPTION_BASE + 6; //非法切换DDS异常
    public static final  int EVENT_EXCEPTION_AIRMODE_OPEN        = EVENT_EXCEPTION_BASE + 7; //打开飞行模式
    public static final  int EVENT_EXCEPTION_AIRMODE_CLOSE       = EVENT_EXCEPTION_BASE + 8; //关闭飞行模式
    public static final  int EVENT_EXCEPTION_PHONECALL_START     = EVENT_EXCEPTION_BASE + 9; // 打电话开始
    public static final  int EVENT_EXCEPTION_PHONECALL_STOP      = EVENT_EXCEPTION_BASE + 10;// 打电话结束
    public static final  int EVENT_EXCEPTION_SEED_CARD_DISABLE   = EVENT_EXCEPTION_BASE + 11;// 关卡开始
    public static final  int EVENT_EXCEPTION_SEED_CARD_ENABLE    = EVENT_EXCEPTION_BASE + 12;// 关卡结束
    public static final  int EVENT_EXCEPTION_COULD_CARD_DISABLE  = EVENT_EXCEPTION_BASE + 13;// 关卡开始
    public static final  int EVENT_EXCEPTION_COULD_CARD_ENABLE   = EVENT_EXCEPTION_BASE + 14;// 关卡结束
    public static final  int EVENT_EXCEPTION_PHONE_DATA_DISABLE  = EVENT_EXCEPTION_BASE + 15;// 移动数据关闭
    public static final  int EVENT_EXCEPTION_PHONE_DATA_ENABLE   = EVENT_EXCEPTION_BASE + 16;// 移动数据打开
    public static final  int EVENT_EXCEPTION_ADD_TO_BALCK_LIST   = EVENT_EXCEPTION_BASE + 17;// app加入网络黑名单
    public static final  int EVENT_EXCEPTION_DEL_FROM_BALCK_LIST = EVENT_EXCEPTION_BASE + 18;// app移除网络黑名单
    public static final  int EVENT_EXCEPTION_SET_DDS_NORMAL      = EVENT_EXCEPTION_BASE + 19; // dds正常切换
    public static final  int EVENT_EXCEPTION_VSIM_REJECT         = EVENT_EXCEPTION_BASE + 20; // 被拒绝
    public static final  int EVENT_EXCEPTION_VSIM_NET_FAIL       = EVENT_EXCEPTION_BASE + 21; // 测速失败
    public static final  int EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START       = EVENT_EXCEPTION_BASE + 22; // 呼出电话，处理out_going_call 开始
    public static final  int EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END       = EVENT_EXCEPTION_BASE + 23; // 呼出电话，处理out_going_call 结束
    public static final  int EVENT_EXCEPTION_SMART_VSIM_SWITCH_CARD = EVENT_EXCEPTION_BASE + 24;// 云卡智能优先换卡
    public static final  int EVENT_EXCEPTION_PHONECALL_START_BEFORE_SERVICE_START = EVENT_EXCEPTION_BASE + 25;//打电话之后启动service

    //网络事件
    public static final int EVENT_NET_BASE               = EVENT_BASE + 700;
    public static final int EVENT_NET_RECONNECT_MSG_FAIL = EVENT_NET_BASE + 1; // reconnect msg error
    public static final int EVENT_NET_APDU_MSG_FAIL      = EVENT_NET_BASE + 2;
    public static final int EVENT_NET_SECURITY_CHECK_FAIL = EVENT_NET_BASE + 3;
    public static final int EVENT_NET_SECURITY_CHECK_TIMEOUT = EVENT_NET_BASE + 4;
    public static final int EVENT_NET_BIND_DUN_FAIL      = EVENT_NET_BASE + 5;
    public static final int EVENT_NET_SOCKET_TIMEOUT        = EVENT_NET_BASE + 6;

    // 其他事件
    public static final int EVENT_MISC_BASE                       = EVENT_BASE + 800;
    public static final int EVENT_BUSINESS_RESTART                = EVENT_MISC_BASE + 1;
    public static final int EVENT_SEED_MCC_CHANGE                = EVENT_MISC_BASE + 2;

    //S2C命令相关事件 ↓↓↓↓
    public static final int EVENT_S2CCMD                     = EVENT_BASE + 1000;//服务器下发指令
    public static final int EVENT_S2CCMD_LIMIT_UP_DOWN_SPEED = EVENT_S2CCMD + 74;//限速指令 第二个参数传入 为限速等级 说明见CmdDefine.kt
    //服务器退出指令，附加参数传入值 1 帐户在其它地方登陆，终端要执行退出登陆操作，2帐户在其它地方登陆，终端无需执行退出登陆操作，服务器端立即释放相关资源
    public static final int EVENT_S2CCMD_LOGOUT              = EVENT_S2CCMD + 130;
    public static final int EVENT_S2CCMD_SWAPCLOUDSIM        = EVENT_S2CCMD + 131; //服务器发出换卡指令，参数传入值 为换卡原因
    public static final int EVENT_S2CCMD_RELOGIN             = EVENT_S2CCMD + 200;  // 重登陆
    public static final int EVENT_S2CCMD_DOWMLOAD_EXT_SOFTSIM_REQ = EVENT_S2CCMD + 27;//终端下载软卡
    public static final int EVENT_S2CCMD_UPDATE_EXT_SOFTSIM_REQ = EVENT_S2CCMD + 28;//终端下载软卡
}

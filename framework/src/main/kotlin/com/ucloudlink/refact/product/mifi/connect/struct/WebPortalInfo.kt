package com.ucloudlink.refact.product.mifi.connect.struct

/**
 * Created by shiqianhua on 2018/1/20.
 */

val EXP_CONNECTING = 0                 //0,0  before SOCKET on
val EXP_COMPLETE = 1                   //1,0  socket_on 主板 SOCKET建立OK
val EXP_DEVICE_INACTIVE = 2            //2,0  UIM FAIL 14 未激活, 未绑定
val EXP_ACCOUNT_INSUFFICIENT = 3       //3,0  余额告警或者余额不足
val EXP_LIMIT_SPEED = 4                //4,0  S2C_CMD_74
val EXP_DEVICE_ABNORMAL = 5            //5,0  设备异常需要收回
val EXP_NOSUITABLE_NETWORK = 6         //6,0  无合适的网络覆盖
val EXP_LOW_POWER = 7                  //7,0  低电
val EXP_LOGIN_ABNORMAL = 8             //8,0  设备登录异常
val EXP_NETWORK_BUSY = 9               //9,0  网络繁忙
val EXP_SYSTEM_BUSY = 10               //10,0  系统繁忙
val EXP_DEVICE_UPGRADE = 11            //11,0  设备升级中
val EXP_DEVICE_UPGRADE_OK = 12         //12,0  设备升级完成
val EXP_SERVICE_NOTICE_ONLYONCE = 13   //13,0  服务器通知，将服务器下发的通知内容字符串填充到uc_portal_status.more_info，portal state_type=13，more_info_flag=1，其他为portal状态上一次保存的值
val EXP_SIM_SELECT = 14                //14,0  SIM卡选择
val EXP_RSIM_STATE = 15                //15,0  实体卡状态
val EXP_INITIAL_ACCOUNT = 16           //16,0  初始100M账户，需要用户注册
val EXP_SLEEP_WAKE_UP = 17             //17,0  卡休眠唤醒
val EXP_NONE = 18                      //X,X   -- no case


/**
 * 上面portal是EXP_RSIM_STATE 填下面的值
 */
val UC_RSIM_EXIST = 13000                        //实体卡检测到
val UC_RSIM_START = 13001                        //实体卡启用
val UC_RSIM_READY = 13002                        //实体卡重启ok ready
val UC_RSIM_REG_REJECT = 13003                   //注册ATTACH被拒绝
val UC_RSIM_REG_BAD = 13004                      //实体卡注册失败
val UC_RSIM_REG_OK = 13005                       //实体卡注册成功
val UC_RSIM_PIN = 13006                          //实体卡需要输入PIN
val UC_RSIM_PUK = 13007                          //实体卡需要输入PUK
val UC_RSIM_CONNECT_BAD = 13008                  //实体卡拨号失败
val UC_RSIM_CONNECT_OK = 13009                  //实体卡拨号失败

data class WebPortalInfo(var portal:Int, var errcode:Int, var runStep:Int, var infoFlag:Int, var info:String)
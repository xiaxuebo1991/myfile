//package com.ucloudlink.refact.remote
//
///**
// * 服务器错误吗
// * Created by yongbin.qin on 2017/5/9.
// */
//object ServerErrCode{
//
//    val RESULT_OK = 100  //服务器返回正确
//
//    val ERR_USER_NOT_EXIST = 1160001 //用户不存在
//
//    val ERR_PASSWD = 1160002 //密码错误
//
//    val ERR_IMEI_OR_NAME_EMPTY = 1160003 //IMEI不能为空/用户名为空
//
//    val ERR_IMEI_NOT_BIND = 1160004 //不存在IMEI的绑定关系
//
//    val ERR_NAME_OR_PASSWD_EMPTY = 1160005 //用户账号或密码为空，且绑定状态不为绑定中
//
//    val ERR_NOT_ACTIVATE_ACCOUNT = 1160006 //免费试用过期，仍未激活用户
//
//    val ERR_EXIST_P_DAY_P_MONTH = 1160008 //用户同时存在包天包月
//
//    val ERR_P_MONTH_BEYONG_MAX = 1160009 //包月超过最大用户数
//
//    val ERR_BALANCE_NOT_ENOUGH_P_DAY = 1160010 //帐户余额不足包天费
//
//    val ERR_BEYONG_FAVORED_NATION = 1160011 //不在优惠国家范围内
//
//    val ERR_GET_USER_FAILD = 1160012 //获取用户失败
//
//    val ERR_IMEI_NOT_EXIST = 1160013 //IMEI不存在
//
//    val ERR_IMEI_BE_DELETED = 11600014 //IMEI已被删除
//
//    val ERR_TOLEN_EXCE = 1160015 //获取AccessToken异常
//
//    val ERR_ACCOUNT_DATA_EXCE = 1160016 //帐户数据异常
//
//    val ERR_BSS_UNKNOWN = 1160017 //未知异常
//
//    val ERR_ACCTOUNT_STOP_USE = 1160018 //用户被停用
//
//    val ERR_NOT_CAR  = 3040000   //没有分到合适的卡
//
//    val ERR_GET_INFO_FAILD  = 3040001  //从BSS获取用户信息失败
//
//    val ERR_NET_SET_NULL = 3040002 //终端上报的网络，对应的网络集为空
//
//    val ERR_NET_COUNTRY_NULL = 3040003 //终端上报的网络，对应国家为空
//
//    val ERR_NOT_PRODUCT_BY_ID = 3040004 //根据产品ID获取不到产品
//
//    val ERR_NOT_STRATEGY_BY_ID = 3040005 //根据策略Id找不到策略
//
//    val ERR_NOT_CAR_POOL_BY_ID = 3040006 //根据策略ID找不到卡池
//
//    val ERR_NOT_CAR_BY_IMSI = 3040007 //根据imsi找不到卡对象
//
//    val ERR_NOT_USER_CODE_BY_USERCODE = 3040008 //根据userCode 找不到用户卡在线对象   客户端需要重新登录
//
//    val ERR_CAR_NUM_NOT_EXIST =  3040009 //卡在线对象,卡号不存在
//
//    val ERR_NET_SYS = 3040010 //网络制式错误
//
//    val ERR_UPDATE_USER_ONLINE_STATE = 3040011 //更新用户在线状态错误
//
//    val ERR_DISPATCH_CAR_LOG =  3040012 //添加分卡日志错误
//
//    val ERR_NOT_AVAILABLE_NET =  3040013 //  当前设备所在位置网络信号太差， 没有可用网络
//
//    val ERR_VIP_NOT_CAR =3040015 //VIP没有抢到合适的卡    客户端需要重新发起分卡请求
//
//    val ERR_NOT_GROUP_BY_IMSI = 3040100 //  根据Imsi找不到 Group对象
//
//    val ERR_RELEASE_CAR_FAILD =  3040200  //释放卡失败
//
//    val ERR_PRE_IMSI_ERR = 3040201  //上次使用的Imsi号错误
//
//    val ERR_VSIM_INFO_OTHER = 3040202 //getVsimInfo其它错误
//
//    val ERR_PUBLIC_VERSION_NULL = 3040300 //公共文件版本号设置为空  不用重新发送，直接调用换卡
//
//    val ERR_DIFF_VERSION_NULL = 3040301 //差异文件版本号为空  不用重新发送，直接调用换卡
//
//    val ERR_VSIM_INFO_PARAM = 3040302 //getVsimInfo参数错误  不用重新发送，直接调用换卡
//
//    val ERR_BIN_FILE_OTHER = 3040303 //getBinFile其它错误
//
//    val ERR_BIN_FILEO_PARAM = 3040400 //getBinFile参数错误
//
//    val ERR_APDU_GET_FAILD = 3040401 //apdu获取失败
//
//    val ERR_NOT_BAM_BY_IMSI = 2160002 //根据imsi找不到对应的BAM
//
//    val ERR_NOT_FOUND_BAM_CHANNEL = 2160002 //不能找到对应BAM的通道
//
//    val ERR_INVOKE_BSS_FAILD = 2161111  //BSS调用失败
//
//    val ERR_INVOKE_CSS_FAILD = 2162000  //CSS调用失败
//
//    val ERR_INVOKE_OSS_FAILD = 2163000  //OSS调用失败
//
//    val ERR_INVOKE_BAM_FAILD = 2164000  // BAM调用失败
//
//    val ERR_INVALID_SESSIONID = 2160001 //无效的sessionid
//
//    val ERR_APDU_HANDLE_EXCE = 2160002 //APDU处理异常
//
//    val ERR_INVOKE_DUBBO_EXCE = 2160003 //调用dubbo服务异常
//
//    val ERR_INVOKE_SERVER_EXCE = 2160004 //调用系统服务异常
//
//    val ERR_SID_EMPTY = 2160006 //终端登录请求sid is empty!
//
//    val ERR_IMEI_EMPTY = 2160007 //终端登录请求imei is empty!
//
//    val ERR_LOGIN_FAILD = 2160008 //终端登录失败,登录返回结果为null
//
//    val ERR_QUERY_SERVER_FAILD = 2160009 //查询服务列表失败
//
//    val ERR_DISPATCH_CAR_FAILD = 2160010 //用户分卡失败
//
//    val ERR_INVOKE_LOGIN_INTERFACE = 2160011 //调用auth服务登录接口出错
//
//    val ERR_LACK_BALANCE = 2160012 //用户余额不足,拒绝登录
//
//    val ERR_ASS_UNKNOWN = 2169999 //未知错误码
//
//}

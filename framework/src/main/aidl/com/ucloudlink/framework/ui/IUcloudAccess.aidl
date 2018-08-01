package com.ucloudlink.framework.ui;
import com.ucloudlink.framework.ui.IUcloudAccessCallback;
import com.ucloudlink.framework.ui.IUcloudDownloadSoftsimCallback;
import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.framework.ui.ExceptionValue;
import com.ucloudlink.framework.ui.PlmnInfo;

interface IUcloudAccess {

   /**启动云卡服务*/
   void startCloudsimService(String userName,String password, String packageId);

   /**停止云卡服务*/
   void stopCloudsimService();

   /**注册回调*/
   void registerCallback(IUcloudAccessCallback callback);

   /**解除注册回调*/
   void unRegisterCallback(IUcloudAccessCallback callback);

   /**获取云卡开启进度*/
   int getProcessState();

   /**设置启动模式、种子卡卡槽*/
   void setParam(int apdu);

   /**增加配置
    * 支持配置 ：
    * cmd                 args     description
    * BOOT_AUTO_RECOVER   ON|OFF   配置是否开机自动恢复云卡
    * SEED_TYPE          PSIM|SSIM 配置种子卡模式，是物理卡模式（PSIM）还是软卡模式（SSIM）
    * CLOUDSIM_SLOT      0|1       配置云卡的卡槽，在0卡槽还是1卡槽，另一个自动配置为种子卡卡槽
    * AUTO_WHEN_REBOOT   ON|OFF    配置是否开机自动启动服务
    * USERINFO           "xx;xx"        用户名和密码，用分号隔开，左边用户名，右边密码，都不能为空
    * SERVER_TYPE        0|1       商用服务器（0）  测试服务器（1）
    * CHANGE_PACKAGE      "packageId"  更换套餐
    * PHY_ROAM_ENABLE    true|false
    * QXDM_ENABLE        true|false
    * SEED_NETWORK_BAND_WIDTH               args   JsonArray配置放通那些ip的带宽
    * SEED_NETWORK_LIMIT_BY_UID             args   JsonArray配置通过uid放通种子卡网络
    * SEED_NETWORK_LIMIT_BY_UID_AND_IP      args   JsonArray配置通过uid+ip(或dns)放通种子卡网络
    * LOCAL_SEEDSIM_DEPTH_OPT               args   true|false 种子卡深度优化
    * */
    int addConfig(String cmd,String args);

    /**查询配置
    * 参考“增加配置”
    * ROM_SUPPORT_SEED_NETWORK_LIMIT_BY_UID_AND_IP   查询ROM是否支持SEED_NETWORK_LIMIT_BY_UID_AND_IP
    * IS_SUPPORT_DEVICE  true|false   查询本设备是否支持云卡  
    * */
    String queryConfig(String cmd);

   /**查询当前是否在异常状态*/
   boolean isInException();

   /**获取当前设备中的异常信息集合*/
   ExceptionValue getExceptionState();
   
   /**查询当前是否在恢复状态*/
   boolean isInRecovery();
   
    /**获取种子卡plmn*/
   PlmnInfo getSeedSimPlmn();

   /**下载软卡请求*/
   void downloadSoftsim(String username, String password, in List<FlowOrder> orders);

   /**注册下载软卡回调*/
   void registerDownloadSoftsimCallback(IUcloudDownloadSoftsimCallback callback);
   
   void unregisterDownloadSoftsimCb(IUcloudDownloadSoftsimCallback callback);

   /**启动种子软卡*/
   int startSeedSoftsim(String username, String packageName, int timeout);

   /**停止种子软卡*/
   int stopSeedSoftsim();

   /** 激活套餐 */
   int activateUserOrder(String username, String order, long activateTime, long deadlineTime);

   /**获取service sid 和 版本号
   * 格式: sid&ver
   * */
   String getServiceSidAndVersion();

   /** 获取状态
    *  1 ---> 软卡更新状态， 返回true or false
    *  2 ---> 限速状态      返回 true or false
    *  3 ---> 限速的值      返回 "up:xxx,down:xxx"
    *  4 ---> 显示wifi的状态  返回 true  or false
   **/
   String getStateStatus(int module);
}

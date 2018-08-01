package com.ucloudlink.refact.business.flow;

import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;

/**
 * Created by jianguo.he on 2017/8/17.
 * 每次统计作为一条记录，不累加流量值
 */

public class SCFlowController {

    private static SCFlowController instance;

    /** 云卡上网后, 种子卡流量上传间隔最小时间 */
    public static final long MIN_UPLOAD_TIMEMILLIS = 5 * 60 * 1000;// xx minute
    /** 云卡上网后,种子卡流量上传延迟执行时间 */
    public static final long MIN_UPLOAD_DELAYMILLIS = 1 * 60 * 1000;
    /** 云卡上网后, 种子卡流量上传触发时间间隔*/
    public static final long MIN_UPLOAD_CHECK_TIMEMILLIS = 30 * 60 * 1000;// xx minute


    /** 测试标记, 是否开启种子卡流量相关功能 */
    private boolean enableSC = true;

    private ISeedFlowCtrl mISeedFlowCtrl;

    private SCFlowController(){
        mISeedFlowCtrl = ServiceManager.systemApi.getISeedFlowCtrl();
    }

    public static SCFlowController getInstance(){
        if(instance==null){
            synchronized (SCFlowController.class){
                if(instance==null){
                    instance = new SCFlowController();
                }
            }
        }
        return  instance;
    }

    public void checkIfNameChange(String curSeedIfName){
        mISeedFlowCtrl.checkWhenIfNameChange(curSeedIfName);
    }

    public void start(String curSeedIfName, String username, String imsi, String mcc, String cardType){
        if(!enableSC)
            return;
        mISeedFlowCtrl.start(curSeedIfName, username, imsi, mcc, cardType);

    }

    public void stop(){
        if(!enableSC)
            return;
        mISeedFlowCtrl.stop();
    }

    public void uploadFlow(boolean enfore){
        mISeedFlowCtrl.uploadFlow(enfore);
    }

    public static String getBindKey(String username, String imsi, String type){
        if(!TextUtils.isEmpty(username) && !TextUtils.isEmpty(imsi) && !TextUtils.isEmpty(type)){
            return type + imsi + username;
        }
        return null;
    }

}

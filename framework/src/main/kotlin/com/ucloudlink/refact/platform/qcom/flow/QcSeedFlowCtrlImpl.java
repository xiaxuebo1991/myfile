package com.ucloudlink.refact.platform.qcom.flow;

import android.os.SystemClock;
import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.IFlow;
import com.ucloudlink.refact.business.flow.ISeedFlowCtrl;
import com.ucloudlink.refact.business.flow.SCFlowController;
import com.ucloudlink.refact.business.flow.SCFlowSateInfo;
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo;
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.utils.DateUtil;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

/**
 * Created by jianguo.he on 2018/2/7.
 */

public class QcSeedFlowCtrlImpl implements ISeedFlowCtrl {

    private SCFlowSateInfo mSCFlowSateInfo;

    /** 种子卡流量上次上传时间 */
    private long lastUploadTimeMillis;

    private IFlow mIFlow;

    public QcSeedFlowCtrlImpl(){
        mIFlow = ServiceManager.systemApi.getSeedIFlow();
    }

    @Override
    public void start(@Nullable String curSeedIfName, @Nullable String username, @Nullable String imsi, @Nullable String mcc, @Nullable String cardType) {
        JLog.logi("SCFlowLog start++   : curSeedIfName = "+(curSeedIfName==null?"null":curSeedIfName)
        + ", username = " + (username==null?"null":username) + ", imsi = " + (imsi==null?"null":imsi)
                + ", mcc = " + (mcc==null?"null":mcc) + ", cardType = " + (cardType==null?"null":cardType));
        if(TextUtils.isEmpty(curSeedIfName)){
            JLog.logi("SCFlowLog start++ curSeedIfName = null");
            return;
        }
        String paramBindKey = SCFlowController.getBindKey(username, imsi, cardType);
        if(TextUtils.isEmpty(paramBindKey)){
            JLog.logi("SCFlowLog start++ paramBindKey = null");
            return;
        }
        JLog.logi("SCFlowLog start++ paramBindKey="+paramBindKey+", mSCFlowSateInfo==null ? "+(mSCFlowSateInfo==null));
        if(mSCFlowSateInfo==null){
            mSCFlowSateInfo = new SCFlowSateInfo(curSeedIfName, SysUtils.getUServiceUid(), username, imsi, mcc, cardType, mIFlow);
            mSCFlowSateInfo.start();
        } else {
            JLog.logi("SCFlowLog start++  "+(mSCFlowSateInfo.toString()));
            String curBindKey = SCFlowController.getBindKey(mSCFlowSateInfo.username, mSCFlowSateInfo.imsi, mSCFlowSateInfo.cardType);
            boolean compareResult = paramBindKey.equals(curBindKey);
            if(!compareResult){
                mSCFlowSateInfo.stop();
            }
            if(!compareResult || (compareResult && mSCFlowSateInfo.beginTime != mSCFlowSateInfo.endTime)){
                if(!mSCFlowSateInfo.isZeroIncr()){
                    SoftsimFlowStateInfo mSoftsimFlowStateInfo = mSCFlowSateInfo.as();
                    ServiceManager.accessEntry.softsimEntry.addNewSoftsimStateInfo(mSoftsimFlowStateInfo);
                    JLog.logi("SCFlowLog add to DB -: "+mSoftsimFlowStateInfo.toString());
                }

                mSCFlowSateInfo = new SCFlowSateInfo(curSeedIfName, SysUtils.getUServiceUid(), username, imsi, mcc, cardType, mIFlow);
                mSCFlowSateInfo.start();

            } else {// 继续统计

            }
        }
    }

    @Override
    public void stop() {
        if(mSCFlowSateInfo==null){
            JLog.logi("SCFlowLog stop++ flow Info = null");
            return;
        }
        JLog.loge("SCFlowLog stop++ flow Info != null");
        mSCFlowSateInfo.stop();

        if(!mSCFlowSateInfo.isZeroIncr()){
            SoftsimFlowStateInfo mSoftsimFlowStateInfo = mSCFlowSateInfo.as();
            ServiceManager.accessEntry.softsimEntry.addNewSoftsimStateInfo(mSoftsimFlowStateInfo);
            JLog.logi("SCFlowLog add to DB -: "+mSoftsimFlowStateInfo.toString());
        }
        mSCFlowSateInfo = null;
    }

    @Override
    public void uploadFlow(boolean enfore) {
        long currentTimeMillis = SystemClock.elapsedRealtime();
        JLog.logi("SCFlowLog uploadFlow - enfore = "+enfore+", distance time "+((currentTimeMillis - lastUploadTimeMillis)/1000)+"s" +
                ", isOutOfMinTimeMillis = "+(currentTimeMillis - lastUploadTimeMillis > SCFlowController.MIN_UPLOAD_TIMEMILLIS) +
                ", lastUploadTimeMillis = "+lastUploadTimeMillis+"("+ DateUtil.format_YYYY_MM_DD_HH_SS_SSS(lastUploadTimeMillis)+")");
        if(enfore || currentTimeMillis - lastUploadTimeMillis > SCFlowController.MIN_UPLOAD_TIMEMILLIS){
            ServiceManager.accessEntry.softsimEntry.startUploadSoftsimFlow(SCFlowController.MIN_UPLOAD_DELAYMILLIS);
            lastUploadTimeMillis = currentTimeMillis;
        }
    }

    @Override
    public void checkWhenIfNameChange(String ifName) {
        if(TextUtils.isEmpty(ifName)){
            QcSeedFlowCtrlImpl.this.stop();
        } else {
            if(mSCFlowSateInfo == null){

            } else {
                if(TextUtils.isEmpty(mSCFlowSateInfo.curSeedIfName)){
                    // 错误数据
                } else if(ifName.equals(mSCFlowSateInfo.curSeedIfName)){
                    // 继续统计
                } else {
                    String username = mSCFlowSateInfo.username;
                    String imsi = mSCFlowSateInfo.imsi;
                    String mcc = mSCFlowSateInfo.mcc;
                    String cardType = mSCFlowSateInfo.cardType;
                    QcSeedFlowCtrlImpl.this.stop();// stop 会导致mSCFlowSateInfo=null
                    start(ifName, username, imsi, mcc, cardType);
                }
            }
        }
    }
}

package com.ucloudlink.refact.business.flow;

import android.os.Parcel;
import android.os.Parcelable;

import com.ucloudlink.framework.protocol.protobuf.SeedCardType;
import com.ucloudlink.refact.utils.DateUtil;
import com.ucloudlink.refact.utils.JLog;

import java.io.Serializable;

/**
 * Created by jianguo.he on 2017/8/5.
 */

public class SCFlowSateInfo implements Parcelable, Serializable {

    /**
     * S1 上：
     * Note: 定义没错，云卡流量的用户流量跟种子卡的用户流量定义不一样
     * 种子卡流量：
     * 用户流量：应用本身使用的流量 UService
     * 系统流量：其他App使用的流量
     * 云卡流量：
     * 用户流量：其他App使用的流量
     * 系统流量：UService使用的流量
     *
     * U3C上：
     * 种子卡系统流量 = U3C本机流量
     * 种子卡用户流量 = U3C wifi client流量
     * */

    private static final long serialVersionUID = -5610323763078460438L;

    public String curSeedIfName;         // 需要统计的网口名称
    public int uid = -1;

                                           // 时间段内 是指 endTime - beginTime
    public String username;              // 当前登录的用户名
    public String imsi;                  // 标识SIM卡唯一性
    public long beginTime;              // 字节数据开始计时时间
    public long endTime;                // 字节数据结束计时时间
    public String formatBeginTime;      // 无意义, 只是格式化beginTime, 便于观察log
    public String formatEndTime;        // 无意义, 只是格式化endTime, 便于观察log
    public String mcc;                   // 国家码
    public long totalTxIncr;            // 时间段内总发送字节数据
    public long totalRxIncr;            // 时间段内总接收字节数据
    public long totalUidTxIncr;         // 时间段内uid总发送字节数据
    public long totalUidRxIncr;         // 时间段内uid总接收字节数据
    public long totalOtherUidTxIncr;    // 时间段内其他uid总发送字节数据
    public long totalOtherUidRxIncr;    // 时间段内其他uid总接收字节数据
    public boolean isSoftsim;            // 是否软卡

    // beginTime
    public String cardType;                // 卡类型
    public long beginTimeTotalTx;        // 字节数据开始时间时的总发送字节数据
    public long beginTimeTotalRx;        // 字节数据开始时间时的总接收字节数据
    // 开始时间点对应的总的流量值， 卡可能不一样，imsi不一样
    public long beginTimeTotalMobileTx; // 字节数据开始时间时的流量总接收字节数据
    public long beginTimeTotalMobileRx; // 字节数据开始时间时的流量总发送字节数据

    public long beginTimeTotalUidTx;    // 字节数据开始时间时的uid总接收字节数据
    public long beginTimeTotalUidRx;    // 字节数据开始时间时的uid总发送字节数据
    public long beginTimeTotalOtherUidTx;    // 字节数据开始时间时的其他uid总接收字节数据
    public long beginTimeTotalOtherUidRx;    // 字节数据开始时间时的其他uid总发送字节数据

    // endTime
    public long endTimeTotalTx;         // 字节数据结束时间时的总发送字节数据
    public long endTimeTotalRx;         // 字节数据结束时间时的总接收字节数据
    // 开始时间点对于的总的流量值， 卡可能不一样，imsi不一样
    public long endTimeTotalMobileTx;  // 字节数据结束时间时的流量总接收字节数据
    public long endTimeTotalMobileRx;  // 字节数据结束时间时的流量总发送字节数据

    public long endTimeTotalUidTx;     // 字节数据结束时间时的uid总接收字节数据
    public long endTimeTotalUidRx;     // 字节数据结束时间时的uid总发送字节数据
    public long endTimeTotalOtherUidTx;    // 字节数据结束时间时的其他uid总接收字节数据
    public long endTimeTotalOtherUidRx;    // 字节数据结束时间时的其他uid总发送字节数据

    private IFlow mIFlow;


    public SCFlowSateInfo(IFlow mIFlow){
        //mIFlow = ServiceManager.systemApi.getSeedIFlow();
        //mIFlow = new FlowImpl();
        this.mIFlow = mIFlow;
    }

    public SCFlowSateInfo(String curSeedIfName, int uid, String username, String imsi, String mcc, String cardType, IFlow mIFlow){

        //mIFlow = ServiceManager.systemApi.getSeedIFlow();
        //mIFlow = new FlowImpl();
        this.mIFlow = mIFlow;

        this.curSeedIfName = curSeedIfName;
        this.uid            = uid;
        this.username      = username;
        this.imsi           = imsi;
        this.mcc            = mcc;
        this.cardType      = cardType;
        this.isSoftsim     = this.cardType!=null && this.cardType.equals(SeedCardType.SOFTSIM.toString());
    }

    public void append(SCFlowSateInfo info){
        this.endTime = info.endTime;
        formatEndTime = DateUtil.format_YYYY_MM_DD_HH_SS_SSS(this.endTime);
        this.totalTxIncr += info.totalTxIncr;
        this.totalRxIncr += info.totalRxIncr;
        this.totalUidTxIncr += info.totalUidTxIncr;
        this.totalUidRxIncr += info.totalUidRxIncr;
        this.totalOtherUidTxIncr += info.totalOtherUidTxIncr;
        this.totalOtherUidRxIncr += info.totalOtherUidRxIncr;
    }


    public void start(){
        // beginTime
        beginTime = System.currentTimeMillis();
        formatBeginTime = DateUtil.format_YYYY_MM_DD_HH_SS_SSS(beginTime);
        //beginTimeTotalTx = mIFlow.getTotalTxBytes(curSeedIfName);// 总的流量需要后读，不然可能会存在漏读数据@ROM
        //beginTimeTotalRx = mIFlow.getTotalRxBytes(curSeedIfName);
        beginTimeTotalMobileTx = mIFlow.getMobileTxBytes(curSeedIfName);
        beginTimeTotalMobileRx = mIFlow.getMobileRxBytes(curSeedIfName);

        // android 7.0 可能会返回-1, 应该是包含了wifi跟mobile的流量
        beginTimeTotalUidTx = mIFlow.getUidTxBytes(curSeedIfName, uid); //mIFlow.getIfNameTxBytes(curSeedIfName);//
        beginTimeTotalUidRx = mIFlow.getUidRxBytes(curSeedIfName, uid);//mIFlow.getIfNameRxBytes(curSeedIfName);//

        beginTimeTotalTx = mIFlow.getTotalTxBytes(curSeedIfName);
        beginTimeTotalRx = mIFlow.getTotalRxBytes(curSeedIfName);

        beginTimeTotalOtherUidTx = beginTimeTotalTx - beginTimeTotalUidTx;
        beginTimeTotalOtherUidRx = beginTimeTotalRx - beginTimeTotalUidRx;

        // endTime
        endTime = beginTime;
        formatEndTime = DateUtil.format_YYYY_MM_DD_HH_SS_SSS(endTime);
        endTimeTotalTx = beginTimeTotalTx;
        endTimeTotalRx = beginTimeTotalRx;
        endTimeTotalMobileTx = beginTimeTotalMobileTx;
        endTimeTotalMobileRx = beginTimeTotalMobileRx;

        endTimeTotalUidTx = beginTimeTotalUidTx;
        endTimeTotalUidRx = beginTimeTotalUidRx;
        endTimeTotalOtherUidTx = endTimeTotalTx - endTimeTotalUidTx;
        endTimeTotalOtherUidRx = endTimeTotalRx - endTimeTotalUidRx;

        JLog.logi("SCFlowLog start -: "+toString());

    }

    public void stop(){
        endTime = System.currentTimeMillis();
        formatEndTime = DateUtil.format_YYYY_MM_DD_HH_SS_SSS(endTime);
//        endTimeTotalTx = mIFlow.getTotalTxBytes(curSeedIfName);// 总的流量需要后读，不然可能会存在漏读数据@ROM
//        endTimeTotalRx = mIFlow.getTotalRxBytes(curSeedIfName);
        endTimeTotalMobileTx = mIFlow.getMobileTxBytes(curSeedIfName);
        endTimeTotalMobileRx = mIFlow.getMobileRxBytes(curSeedIfName);

        endTimeTotalUidTx = mIFlow.getUidTxBytes(curSeedIfName, uid);//mIFlow.getIfNameTxBytes(curSeedIfName);//
        endTimeTotalUidRx = mIFlow.getUidRxBytes(curSeedIfName, uid);//mIFlow.getIfNameRxBytes(curSeedIfName);//

        endTimeTotalTx = mIFlow.getTotalTxBytes(curSeedIfName);
        endTimeTotalRx = mIFlow.getTotalRxBytes(curSeedIfName);

        endTimeTotalOtherUidTx = endTimeTotalTx - endTimeTotalUidTx;
        endTimeTotalOtherUidRx = endTimeTotalRx - endTimeTotalUidRx;

        totalTxIncr = endTimeTotalTx - beginTimeTotalTx;
        totalRxIncr = endTimeTotalRx - beginTimeTotalRx;
        totalUidTxIncr = endTimeTotalUidTx - beginTimeTotalUidTx;
        totalUidRxIncr = endTimeTotalUidRx - beginTimeTotalUidRx;
        totalOtherUidTxIncr = endTimeTotalOtherUidTx - beginTimeTotalOtherUidTx;
        totalOtherUidRxIncr = endTimeTotalOtherUidRx - beginTimeTotalOtherUidRx;

        JLog.logi("SCFlowLog stop -: "+toString());
    }

    public boolean isZeroIncr(){
        return !(totalTxIncr > 0 || totalRxIncr > 0 );
    }

    public SoftsimFlowStateInfo as(){
        return new SoftsimFlowStateInfo(
                username,
                imsi,
                beginTime,
                endTime,
                mcc,
                totalTxIncr,
                totalRxIncr,
                totalUidTxIncr,
                totalUidRxIncr,
                totalOtherUidTxIncr,
                totalOtherUidRxIncr,
                isSoftsim
        );
    }

    public boolean isFullBytes(long value){
        return totalTxIncr + totalRxIncr > value;
    }

    public boolean isFullTimeMillis(long value){
        return System.currentTimeMillis() - endTime > value;
    }

    @Override
    public String toString() {
        return "SCFlowSateInfo{" +
                "curSeedIfName='" + curSeedIfName + '\'' +
                ", uid='" + uid + '\'' +
                ", username='" + username + '\'' +
                ", imsi='" + imsi + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", formatBeginTime='" + formatBeginTime + '\'' +
                ", formatEndTime='" + formatEndTime + '\'' +
                ", mcc='" + mcc + '\'' +
                ", totalTxIncr=" + totalTxIncr +
                ", totalRxIncr=" + totalRxIncr +
                ", totalUidTxIncr=" + totalUidTxIncr +
                ", totalUidRxIncr=" + totalUidRxIncr +
                ", totalOtherUidTxIncr=" + totalOtherUidTxIncr +
                ", totalOtherUidRxIncr=" + totalOtherUidRxIncr +
                ", isSoftsim=" + isSoftsim +
                ", cardType='" + cardType + '\'' +
                ", beginTimeTotalTx=" + beginTimeTotalTx +
                ", beginTimeTotalRx=" + beginTimeTotalRx +
                ", beginTimeTotalMobileTx=" + beginTimeTotalMobileTx +
                ", beginTimeTotalMobileRx=" + beginTimeTotalMobileRx +
                ", beginTimeTotalUidTx=" + beginTimeTotalUidTx +
                ", beginTimeTotalUidRx=" + beginTimeTotalUidRx +
                ", beginTimeTotalOtherUidTx=" + beginTimeTotalOtherUidTx +
                ", beginTimeTotalOtherUidRx=" + beginTimeTotalOtherUidRx +
                ", endTimeTotalTx=" + endTimeTotalTx +
                ", endTimeTotalRx=" + endTimeTotalRx +
                ", endTimeTotalMobileTx=" + endTimeTotalMobileTx +
                ", endTimeTotalMobileRx=" + endTimeTotalMobileRx +
                ", endTimeTotalUidTx=" + endTimeTotalUidTx +
                ", endTimeTotalUidRx=" + endTimeTotalUidRx +
                ", endTimeTotalOtherUidTx=" + endTimeTotalOtherUidTx +
                ", endTimeTotalOtherUidRx=" + endTimeTotalOtherUidRx +
                '}';
    }

    protected SCFlowSateInfo(Parcel in) {
        curSeedIfName = in.readString();
        uid = in.readInt();
        username = in.readString();
        imsi = in.readString();
        beginTime = in.readLong();
        endTime = in.readLong();
        formatBeginTime = DateUtil.format_YYYY_MM_DD_HH_SS_SSS(beginTime);
        formatEndTime = DateUtil.format_YYYY_MM_DD_HH_SS_SSS(endTime);
        mcc = in.readString();
        totalTxIncr = in.readLong();
        totalRxIncr = in.readLong();
        totalUidTxIncr = in.readLong();
        totalUidRxIncr = in.readLong();
        totalOtherUidTxIncr = in.readLong();
        totalOtherUidRxIncr = in.readLong();
        isSoftsim = in.readByte() != 0;
        cardType = in.readString();
        beginTimeTotalTx = in.readLong();
        beginTimeTotalRx = in.readLong();
        beginTimeTotalMobileTx = in.readLong();
        beginTimeTotalMobileRx = in.readLong();
        beginTimeTotalUidTx = in.readLong();
        beginTimeTotalUidRx = in.readLong();
        beginTimeTotalOtherUidTx = in.readLong();
        beginTimeTotalOtherUidRx = in.readLong();
        endTimeTotalTx = in.readLong();
        endTimeTotalRx = in.readLong();
        endTimeTotalMobileTx = in.readLong();
        endTimeTotalMobileRx = in.readLong();
        endTimeTotalUidTx = in.readLong();
        endTimeTotalUidRx = in.readLong();
        endTimeTotalOtherUidTx = in.readLong();
        endTimeTotalOtherUidRx = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(curSeedIfName);
        dest.writeInt(uid);
        dest.writeString(username);
        dest.writeString(imsi);
        dest.writeLong(beginTime);
        dest.writeLong(endTime);
        dest.writeString(mcc);
        dest.writeLong(totalTxIncr);
        dest.writeLong(totalRxIncr);
        dest.writeLong(totalUidTxIncr);
        dest.writeLong(totalUidRxIncr);
        dest.writeLong(totalOtherUidTxIncr);
        dest.writeLong(totalOtherUidRxIncr);
        dest.writeByte((byte) (isSoftsim ? 1 : 0));
        dest.writeString(cardType);
        dest.writeLong(beginTimeTotalTx);
        dest.writeLong(beginTimeTotalRx);
        dest.writeLong(beginTimeTotalMobileTx);
        dest.writeLong(beginTimeTotalMobileRx);
        dest.writeLong(beginTimeTotalUidTx);
        dest.writeLong(beginTimeTotalUidRx);
        dest.writeLong(beginTimeTotalOtherUidTx);
        dest.writeLong(beginTimeTotalOtherUidRx);
        dest.writeLong(endTimeTotalTx);
        dest.writeLong(endTimeTotalRx);
        dest.writeLong(endTimeTotalMobileTx);
        dest.writeLong(endTimeTotalMobileRx);
        dest.writeLong(endTimeTotalUidTx);
        dest.writeLong(endTimeTotalUidRx);
        dest.writeLong(endTimeTotalOtherUidTx);
        dest.writeLong(endTimeTotalOtherUidRx);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SCFlowSateInfo> CREATOR = new Creator<SCFlowSateInfo>() {
        @Override
        public SCFlowSateInfo createFromParcel(Parcel in) {
            return new SCFlowSateInfo(in);
        }

        @Override
        public SCFlowSateInfo[] newArray(int size) {
            return new SCFlowSateInfo[size];
        }
    };



}

package com.ucloudlink.framework.ui;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * Created by haiping.liu on 2016/12/14.
 * 流量订单类
 */

public class FlowOrder implements Parcelable {

    private String orderId;

    //套餐使用国家
    private int[] mccLists; // TODO: 2017/5/18 看看是否需要

    //创建时间  单位 ms
    private long createTime;  // unit:ms

    //激活期限  单位：天
    private int activatePeriod;

    // 种子卡策略 1:纯软卡  2：纯英卡  3：软卡优先   4：硬卡优先
    private int seedSimPolicy;

    public FlowOrder(String orderId, int[] mccLists, long createTime, int activatePeriod, int seedSimPolicy) {
        this.orderId = orderId;
        this.mccLists = mccLists;
        this.createTime = createTime;
        this.activatePeriod = activatePeriod;
        this.seedSimPolicy = seedSimPolicy;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public int[] getMccLists() {
        return mccLists;
    }

    public void setMccLists(int[] mccLists) {
        this.mccLists = mccLists;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getActivatePeriod() {
        return activatePeriod;
    }

    public void setActivatePeriod(int activatePeriod) {
        this.activatePeriod = activatePeriod;
    }

    public int getSeedSimPolicy() {
        return seedSimPolicy;
    }

    public void setSeedSimPolicy(int seedSimPolicy) {
        this.seedSimPolicy = seedSimPolicy;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(orderId);

        if (mccLists == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(mccLists.length);
        }
        //如果数组为空，就可以不写
        if (mccLists != null) {
            dest.writeIntArray(mccLists);
        }

        dest.writeLong(createTime);
        dest.writeInt(activatePeriod);
        dest.writeInt(seedSimPolicy);
    }

    public static final Creator<FlowOrder> CREATOR = new Creator<FlowOrder>() {
        @Override
        public FlowOrder createFromParcel(Parcel in) {
            return new FlowOrder(in);
        }

        @Override
        public FlowOrder[] newArray(int size) {
            return new FlowOrder[size];
        }
    };

    protected FlowOrder(Parcel in) {
        this.orderId = in.readString();

        //开始读数组的长度
        int length = in.readInt();
        int[] msg = null;
        //如果数组长度大于0，那么就读数组， 所有数组的操作都可以这样。
        if (length > 0) {
            msg = new int[length];
            in.readIntArray(msg);
        }

        this.mccLists = msg;
        this.createTime = in.readLong();
        this.activatePeriod = in.readInt();
        this.seedSimPolicy = in.readInt();
    }

    @Override
    public String toString() {
        return "FlowOrder{" +
                "orderId='" + orderId + '\'' +
                ", mccLists=" + Arrays.toString(mccLists) +
                ", createTime=" + createTime +
                ", activatePeriod=" + activatePeriod +
                ", seedSimPolicy=" + seedSimPolicy +
                '}';
    }
}

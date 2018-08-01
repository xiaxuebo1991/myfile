package com.ucloudlink.refact.business.softsim.struct;

import com.ucloudlink.refact.ServiceManager;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by shiqianhua on 2016/12/6.
 */

public class OrderInfo {
    private String orderId;
    private ArrayList<String> sofsimList;
    private long createTime; // ms
    private long activatePeriod; // day  -1：表示永久有效
    private int [] mccLists;

    // 种子卡策略 1:纯软卡  2：纯硬卡  3：软卡优先   4：硬卡优先
    private int simUsePolicy;

    private boolean isActivate = false;
    private long activatTime = 0; // ms
    private long deadlineTime = 0; // ms -1：表示永久有效

    private boolean isOutOfDate = false;

    public static final int SOFT_SIM_ONLY = 1;
    public static final int PHY_SIM_ONLY = 2;
    public static final int SOFT_SIM_FIRST = 3;
    public static final int PHY_SIM_FIRST = 4;


    public OrderInfo(String orderId, ArrayList<String> sofsimList) {
        this.orderId = orderId;
        this.sofsimList = sofsimList;
    }

    public OrderInfo(String orderId, ArrayList<String> sofsimList, long createTime, long activatePeriod, int [] mccLists, int simUsePolicy) {
        this.orderId = orderId;
        this.sofsimList = sofsimList;
        this.createTime = createTime;
        this.activatePeriod = activatePeriod;
        this.mccLists = mccLists;
        this.simUsePolicy = simUsePolicy;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public ArrayList<String> getSofsimList() {
        return sofsimList;
    }

    public void setSofsimList(ArrayList<String> sofsimList) {
        this.sofsimList = sofsimList;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getActivatePeriod() {
        return activatePeriod;
    }

    public void setActivatePeriod(long activatePeriod) {
        this.activatePeriod = activatePeriod;
    }

    public int [] getMccLists() {
        return mccLists;
    }

    public void setMccLists(int [] mccLists) {
        this.mccLists = mccLists;
    }

    public int getSimUsePolicy() {
        return simUsePolicy;
    }

    public void setSimUsePolicy(int simUsePolicy) {
        this.simUsePolicy = simUsePolicy;
    }

    public boolean isActivate() {
        return isActivate;
    }

    public void setActivate(boolean activate) {
        isActivate = activate;
    }

    public long getActivatTime() {
        return activatTime;
    }

    public void setActivatTime(long activatTime) {
        this.activatTime = activatTime;
    }

    public long getDeadlineTime() {
        return deadlineTime;
    }

    public void setDeadlineTime(long deadlineTime) {
        this.deadlineTime = deadlineTime;
    }

    public boolean isOutOfDate() {
        return isOutOfDate;
    }

    public void setOutOfDate(boolean outOfDate) {
        isOutOfDate = outOfDate;
    }

    @Override
    public String toString() {
        return "OrderInfo{" +
                "orderId='" + orderId + '\'' +
                ", sofsimList=" + sofsimList +
                ", createTime=" + createTime +
                ", activatePeriod=" + activatePeriod +
                ", mccLists=" + Arrays.toString(mccLists) +
                ", simUsePolicy=" + simUsePolicy +
                ", isActivate=" + isActivate +
                ", activatTime=" + activatTime +
                ", deadlineTime=" + deadlineTime +
                ", isOutOfDate=" + isOutOfDate +
                '}';
    }
}

package com.ucloudlink.refact.business.flow;

/**
 * Created by shiqianhua on 2016/12/19.
 */

public class SoftsimFlowStateInfo {

    private String username;
    private String imsi;
    private long startTime;
    private long endTime;
    private String mcc;
    private long upFlow;
    private long downFlow;
    private long upUserFlow;
    private long downUserFlow;
    private long upSysFlow;
    private long downSysFlow;
    private boolean isSoftsim;

    public SoftsimFlowStateInfo(String username, String imsi, long startTime, long endTime, String mcc, long upFlow, long downFlow, long upUserFlow, long downUserFlow, long upSysFlow, long downSysFlow, boolean isSoftsim) {
        this.username = username;
        this.imsi = imsi;
        this.startTime = startTime;
        this.endTime = endTime;
        this.mcc = mcc;
        this.upFlow = upFlow;
        this.downFlow = downFlow;
        this.upUserFlow = upUserFlow;
        this.downUserFlow = downUserFlow;
        this.upSysFlow = upSysFlow;
        this.downSysFlow = downSysFlow;
        this.isSoftsim = isSoftsim;
    }

    public long getDownSysFlow() {
        return downSysFlow;
    }

    public void setDownSysFlow(long downSysFlow) {
        this.downSysFlow = downSysFlow;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public long getUpFlow() {
        return upFlow;
    }

    public void setUpFlow(long upFlow) {
        this.upFlow = upFlow;
    }

    public long getDownFlow() {
        return downFlow;
    }

    public void setDownFlow(long downFlow) {
        this.downFlow = downFlow;
    }

    public long getUpUserFlow() {
        return upUserFlow;
    }

    public void setUpUserFlow(long upUserFlow) {
        this.upUserFlow = upUserFlow;
    }

    public long getDownUserFlow() {
        return downUserFlow;
    }

    public void setDownUserFlow(long downUserFlow) {
        this.downUserFlow = downUserFlow;
    }

    public long getUpSysFlow() {
        return upSysFlow;
    }

    public void setUpSysFlow(long upSysFlow) {
        this.upSysFlow = upSysFlow;
    }

    public boolean isSoftsim() {
        return isSoftsim;
    }

    public void setSoftsim(boolean softsim) {
        isSoftsim = softsim;
    }

    @Override
    public String toString() {
        return "SoftsimFlowStateInfo{" +
                "username='" + username + '\'' +
                ", imsi='" + imsi + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", mcc='" + mcc + '\'' +
                ", upFlow=" + upFlow +
                ", downFlow=" + downFlow +
                ", upUserFlow=" + upUserFlow +
                ", downUserFlow=" + downUserFlow +
                ", upSysFlow=" + upSysFlow +
                ", downSysFlow=" + downSysFlow +
                ", isSoftsim=" + isSoftsim +
                '}';
    }
}

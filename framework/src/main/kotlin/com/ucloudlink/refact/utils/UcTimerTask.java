package com.ucloudlink.refact.utils;

import java.util.TimerTask;

public abstract class UcTimerTask extends TimerTask {
    private long whenTime;

    public long getWhenTime() {
        return whenTime;
    }

    public void setWhenTime(long whenTime) {
        this.whenTime = whenTime;
    }

    @Override
    public String toString() {
        return "UcTimerTask{" +
                "whenTime=" + whenTime +
                '}';
    }
}

package com.ucloudlink.simservice;

/**
 * Created by jiaming.liang on 2016/9/9.
 */
public abstract class PeriodUpdateCloudSim /* extends PeriodTask*/ {

    public long getDelayTime() {
        return 0;
    }


    public long getPeriodTime() {
        return 3000;
    }
    
}

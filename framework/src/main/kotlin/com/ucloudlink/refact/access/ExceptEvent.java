package com.ucloudlink.refact.access;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by shiqianhua on 2016/12/21.
 */

public class ExceptEvent {
    private boolean inException = false;
    private Date startTime;
//    private Date endTime;
//    private long lastTime;

    private long timeoutValue = TimeUnit.MINUTES.toMillis(6);

    public ExceptEvent(long timeout){
        timeoutValue = timeout;
    }

    public ExceptEvent(){

    }

    public void startException(){
        if(startTime == null){
            startTime = new Date();
        }
        inException = true;
    }

    public void stopException(){
        inException = false;
    }

    public long getLastTime(){
        if(startTime != null){
            return new Date().getTime() - startTime.getTime();
        }
        return  0;
    }

    public boolean getInException(){
        return inException;
    }

    public boolean isOverTimeoutTh(){
        if(getLastTime() >= timeoutValue){
            return true;
        }else {
            return false;
        }
    }

    public void clearException(){
        inException = false;
        startTime = null;
    }

    @Override
    public String toString() {
        return "inException: " + inException + " starttime:" + startTime;
    }
}

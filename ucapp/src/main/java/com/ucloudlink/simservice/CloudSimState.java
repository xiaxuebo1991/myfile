package com.ucloudlink.simservice;

/**
 * Created by jiaming.liang on 2016/6/2.
 */
public class CloudSimState {
    public static final int state_off        = 0;
    public static final int state_turning_on = 1;
    public static final int state_on         = 2;
    public static final int state_switching  = 3;
    //*****以上是各种状态*************************//
    
    int    CarrierType;//营运商
    String imsi;//sim卡的15位卡号
    int    LteSignalStrength;
    
    private int state = state_off;//状态

    public int getState() {
        return state;
    }

    private void setState(int state) {
//        logd("sim setstate old :" + this.status + "new:" + status);
        if (this.state != state) {
            this.state = state;
            notifyStateChange(state);
        }
    }

    private void notifyStateChange(int state) {
//        EventBus aDefault = EventBus.getDefault();
//        logd("SimManagerState EventBus:" + aDefault);
//        aDefault.post(this);
    }

    public void turningOn() {
        setState(state_turning_on);
    }

    public void close() {
        setState(state_off);
    }

    public void switching() {
        setState(state_switching);
    }

    public void stateOn() {//切换成功
        setState(state_on);
    }
}


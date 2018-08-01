package com.ucloudlink.refact.business.flow;

/**
 * Created by pengchugang on 2017/5/12.
 */

public class LocalPackageInfo {
    private String packageName;
    private boolean restrict;
    private int uid;

    public boolean getRestrict(){
        return restrict;
    }
    public void setRestrict(boolean bVal){
        restrict = bVal;
    }

    public String getPackageName(){
        return packageName;
    }

    public void setPackageName(String strName){
        packageName = strName;
    }

    public void setPackageUid(int uid){
        this.uid = uid;
    }

    public int getPackageUid(){
        return uid;
    }
}

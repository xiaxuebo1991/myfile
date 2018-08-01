package com.ucloudlink.framework.ui;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shiqianhua on 2017/3/7.
 */

public class PlmnInfo implements Parcelable {
    private List<String> plmnList = new ArrayList<String>();

    public PlmnInfo() {
    }

    public PlmnInfo(Parcel parcel){
        super();
        this.setPlmnList(parcel.readArrayList(List.class.getClassLoader()));// 3
    }

    public List<String> getPlmnList() {
        return plmnList;
    }

    public void setPlmnList(List plmnList) {
        this.plmnList = plmnList;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(plmnList);
    }

    public static final Creator<PlmnInfo> CREATOR = new Creator<PlmnInfo>() {
        @Override
        public PlmnInfo createFromParcel(Parcel parcel) {
            return new PlmnInfo(parcel);
        }

        @Override
        public PlmnInfo[] newArray(int size) {
            return new PlmnInfo[size];
        }
    };

    @Override
    public String toString() {
        return "PlmnInfo{" +
                "plmnList=" + plmnList +
                '}';
    }
}

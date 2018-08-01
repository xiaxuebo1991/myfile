package com.ucloudlink.refact.access.ui.struct;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by shiqianhua on 2016/11/3.
 */
public class UcSystemStatus implements Parcelable {
    int processPersent;

    protected UcSystemStatus(Parcel in) {
        processPersent = in.readInt();
    }

    public static final Creator<UcSystemStatus> CREATOR = new Creator<UcSystemStatus>() {
        @Override
        public UcSystemStatus createFromParcel(Parcel in) {
            return new UcSystemStatus(in);
        }

        @Override
        public UcSystemStatus[] newArray(int size) {
            return new UcSystemStatus[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(processPersent);
    }
}

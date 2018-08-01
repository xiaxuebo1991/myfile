package com.ucloudlink.framework.ui;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Created by shiqianhua on 2016/12/27.
 */

public class ExceptionValue implements Parcelable {
    private ArrayList<Integer> except;

    public ExceptionValue(ArrayList<Integer> except) {
        this.except = except;
    }

    public ArrayList<Integer> getExcept() {
        return except;
    }

    public void setExcept(ArrayList<Integer> except) {
        this.except = except;
    }

    @Override
    public String toString() {
        return "ExceptionValue{" +
                "except=" + except +
                '}';
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int count = except.size();
        dest.writeInt(count);
        for(Integer a: except){
            dest.writeInt(a);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ExceptionValue> CREATOR = new Creator<ExceptionValue>(){
        @Override
        public ExceptionValue createFromParcel(Parcel source) {
            ArrayList<Integer> result = new ArrayList<Integer>();
            int count = source.readInt();
            for(int i = 0; i < count; i++){
                result.add(source.readInt());
            }
            return new ExceptionValue(result);
        }

        @Override
        public ExceptionValue[] newArray(int size) {
            return new ExceptionValue[size];
        }
    };
}

package com.ucloudlink.refact.access.struct;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by shiqianhua on 2016/10/17.
 */
public class LoginInfo implements Serializable{
    private String username;
    private String passwd;
    private static final long serialVersionUID = 1L;
    public LoginInfo(String username, String passwd){
        setUsername(username);
        setPasswd(passwd);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswd() {
        return passwd;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

}

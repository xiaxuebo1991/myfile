package com.ucloudlink.refact.business.softsim.download.struct;

import com.ucloudlink.framework.ui.FlowOrder;

import java.util.ArrayList;

/**
 * Created by shiqianhua on 2016/12/22.
 */

public class DownloadReqInfo {
    private String username;
    private String password;
    private ArrayList<FlowOrder> order;

    public DownloadReqInfo(String username, String password, ArrayList<FlowOrder> order) {
        this.username = username;
        this.password = password;
        this.order = order;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ArrayList<FlowOrder> getOrder() {
        return order;
    }

    public void setOrder(ArrayList<FlowOrder> order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}

package com.ucloudlink.refact.business.softsim.struct;

import java.util.ArrayList;

/**
 * Created by shiqianhua on 2016/12/6.
 */

public class UserOrderInfo {
    private String username;
    private ArrayList<OrderInfo> orderList;

    public UserOrderInfo(String username, ArrayList<OrderInfo> orderList) {
        this.username = username;
        this.orderList = orderList;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ArrayList<OrderInfo> getOrderList() {
        return orderList;
    }

    public void setOrderList(ArrayList<OrderInfo> orderList) {
        this.orderList = orderList;
    }

    @Override
    public String toString() {
        return "UserOrderInfo{" +
                "username='" + username + '\'' +
                ", orderList=" + orderList +
                '}';
    }
}

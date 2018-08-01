package com.ucloudlink.refact.business.softsim.download.remote.struct;

/**
 * Created by shiqianhua on 2016/12/1.
 */

public class ServerInfo {
    private String serverIp;
    private int serverPort;

    public ServerInfo(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public String toString() {
        return "ServerInfo{" +
                "serverIp='" + serverIp + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}

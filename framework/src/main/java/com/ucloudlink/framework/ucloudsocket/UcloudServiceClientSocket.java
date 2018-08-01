/******************************************************************************
 * @file UcloudServiceClientSocket.java
 * <p/>
 * ---------------------------------------------------------------------------
 * Copyright (c) 2014 ucloudlink Technologies, Inc.  All Rights Reserved.
 * ucloudlink Technologies Proprietary and Confidential.
 * ---------------------------------------------------------------------------
 ******************************************************************************/

package com.ucloudlink.framework.ucloudsocket;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ucloudlink.framework.util.ByteUtils;
import com.ucloudlink.refact.utils.JLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.ucloudlink.refact.utils.JLog;
/*定义和实现类UcloudServiceClientSocket*/
public class UcloudServiceClientSocket extends Handler implements Runnable {
    public boolean socketDisConnectStatus = true;
    private final String TAG = "UcloudServiceClientSocket";
    /*定义socket地址，和socket输入输出变量*/
    private       String       SocketAddress            = "qmux_radio/ucloudlink_client_socket";
    private final int          SOCKET_FAILED_RETRY_TIME = 20;
    private final int          SOCKET_FAILED_SLEEP_TIME = 4000;
    private       LocalSocket  mUclooudSocket                  = null;
    private       InputStream  mIS                      = null;
    private       OutputStream mOS                      = null;
    //定义buffer大小
    private final int    BUFFER_SIZE   = 1024;
    private       byte[] mBuffer       = new byte[BUFFER_SIZE];
    private       int    mBufferLength = 0;

    private Handler mRecvHdlr  = null;
    private int     instanceId = 0;

    private boolean mToDestroy = false;

    public UcloudServiceClientSocket(Handler recvHdlr, int slotId, Looper looper) {
        super(looper);
        JLog.logv("UcloudServiceClientSocket()");
        mRecvHdlr = recvHdlr;
        instanceId = slotId;
        SocketAddress = SocketAddress + Integer.toString(slotId);
    }

    public void toDestroy() {
        JLog.logv("toDestroy()");
        mToDestroy = true;
        resetSocket();
    }
    /*连接socket*/
    private boolean connectSocket() {
        JLog.logv("ucloudlink connectSocket()");
        resetSocket();
        int counter = 0;
        while (counter < SOCKET_FAILED_RETRY_TIME) {
            try {
                LocalSocketAddress addr = new LocalSocketAddress(SocketAddress, LocalSocketAddress.Namespace.RESERVED);
                mUclooudSocket = new LocalSocket();
                JLog.logi("ucloudlink Connecting to socket " + addr.getName() + "...");
                mUclooudSocket.connect(addr);
                JLog.logd("ucloudlink Connected to socket " + addr.getName());
                mOS = mUclooudSocket.getOutputStream();
                mIS = mUclooudSocket.getInputStream();
                JLog.logd("ucloudlink connectSocket: mOS:" + mOS + "mIS" + mIS);
                socketDisConnectStatus = false;
                return true;
            } catch (Exception e) {
                JLog.loge("ucloudlink Socket connection failed");
                mUclooudSocket = null;
                e.printStackTrace();
                try {
                    Thread.sleep(SOCKET_FAILED_SLEEP_TIME);
                } catch (Exception sleepExp) {
                    JLog.loge("thread sleep failed");
                }
            }
            JLog.loge("ucloudlink connect socket counter is "+counter);
            counter++;
        }
        return false;
    }
   /*关闭或者重置socket*/
    private void resetSocket() {
        JLog.logv("resetSocket()");
        if (mUclooudSocket != null) {
            try {
                mUclooudSocket.shutdownInput();
                mUclooudSocket.shutdownOutput();
                mUclooudSocket.close();
            } catch (IOException e) {
                JLog.loge("resetSocket() - failed!");
                e.printStackTrace();
            }
            mUclooudSocket = null;
            mIS = null;
            mOS = null;
        } else {
            JLog.loge("resetSocket() - socket is not initialized");
        }
    }
    /*接收Message*/
    @Override
    public void handleMessage(Message msg) {
        byte[] bytes = (byte[]) msg.obj;
        JLog.logv("ucloudlink handleMessage() - Message length: " + bytes.length);
        send(bytes);
    }
    /*发送消息到对端*/
    private void send(byte[] bytes) {
        if (mUclooudSocket == null) {
            JLog.loge("send() - mUclooudSocket is null!");
            return;
        }
        //       logd( "sendbytes!!: bytes"+ ByteUtils.bytesToHex(bytes));
        try {            byte[] toSend = new byte[bytes.length + 4];
            // length in big endian
            toSend[0] = toSend[1] = 0;
            toSend[2] = (byte) ((bytes.length >> 8) & 0xff);
            toSend[3] = (byte) (bytes.length & 0xff);
            System.arraycopy(bytes, 0, toSend, 4, bytes.length);
            //           logd( "send: mOS:"+mOS+"toSend:"+ByteUtils.bytesToHex(toSend));
            mOS.write(toSend);
        } catch (Exception e) {
            JLog.loge("send() - write failed !!!");
            e.printStackTrace();
        }
    }
    /*接收对端信息*/
    public void run() {
        JLog.logv("run()");
        boolean toConnectSocket = true;
        while (!mToDestroy) {
            if (toConnectSocket && !connectSocket()) {
                JLog.loge("run() - connect socket failed.");
                return;
            }
            toConnectSocket = false;
            int bytesRead = 0;
            int posOffset = 0;
            //wangkun
            //int bytesToRead = 4;
            int bytesToRead = 4;
            try {
                do {
                    bytesRead = mIS.read(mBuffer, posOffset, bytesToRead);
                    JLog.logv("run() - bytesToRead size is: " + bytesToRead);
                    JLog.logv("run() - bytesRead is: " + bytesRead);
                    if (bytesRead < 0) {
                        JLog.loge("run() - bytesRead < 0 when reading message length");
                        break;
                    }
                    posOffset += bytesRead;
                    bytesToRead -= bytesRead;
                } while (bytesToRead > 0);
            } catch (IOException e) {
                JLog.loge("Exception in reading socket");
                e.printStackTrace();
                break;
            }
            if (bytesRead < 0) {
                toConnectSocket = true;
                continue;
            }
            posOffset = 0;
            bytesToRead = ((mBuffer[0] & 0xff) << 24) | ((mBuffer[1] & 0xff) << 16) | ((mBuffer[2] & 0xff) << 8) | (mBuffer[3] & 0xff);
            JLog.logv("ucloudlink run() - Message size is: " + bytesToRead);
            mBufferLength = bytesToRead;
            try {
                do {
                    bytesRead = mIS.read(mBuffer, posOffset, bytesToRead);
                    if (bytesRead < 0) {
                        JLog.loge("run() - bytesRead < 0 when reading message");
                        break;
                    }
                    posOffset += bytesRead;
                    bytesToRead -= bytesRead;
                } while (bytesToRead > 0 && mIS != null);
            } catch (Exception e) {
                JLog.loge("Exception in reading socket");
                e.printStackTrace();
                break;
            }
            if (bytesRead < 0) {
                toConnectSocket = true;
                continue;
            }
            handleRecvBytes();
        }
        JLog.logd("exit run");
    }
    /*拿到对端信息后，进一步处理*/
        private void handleRecvBytes() {
            JLog.logi( "ucloudlink handleRecvBytes()");
            if (mRecvHdlr == null) {
                return;
            }
            Message msg;
            byte[] bytes = Arrays.copyOf(mBuffer, mBufferLength);
            JLog.logd( "ucloudlink handleRecvBytes: bytes"+ ByteUtils.bytesToHex(bytes));

            msg = mRecvHdlr.obtainMessage(UcloudServiceClientService.EVENT_RESP, instanceId, 0, bytes);
            msg.sendToTarget();
    }
    /*获取socket状态*/
    public boolean getSocketDisConnectStatus(){
        JLog.logi( "ucloudlink socket getSocketDisConnectStatus");
        return socketDisConnectStatus;
    }
}

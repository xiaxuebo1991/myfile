/******************************************************************************
 * @file UimRemoteClientSocket.java
 * <p/>
 * ---------------------------------------------------------------------------
 * Copyright (c) 2014 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 * ---------------------------------------------------------------------------
 ******************************************************************************/

package com.ucloudlink.framework.remoteuim;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static com.ucloudlink.refact.utils.JLog.*;

public class UimRemoteClientSocket extends Handler implements Runnable {
    private final String TAG = "UimRemoteClientSocket";

    private       String       SocketAddress            = "qmux_radio/uim_remote_client_socket";
    private final int          SOCKET_FAILED_RETRY_TIME = 20;
    private final int          SOCKET_FAILED_SLEEP_TIME = 4000;
    private       LocalSocket  mSocket                  = null;
    private       InputStream  mIS                      = null;
    private       OutputStream mOS                      = null;

    private final int    BUFFER_SIZE   = 1024;
    private       byte[] mBuffer       = new byte[BUFFER_SIZE];
    private       int    mBufferLength = 0;

    private Handler mRecvHdlr  = null;
    private int     instanceId = 0;

    private boolean mToDestroy = false;

    public UimRemoteClientSocket(Handler recvHdlr, int slotId, Looper looper) {
        super(looper);
        logv("UimRemoteClientSocket()");
        mRecvHdlr = recvHdlr;
        instanceId = slotId;
        SocketAddress = SocketAddress + Integer.toString(slotId);
    }

    public void toDestroy() {
        logv("toDestroy()");
        mToDestroy = true;
        resetSocket();
    }

    private boolean connectSocket() {
        logv("connectSocket()");
        resetSocket();
        int counter = 0;
        while (counter < SOCKET_FAILED_RETRY_TIME) {
            try {
                LocalSocketAddress addr = new LocalSocketAddress(SocketAddress, LocalSocketAddress.Namespace.RESERVED);
                mSocket = new LocalSocket();
                logi("Connecting to socket " + addr.getName() + "...");
                mSocket.connect(addr);
                logd("Connected to socket " + addr.getName());
                mOS = mSocket.getOutputStream();
                mIS = mSocket.getInputStream();
                logd("connectSocket: mOS:" + mOS + "mIS" + mIS);
                return true;
            } catch (Exception e) {
                loge("Socket connection failed");
                mSocket = null;
                e.printStackTrace();
                try {
                    Thread.sleep(SOCKET_FAILED_SLEEP_TIME);
                } catch (Exception sleepExp) {
                    loge("thread sleep failed");
                }
            }
            counter++;
        }
        return false;
    }

    private void resetSocket() {
        logv("resetSocket()");
        if (mSocket != null) {
            try {
                mSocket.shutdownInput();
                mSocket.shutdownOutput();
                mSocket.close();
            } catch (IOException e) {
                loge("resetSocket() - failed!");
                e.printStackTrace();
            }
            mSocket = null;
            mIS = null;
            mOS = null;
        } else {
            loge("resetSocket() - socket is not initialized");
        }
    }

    @Override
    public void handleMessage(Message msg) {
        byte[] bytes = (byte[]) msg.obj;
        logv("handleMessage() - Message length: " + bytes.length);
        send(bytes);
    }

    private void send(byte[] bytes) {
        if (mSocket == null) {
            loge("send() - mSocket is null!");
            return;
        }
        //       logd( "sendbytes!!: bytes"+ ByteUtils.bytesToHex(bytes));
        try {
            byte[] toSend = new byte[bytes.length + 4];
            // length in big endian
            toSend[0] = toSend[1] = 0;
            toSend[2] = (byte) ((bytes.length >> 8) & 0xff);
            toSend[3] = (byte) (bytes.length & 0xff);
            System.arraycopy(bytes, 0, toSend, 4, bytes.length);
//           logd( "send: mOS:"+mOS+"toSend:"+ByteUtils.bytesToHex(toSend));
            Log.d(TAG, "send: startTime :" + System.currentTimeMillis());
            mOS.write(toSend);
            Log.d(TAG, "send: endTime :" + System.currentTimeMillis());
        } catch (Exception e) {
            loge("send() - write failed !!!");
            e.printStackTrace();
        }
    }

    public void run() {
        logv("run()");
        boolean toConnectSocket = true;
        while (!mToDestroy) {
            if (toConnectSocket && !connectSocket()) {
                loge("run() - connect socket failed.");
                return;
            }
            toConnectSocket = false;
            int bytesRead = 0;
            int posOffset = 0;
            int bytesToRead = 4;
            try {
                do {
                    bytesRead = mIS.read(mBuffer, posOffset, bytesToRead);
                    if (bytesRead < 0) {
                        loge("run() - bytesRead < 0 when reading message length");
                        break;
                    }
                    posOffset += bytesRead;
                    bytesToRead -= bytesRead;
                } while (bytesToRead > 0);
            } catch (IOException e) {
                loge("Exception in reading socket");
                e.printStackTrace();
                break;
            }
            if (bytesRead < 0) {
                toConnectSocket = true;
                continue;
            }
            posOffset = 0;
            bytesToRead = ((mBuffer[0] & 0xff) << 24) | ((mBuffer[1] & 0xff) << 16) | ((mBuffer[2] & 0xff) << 8) | (mBuffer[3] & 0xff);
            logv("run() - Message size is: " + bytesToRead);
            mBufferLength = bytesToRead;
            try {
                do {
                    bytesRead = mIS.read(mBuffer, posOffset, bytesToRead);
                    if (bytesRead < 0) {
                        loge("run() - bytesRead < 0 when reading message");
                        break;
                    }
                    posOffset += bytesRead;
                    bytesToRead -= bytesRead;
                } while (bytesToRead > 0 && mIS != null);
            } catch (Exception e) {
                loge("Exception in reading socket");
                e.printStackTrace();
                break;
            }
            if (bytesRead < 0) {
                toConnectSocket = true;
                continue;
            }
            handleRecvBytes();
        }
        logd("exit run");
    }

    private void handleRecvBytes() {
        //        logi( "handleRecvBytes()");
        if (mRecvHdlr == null) {
            return;
        }
        Message msg;
        byte[] bytes = Arrays.copyOf(mBuffer, mBufferLength);
        //       logd( "handleRecvBytes: bytes"+ByteUtils.bytesToHex(bytes));
        //arg1 consists of the sim (slot)instance id of the socket.
        msg = mRecvHdlr.obtainMessage(UimRemoteClientService.EVENT_RESP, instanceId, 0, bytes);
        msg.sendToTarget();
    }

    private void logv(String msg) {
        
    }
}

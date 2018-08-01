package com.ucloudlink.refact.business.flow.netlimit.common;

import com.ucloudlink.refact.utils.JLog;

/**
 * Created by jianguo.he on 2017/11/13.
 * 统计网络发送、接收数据长度
 */

public class NetPackageStatisticsCtrl {

    private static NetPackageStatisticsCtrl instance;

    private boolean enable = true;
    private boolean isStatisticsing = false;
    private long decodeReadableBytes;
    private long decodeDataLen;

    private long encodeOutLen;

    private NetPackageStatisticsCtrl(){

    }

    public static NetPackageStatisticsCtrl getInstance(){
        if(instance==null){
            synchronized (NetPackageStatisticsCtrl.class){
                if(instance==null){
                    instance = new NetPackageStatisticsCtrl();
                }
            }
        }
        return instance;
    }

    public void start(){
        if(!enable){
            return;
        }
        decodeReadableBytes = 0;
        encodeOutLen = 0;
        isStatisticsing = true;
        JLog.logi("NettyLen start(): decodeReadableBytes = "+decodeReadableBytes+", encodeOutLen = "+encodeOutLen);
    }

    public void stop(){
        if(!enable){
            return;
        }
        isStatisticsing = false;
        JLog.logi("NettyLen stop(): decodeReadableBytes = "+decodeReadableBytes+", encodeOutLen = "+encodeOutLen);
    }

    /***
     * 统计收到的数据长度，未包含请求头，请求尾
     * @param len 网络接收数据长度
     * @return
     */
    public NetPackageStatisticsCtrl appendDecodeReadableBytes(long len){
        if(isStatisticsing){
            decodeReadableBytes += len;
            JLog.logi("NettyLen appendReadableBytes(len="+len+"): decodeReadableBytes = "+decodeReadableBytes);
        }
        return instance;
    }

    /**
     * decode的时候把数据封装成ProtoPacket的数据长度, 非网络传送数据长度依据
     * @param len   ProtoPacket.data的长度
     * @return
     */
    public NetPackageStatisticsCtrl appendDecodeDataLen(long len){
        if(isStatisticsing){
            decodeDataLen += len;
            //JLog.logi("NettyLen appendDecodeDataLen(len="+len+"): decodeDataLen = "+decodeDataLen);
        }
        return instance;
    }

    /**
     * 统计发送的数据长度, 未包含请求头，请求尾
     * @param len 发送给网络的数据长度
     * @return
     */
    public NetPackageStatisticsCtrl appendEncodeOutLen(long len){
        if(isStatisticsing){
            encodeOutLen += len;
            JLog.logi("NettyLen appendEncodeOutLen(len="+len+"): encodeOutLen = "+encodeOutLen);
        }
        return instance;
    }

}

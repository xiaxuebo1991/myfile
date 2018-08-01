/******************************************************************************
  @file    UcloudServiceClientMsgPacking.java

  ---------------------------------------------------------------------------
  Copyright (c) 2014 Qualcomm Technologies, Inc.  All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

package com.ucloudlink.framework.ucloudsocket;

import com.ucloudlink.refact.utils.JLog;



public class UcloudServiceClientMsgPacking {
    private static final String LOG_TAG = "UcloudServiceClientSer";

    public static byte[] packMsg (MessageId msgId, MessageType msgType, Object msg) {
        byte[] bytes = null;
        JLog.logv( "packMsg() - msgId: " + msgId + ", msgType: " + msgType + ", msg:" + msg);
        try {
            if (msgType == MessageType.UCLOUDLINK_MSG_REQUEST) {
                switch (msgId) {
                case UCLOUDLINK_EVENT:
                    bytes = ((UcloudServiceEventReq)msg).encode();
                    break;
                default:
                    JLog.loge( "unexpected msgId");
                };
            } else {
                JLog.loge( "unexpected msgType");
            }
        } catch (Exception e) {
            JLog.loge( "Exception in msg protobuf encoding");
            e.printStackTrace();
        }

        return bytes;
    }

    public static Object unpackMsg(int msgId, int msgType, byte[] bytes) {
        Object msg = null;
        JLog.logi( "unpackMsg() - msgId: " + msgId + ", msgType: " + msgType);
        //MessageId.UIM_REMOTE_APDU
        try {
            if (MessageType.fromValue(msgType) == MessageType.UCLOUDLINK_MSG_RESPONSE) {
                switch (MessageId.fromValue(msgId)) {

                    case UCLOUDLINK_EVENT:
                        //msg = UcloudServiceEventResp.ADAPTER.decode(bytes);
                        break;
                    default:
                        JLog.loge( "unexpected msgId");
                };
            } else if (MessageType.fromValue(msgType) == MessageType.UCLOUDLINK_MSG_INDICATION) {
                switch (MessageId.fromValue(msgId)) {
                    default:
                        JLog.loge( "unexpected msgId");
                };

            } else {
                JLog.loge( "unexpected msgType");
            }
        } catch (Exception e) {
            JLog.loge( "Exception in msg protobuf decoding");
        }

        return msg;
    }

    public static byte[] packTag(MessageTag tag) {
        JLog.logv( "packTag()");
        byte[] bytes = null;
        try {
            bytes = tag.encode();
        } catch (Exception e) {
            JLog.loge( "Exception in msg protobuf encoding");
        }
        return bytes;
    }

    public static MessageTag unpackTag(byte[] bytes) {
        JLog.logi( "unpackTag()");
        MessageTag tag = null;
        try {
            tag = MessageTag.ADAPTER.decode(bytes);
        } catch (Exception e) {
            JLog.loge( "Exception in tag protobuf decoding");
        }
        return tag;
    }
}

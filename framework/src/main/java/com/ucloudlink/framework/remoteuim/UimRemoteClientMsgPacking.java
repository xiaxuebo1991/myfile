/******************************************************************************
  @file    UimRemoteClientMsgPacking.java

  ---------------------------------------------------------------------------
  Copyright (c) 2014 Qualcomm Technologies, Inc.  All Rights Reserved.
  Qualcomm Technologies Proprietary and Confidential.
  ---------------------------------------------------------------------------
******************************************************************************/

package com.ucloudlink.framework.remoteuim;

import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;
import static com.ucloudlink.refact.utils.JLog.logv;

public class UimRemoteClientMsgPacking {
    private static final String LOG_TAG = "UimRemoteClientSer";

    public static byte[] packMsg (MessageId msgId, MessageType msgType, Object msg) {
        byte[] bytes = null;
        logv( "packMsg() - msgId: " + msgId + ", msgType: " + msgType + ", msg:" + msg);
        try {
            if (msgType == MessageType.UIM_REMOTE_MSG_REQUEST) {
                switch (msgId) {
                case UIM_REMOTE_EVENT:
                    bytes = ((UimRemoteEventReq)msg).encode();
                    break;
                case UIM_REMOTE_APDU:
                    bytes = ((UimRemoteApduReq)msg).encode();
                    break;
                default:
                    loge( "unexpected msgId");
                };
            } else {
                loge( "unexpected msgType");
            }
        } catch (Exception e) {
            loge( "Exception in msg protobuf encoding");
            e.printStackTrace();
        }

        return bytes;
    }

    public static Object unpackMsg(int msgId, int msgType, byte[] bytes) {
        Object msg = null;
        logi( "unpackMsg() - msgId: " + msgId + ", msgType: " + msgType);
        //MessageId.UIM_REMOTE_APDU
        try {
            if (MessageType.fromValue(msgType) == MessageType.UIM_REMOTE_MSG_RESPONSE) {
                switch (MessageId.fromValue(msgId)) {

                    case UIM_REMOTE_EVENT:
                        msg = UimRemoteEventResp.ADAPTER.decode(bytes);
                        break;
                    case UIM_REMOTE_APDU:
                        msg = UimRemoteApduResp.ADAPTER.decode(bytes);
                        break;
                    default:
                        loge( "unexpected msgId");
                };
            } else if (MessageType.fromValue(msgType) == MessageType.UIM_REMOTE_MSG_INDICATION) {
                switch (MessageId.fromValue(msgId)) {

                    case UIM_REMOTE_APDU:
                        msg = UimRemoteApduInd.ADAPTER.decode(bytes);
                        break;
                    case UIM_REMOTE_CONNECT:
                        break;
                    case UIM_REMOTE_DISCONNECT:
                        break;
                    case UIM_REMOTE_POWER_UP:
                        msg = UimRemotePowerUpInd.ADAPTER.decode(bytes);
                        break;
                    case UIM_REMOTE_POWER_DOWN:
                        msg = UimRemotePowerDownInd.ADAPTER.decode(bytes);
                        break;
                    case UIM_REMOTE_RESET:
                        break;
                    default:
                        loge( "unexpected msgId");
                };

            } else {
                loge( "unexpected msgType");
            }
        } catch (Exception e) {
            loge( "Exception in msg protobuf decoding");
        }

        return msg;
    }

    public static byte[] packTag(MessageTag tag) {
        logv( "packTag()");
        byte[] bytes = null;
        try {
            bytes = tag.encode();
        } catch (Exception e) {
            loge( "Exception in msg protobuf encoding");
        }
        return bytes;
    }

    public static MessageTag unpackTag(byte[] bytes) {
        logi( "unpackTag()");
        MessageTag tag = null;
        try {
            tag = MessageTag.ADAPTER.decode(bytes);
        } catch (Exception e) {
            loge( "Exception in tag protobuf decoding");
        }
        return tag;
    }
}

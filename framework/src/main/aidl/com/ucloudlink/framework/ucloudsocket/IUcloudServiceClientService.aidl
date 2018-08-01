/******************************************************************************
 * @file    IUcServiceClientService.aidl
 * @brief   This interface describes the APIs for the service which allows
 *          a client to make a remote UIM card available to the service.
 *
 * @version 00.00.01
 *
 * ---------------------------------------------------------------------------
 * Copyright (c) 2014 Qualcomm Technologies, Inc.  All rights reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 * ---------------------------------------------------------------------------
 *
 *******************************************************************************/

package com.ucloudlink.framework.ucloudsocket;

import com.ucloudlink.framework.ucloudsocket.IUcloudServiceClientServiceCallback;

interface IUcloudServiceClientService {
    /**
     * registerCallback will be used by a client to register a callback to be
     * notified asynchronously
     *
     * @param cb
     *    Defines the callback interface
     *
     * @return
     *    UIM_REMOTE_SUCCESS       = 0;
     *    UIM_REMOTE_ERROR         = 1;
     */
    int registerCallback(in IUcloudServiceClientServiceCallback cb);

    /**
     * deregisterCallback will be used by a client to deregister a callback to be
     * notified asynchronously
     *
     * @param cb
     *    Defines the callback interface
     *
     * @return
     *    UIM_REMOTE_SUCCESS       = 0;
     *    UIM_REMOTE_ERROR         = 1;
     */
    int deregisterCallback(in IUcloudServiceClientServiceCallback cb);

    /**
     * UcServiceEvent will be used by a client to send remote UIM card
     * events to the service
     *
     * @param slot
     *    UIM_REMOTE_SLOT0  = 0;
     *    UIM_REMOTE_SLOT1  = 1;
     *    UIM_REMOTE_SLOT2  = 2;
     *
     * @param event
     *    UIM_REMOTE_CONNECTION_UNAVAILABLE = 0;
     *    UIM_REMOTE_CONNECTION_AVAILABLE   = 1;
     *    UIM_REMOTE_CARD_INSERTED          = 2;
     *    UIM_REMOTE_CARD_REMOVED           = 3;
     *    UIM_REMOTE_CARD_ERROR             = 4;
     *    UIM_REMOTE_CARD_RESET             = 5;
     *
     * @param atr
     *    The byte array atr will be populated for events UIM_REMOTE_CARD_INSERTED
     *    and UIM_REMOTE_CARD_RESET
     *
     * @param errCode
     *    This param will be non-zero only for UIM_REMOTE_CARD_ERROR event
     *    UIM_REMOTE_CARD_ERROR_NONE               = 0;
     *    UIM_REMOTE_CARD_ERROR_UNKNOWN            = 1;
     *    UIM_REMOTE_CARD_ERROR_NO_LINK_EST        = 2;
     *    UIM_REMOTE_CARD_ERROR_CMD_TIMEOUT        = 3;
     *    UIM_REMOTE_CARD_ERROR_POWER_DOWN         = 4;
     *
     * 	@Transport
     *    UIM_REMOTE_TRANSPORT_OTHER = 0;
     *    UIM_REMOTE_TRANSPORT_BLUETOOTH = 1;
     *    UIM_REMOTE_TRANSPORT_IP = 2;
     *
     *  @Usage
     *    UIM_REMOTE_USAGE_REDUCED = 0;
     *    UIM_REMOTE_USAGE_NORMAL = 1;
     *
     * @return
     *    UIM_REMOTE_SUCCESS       = 0;
     *    UIM_REMOTE_ERROR         = 1;
     */
    int ucloudServiceEventProc(in int slot, in int event, in byte[] atr, in int subevent,in String subeventstring,in int errCode,int eventTimeout);
    /**
     * UcServiceApdu will be used by the client to provide response APDUs
     * returned by the remote UIM card in response to the command APDU send
     * by the service
     *
     * @param slot
     *    UIM_REMOTE_SLOT0  = 0;
     *    UIM_REMOTE_SLOT1  = 1;
     *    UIM_REMOTE_SLOT2  = 2;
     *
     * @param apduStatus
     *    Inform the UIM remote client of the APDU exchange status
     *    UIM_REMOTE_APDU_EXCHANGE_SUCCESS = 0;
     *    UIM_REMOTE_APDU_EXCHANGE_FAILURE = 1;
     *
     * @param apduResp
     *    APDU response data from card
     *
     * @return
     *    UIM_REMOTE_SUCCESS       = 0;
     *    UIM_REMOTE_ERROR         = 1;
     */
     boolean getSocketDisConnectStatus();
}

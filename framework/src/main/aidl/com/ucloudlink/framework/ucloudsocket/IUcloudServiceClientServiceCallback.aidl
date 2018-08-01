/******************************************************************************
 * @file    IUcServiceClientServiceCallback.aidl
 * @brief   This interface describes the APIs for the callback that a client
 *          which uses IUcLOUDsERVICEClientService should implement in order to
 *          be notified of asynchronous indications.
 *
 * @version 00.00.01
 *
 * Copyright (c) 2014 Qualcomm Technologies, Inc.  All rights reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 *
 *******************************************************************************/

package com.ucloudlink.framework.ucloudsocket;

interface IUcloudServiceClientServiceCallback {
    /**
     * Send Event response to the client
     *
     * @param responseCode
     *    UIM_REMOTE_RESP_SUCCESS = 0;
     *    UIM_REMOTE_RESP_FAILURE = 1;
     *
     * @return None
     */
    void ucloudServiceEventResponse(in int slot, in int responseCode);
    /**
     * Send an APDU indication requesting client to send an APDU to
     * the remote UIM card
     *
     * @param slot
     *    UIM_REMOTE_SLOT0  = 0;
     *    UIM_REMOTE_SLOT1  = 1;
     *    UIM_REMOTE_SLOT2  = 2;
     *
     * @param apduCmd
     *    APDU command data sent to the remote UIM
     *
     * @return None
     */
    void ucloudServiceEventIndication(in int slot, in byte[] apduCmd);

}

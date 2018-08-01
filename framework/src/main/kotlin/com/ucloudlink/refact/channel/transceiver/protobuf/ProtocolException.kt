package com.ucloudlink.refact.channel.transceiver.protobuf

/**
 * Created by chentao on 2016/6/25.
 */
class ProtocolException: Exception {

    constructor(detailMessage: String?) : super(detailMessage)

    constructor(detailMessage: String?, throwable: Throwable?) : super(detailMessage, throwable)

    constructor(throwable: Throwable?) : super(throwable)

    constructor() : super()
}
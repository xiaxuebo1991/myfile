package com.ucloudlink.framework.remoteuim

/**
 * Created by chentao on 2016/7/1.
 */
class CardException : Exception {
    constructor():super()

    constructor(detailMessage: String?) : super(detailMessage)

    constructor(detailMessage: String?, throwable: Throwable?) : super(detailMessage, throwable)

    constructor(throwable: Throwable?) : super(throwable)
}
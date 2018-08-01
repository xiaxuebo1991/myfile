package com.ucloudlink.refact.channel.apdu

import com.ucloudlink.refact.channel.apdu.ApduData
import rx.Observable
import rx.Single

/**
 * 代理sim卡的apdu信息。Card类会通过这个代理来获取需要发送给modem的apdu响应。
 * apdu响应可以是通过网络获取，也可以是本地softsim库提供。
 */
interface ApduDelegate {
    /**
     * @param req sim卡收到apdu请求
     * @return Observable<ApduData> 回复apdu响应
     */
    fun apdu(req: ApduData): Single<ApduData>
}
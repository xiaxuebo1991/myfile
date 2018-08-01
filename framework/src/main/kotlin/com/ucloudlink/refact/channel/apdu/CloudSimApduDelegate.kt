package com.ucloudlink.refact.channel.apdu

import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.channel.apdu.ApduData
import com.ucloudlink.refact.channel.apdu.ApduDelegate
import com.ucloudlink.refact.business.Requestor
import rx.Single
import rx.lang.kotlin.single
import java.util.concurrent.TimeUnit

/**
 * Created by wangliang on 2016/9/7.
 */
class CloudSimApduDelegate {

    /*override fun apdu(reqData: ApduData): Single<ApduData> {
        return single<ApduData> { sub ->
            /*Requestor.requestApdu(reqData).subscribe({
                sub.onSuccess(ApduData(reqData.imsi, it as ByteArray))
            }, {
                loge("requestApdu error:${it.message}")
                it.printStackTrace()
                sub.onError(it)
            })
        }.timeout(12, TimeUnit.SECONDS) // wlmark 超时后，依然会发送apdu指令，需要修改？*/
    }*/

}
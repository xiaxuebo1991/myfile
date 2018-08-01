package com.ucloudlink.refact.business.uploadsocketok

import com.ucloudlink.framework.protocol.protobuf.Cloudsim_socket_ok_req
import com.ucloudlink.framework.protocol.protobuf.Cloudsim_socket_ok_rsp
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.StateMessageId
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import rx.Subscription

/**
 * Created by haiping.liu on 2018/5/18.
 * 上报云卡socket建立,触发流量更新
 */
class UploadSocketOkTask {
    private var subscription: Subscription? = null
    fun uploadSocketOk() {
        val imsi = ServiceManager.accessEntry.accessState.imis
        val imei = Configuration.getImei(ServiceManager.appContext)
        val req = Cloudsim_socket_ok_req(imsi, imei, 2)
        JLog.logd("req=${req}")

        if (imsi == null) {
            return
        }
        subscription = Requestor.requestCloudsimSocketOK(req, 15)
                .retry(2)
                .subscribe({
                    subscription?.unsubscribe()
                    if (it is Cloudsim_socket_ok_rsp) {
                        JLog.logd("success errorCode=${it.errorCode}")
                        ServiceManager.accessEntry.accessState.sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD)
                    } else {
                        JLog.logd("fail1:$it")
                    }
                }, {
                    JLog.logd("fail2:$it")
                })
    }

    fun unSubscription(){
        subscription?.unsubscribe()
    }
}
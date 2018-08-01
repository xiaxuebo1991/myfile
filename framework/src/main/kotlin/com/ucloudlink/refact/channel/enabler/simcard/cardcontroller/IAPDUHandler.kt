package com.ucloudlink.refact.channel.enabler.simcard.cardcontroller

import com.ucloudlink.refact.channel.enabler.datas.Card
import rx.Single
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by jiaming.liang on 2018/1/5.
 * initApduEnv()
 * getApduResponse
 * addAuthListen
 * clearApdu
 */
enum class AuthState {
    AUTH_BEGIN, AUTH_SUCCESS, AUTH_FAIL
}

abstract class IAPDUHandler {
    val SUCCESS = 0
    val listens = ConcurrentHashMap<String, (AuthState) -> Unit>()
    var listenKeyIndex: Int = 0

    /**
     * @param card
     * @return 返回处理结果
     */
    open fun initAPDUEnv(card: Card): Int {
        return SUCCESS
    }

    /**
     * 获取apdu 响应
     */
    open fun getAPDUResponse(card: Card, request: APDU): Single<APDU> {
        return Single.create { /*it.onSuccess(response)*/ }
    }

    /**
     * 增加监听回调，返回一个回调的key 用于停止监听
     */
    open fun addAuthListen(listen: (AuthState) -> Unit): String {
        val key = "listen${listenKeyIndex++}"
        listens.put(key, listen)
        return key
    }

    /**
     * 移除监听回调
     * @param listenKey 监听回调的key
     */
    open fun removeAuthListen(listenKey: String) {
        listens.remove(listenKey)
    }

    open fun onAuthStateChange(state: AuthState) {
        listens.forEach {
            it.value.invoke(state)
        }
    }
}



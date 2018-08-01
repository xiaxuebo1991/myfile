package com.ucloudlink.refact.business.softsim

import android.text.TextUtils
import com.ucloudlink.framework.remoteuim.CardException
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.framework.util.ByteUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import okio.Okio
import rx.Observable
import java.io.File

/**
 * 卡仓库
 * 维护本地卡片数据库， 和远程下载更新卡镜像
 */
object CardRepository {
    fun cardExists(imsi: String): Boolean {
        return false
    }

    init {

    }

    fun storeCloudCard(imsi: String, buf: ByteArray): Observable<Any> {
        return Observable.create({
            sub ->
            val fileName = Configuration.simDataDir + imsi + ".bin"
            val file = File(fileName)
            val writer = Okio.buffer(Okio.sink(file))
            writer.write(buf)
            writer.flush()

            val ret = SoftSimNative.addCard(imsi)

            if (ret == SoftSimNative.E_SOFTSIM_SUCCESS || ret == SoftSimNative.E_SOFTSIM_CARD_EXIST) {
                sub.onNext("success")
                sub.onCompleted()
            } else {
                sub.onError(Exception("parse error ret =$ret"))
            }
        })
    }

    fun storeSoftCard(card: Card): Observable<Any> {
        return Observable.defer {
            Observable.create<Any>({
                sub ->
                if (card.ki != null && card.opc != null) {
                    val kiByte = ByteUtils.hexStringToByte(card.ki)
                    val opcByte = ByteUtils.hexStringToByte(card.opc)
                    val msisdnByte = if (card.msisdn == null) null else ByteUtils.hexStringToByte(card.msisdn)
                    val iccid = if (card.iccId == null) "8944502101169533700" else card.iccId
                    if (TextUtils.isEmpty(card.imageId)) {
                        sub.onError(Exception("card.imageid==null"))
                        return@create
                    }
                    val ret = SoftSimNative.addCard(card.imsi, card.imageId!!, iccid!!, msisdnByte, kiByte, opcByte)
                    logd("SoftSimNative.addCard ret:$ret")
                    if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
                        sub.onNext("success")
                        sub.onCompleted()
                    } else {
                        sub.onError(CardException("parse error"))
                    }
                } else {
                    sub.onError(CardException("not a softcard"))
                }
            })
        }
    }

    fun downloadCard(imsi: String): Observable<Card> {
        throw UnsupportedOperationException()
    }

    /**
     * 获取卡
     */
    fun fetchSoftCard(card: Card): Boolean {
        val ret = SoftSimNative.queryCard(card.imsi)
        logd("fetchSoftCard ret:$ret")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            return true
        } else {
            return if (card.ki != null && card.opc != null && !TextUtils.isEmpty(card.imageId)) {
                val kiByte = ByteUtils.hexStringToByte(card.ki)
                val opcByte = ByteUtils.hexStringToByte(card.opc)
                val msisdnByte = if (card.msisdn == null) null else ByteUtils.hexStringToByte(card.msisdn)
                val iccid = if (card.iccId == null) "8944502101169533700" else card.iccId
                //软卡在位则不进行add
                if (ServiceManager.seedCardEnabler.getCard().cardType === CardType.SOFTSIM) {
                    logd("addCard while SOFTSIM");
                    return false
                }
                val ret = SoftSimNative.addCard(card.imsi, card.imageId!!, iccid!!, msisdnByte, kiByte, opcByte)
                logd("SoftSimNative.addCard ret:$ret")
                ret == SoftSimNative.E_SOFTSIM_SUCCESS
            } else {
                loge("card:${card.ki} - ${card.opc} - ${card.imageId}")
                false
            }
        }
    }

    fun deleteCard(imsi:String){
        var ret = SoftSimNative.queryCard(imsi)
        logd("query card $imsi return $ret")
        if(ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            ret = SoftSimNative.deleteCard(imsi)
        }
    }
}




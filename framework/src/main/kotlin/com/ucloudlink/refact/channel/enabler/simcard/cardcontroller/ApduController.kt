package com.ucloudlink.refact.channel.enabler.simcard.cardcontroller


import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardModel
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.MccTypeMap
import com.ucloudlink.refact.utils.HexUtil
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv
import com.ucloudlink.refact.utils.toHex
import org.codehaus.jackson.util.ByteArrayBuilder
import rx.Single
import rx.lang.kotlin.subscribeWith

/**
 * Created by zhangxian on 2018/1/11.
 */
class ApduController : IAPDUHandler() {
    private var vsimDirEHPLMN: Boolean = false
    private var vsimDirMSISDN: Boolean = false
    private var vsimDirAD: Boolean = false
    private var softsimDirAD: Boolean = false
    private var softsimDirPSLOCI: Boolean = false
    private var vsimDirHPLMNwAcT: Boolean = false
    private var softsimDirFPLMN: Boolean = false
    private var softsimDirOPLMN: Boolean = false
    private var softsimDirHPLMNwAct: Boolean = false

    private val SERVER_RSP: Int = 1
    private val NATIVE_RSP: Int = 2
    private val CHANGE_RSP: Int = 3
    private val SERVER_REQ: Int = 4
    private val NATIVE_REQ: Int = 5
    private val CHANGE_REQ: Int = 6

    private var ehplmnBuff: ByteArray? = null
//    private var mMccTypeMap: HashMap<Int, Int> = HashMap<Int, Int>()

    //private val APDU_INVALID = "apdu response invalid"

    override fun initAPDUEnv(card: Card): Int {
        initCardEplmnList(card)
        return super.initAPDUEnv(card)
    }

    override fun getAPDUResponse(card: Card, cmd: APDU): Single<APDU> {
        return Single.create { sub ->

            if (isAuthReq(cmd) && card.cardType == CardType.VSIM) {
                onAuthStateChange(AuthState.AUTH_BEGIN)
                printApduCmd(card, cmd.req.toHex(), SERVER_REQ)
                AuthController.authHandler(card, cmd).subscribeWith {
                    onSuccess {
                        printApduCmd(card, cmd.rsp.toHex(), SERVER_RSP)
                        cmd.isSucc = true
                        sub.onSuccess(it)
                    }
                    onError {
                        if (it.message == AuthController.APDU_INVALID) {
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_APDU_INVALID)
                        }
                        onAuthStateChange(AuthState.AUTH_FAIL)
                        sub.onError(Exception("authHandler exception"))
                    }
                }
            } else {
                printApduCmd(card, cmd.req.toHex(), NATIVE_REQ)
                val ret = apduNativeHandler(card, cmd)
                if (ret) {
                    cmd.isSucc = true
                    sub.onSuccess(cmd)
                } else {
                    sub.onError(Exception("apduNativeHandler exception"))
                }
            }
        }
    }

    fun isAuthReq(cmd: APDU): Boolean {
        if (cmd.req[1] == 0x88.toByte()) {
            cmd.isAuth = true
            return true
        } else {
            return false
        }
    }

    private fun needCallNative(card: Card, cmd: APDU): Boolean {
        if ((vsimDirMSISDN && (cmd.req.get(1) == 0xB2.toByte()) && (card.cardType == CardType.VSIM))) {
            val buider: ByteArrayBuilder = ByteArrayBuilder()
            var i: Int = 0

            while (i < cmd.req.get(4).toInt()) {
                buider.append(0xFF)
                i++
            }
            buider.append(0x90)
            buider.append(0x00)

            cmd.rsp = buider.toByteArray()
            logv("vsimDirMSISDN:${cmd.rsp.toHex()}")
            return false
        }
        return true
    }

    private fun apduNativeHandler(card: Card, cmd: APDU): Boolean {
        val nativeOutRsp = ByteArray(512)
        val rspLen = IntArray(1)

        saveEfDir(card, cmd.req)

        if (needCallNative(card, cmd)) {
            val ret = SoftSimNative.apdu(card.vslot, cmd.req, nativeOutRsp, rspLen)
            if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
                cmd.rsp = nativeOutRsp.copyOfRange(0, rspLen[0])
                printApduCmd(card, cmd.rsp.toHex(), NATIVE_RSP)
                changeApduRsp(card, cmd)
                return true
            } else {
                logv("modem_apdu rsp failed:VSIM,${card.slot},$ret")
                return false
            }
        } else {
            return true
        }
    }

    private fun saveEfDir(card: Card, request: ByteArray) {
        if ((request[1] == 0xA4.toByte()) && (card.cardType == CardType.VSIM)) {
            vsimDirEHPLMN = (request[request.lastIndex - 1] == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0xD9.toByte())

            vsimDirMSISDN = (request[request.lastIndex - 1] == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0x40.toByte())

            vsimDirAD = (request[request.lastIndex - 1] == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0xAD.toByte())

            vsimDirHPLMNwAcT = (request[request.lastIndex - 1] == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0x62.toByte())

        }
        if ((request[1] == 0xA4.toByte()) && (card.cardType == CardType.SOFTSIM)) {
            softsimDirAD = (request[request.lastIndex - 1] == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0xAD.toByte())

            softsimDirPSLOCI = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0x73.toByte())

            softsimDirFPLMN = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0x7B.toByte())

            softsimDirOPLMN = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0x61.toByte())

            softsimDirHPLMNwAct = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request[request.lastIndex] == 0x62.toByte())
        }
    }

    private fun changeApduRsp(card: Card, apdu: APDU) {
        val temp_mnc_numeric: Int
        val request = apdu.req
        var rspApdu = apdu.rsp

        if (vsimDirEHPLMN && (ehplmnBuff != null) && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.VSIM)) {
            val builder: ByteArrayBuilder = ByteArrayBuilder()
            var i: Int = 0
            var j: Int = 0

            while (i < request[4].toInt()) {
                if (i < ehplmnBuff!!.size) {
                    builder.append(ehplmnBuff!![i].toInt())
                    i++
                } else {
                    if ((rspApdu[j] != 0xFF.toByte()) && (rspApdu[j + 1] != 0xFF.toByte()) &&
                            (rspApdu.get(j + 2) != 0xFF.toByte())) {
                        logv("rspApdu:" + rspApdu.toHex().substring(j * 2, j * 2 + 6) + "ehplmnBuff:" + ehplmnBuff!!.toHex())
                        if (!ehplmnBuff!!.toHex().contains(rspApdu.toHex().substring(j * 2, j * 2 + 6), true)) {
                            builder.append(rspApdu[j].toInt())
                            builder.append(rspApdu[j + 1].toInt())
                            builder.append(rspApdu[j + 2].toInt())
                            i += 3
                        }
                    } else {
                        builder.append(0xFF)
                        builder.append(0xFF)
                        builder.append(0xFF)
                        i += 3
                    }
                    j += 3
                }
            }
            builder.append(0x90)
            builder.append(0x00)

            rspApdu = builder.toByteArray()
            printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
        } else if ((vsimDirAD && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.VSIM))) {
            temp_mnc_numeric = rspApdu.get(index = 3).toInt()
            if (temp_mnc_numeric == 2 || temp_mnc_numeric == 3) {
                Configuration.vsim_mnc_numeric = temp_mnc_numeric
            }
            logv("vsim_mnc_numeric:" + Configuration.vsim_mnc_numeric + " temp_mnc_numeric:" + temp_mnc_numeric)
        } else if ((softsimDirAD && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {
            temp_mnc_numeric = rspApdu[3].toInt()
            if (temp_mnc_numeric == 2 || temp_mnc_numeric == 3) {
                Configuration.softsim_mnc_numeric = temp_mnc_numeric
            }
            logv("softsim_mnc_numeric:" + Configuration.softsim_mnc_numeric + " temp_mnc_numeric:" + temp_mnc_numeric)
        } else if ((softsimDirPSLOCI && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {
            if (rspApdu[7] ==
                    0x64.toByte() && rspApdu[8] == 0xF0.toByte() && rspApdu[9] == 0x0.toByte()) {
                rspApdu[9] = 0x10
            }
            printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
        } else if ((softsimDirFPLMN && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {

            val fplmn = card.fplmn
            if (fplmn != null) {

                var index = 0
                val size = rspApdu.size

                fplmn.forEach {
                    var plmn = it
                    if (plmn.length == 5 || plmn.length == 6) {

                        if (index + 3 >= size) {
                            return@forEach
                        }

                        if (plmn.length == 5) {
                            plmn += "f"
                        }

                        rspApdu[index++] = (plmn[1].parseByte() * 0x10 + plmn[0].parseByte()).toByte()
                        rspApdu[index++] = (plmn[5].parseByte() * 0x10 + plmn[2].parseByte()).toByte()
                        rspApdu[index++] = (plmn[4].parseByte() * 0x10 + plmn[3].parseByte()).toByte()

                    }

                }

                printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
            }
            printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
        } else if ((softsimDirOPLMN && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {
            val oplmn = card.preferredPlmn
            if (oplmn != null) {

                var index = 0
                val size = rspApdu.size

                oplmn.forEach {
                    var plmn = it
                    if (plmn.length == 5 || plmn.length == 6) {

                        if (index + 3 >= size) {
                            return@forEach
                        }

                        if (plmn.length == 5) {
                            plmn += "f"
                        }

                        rspApdu[index++] = (plmn[1].parseByte() * 0x10 + plmn[0].parseByte()).toByte()
                        rspApdu[index++] = (plmn[5].parseByte() * 0x10 + plmn[2].parseByte()).toByte()
                        rspApdu[index++] = (plmn[4].parseByte() * 0x10 + plmn[3].parseByte()).toByte()

                    }

                }

                printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
            }
            printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
        }else if ((vsimDirHPLMNwAcT && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.VSIM))) {
            var number = 0

            while (number < request[4].toInt()) {
                if (rspApdu[number] == 0xff.toByte()) {
                    logv("HPLMNwAcT break")
                    break
                }
                if (rspApdu[number + 3].toInt() and 0x40 != 0x40) {
                    rspApdu[number + 3] = (rspApdu[number + 3].toInt() or 0x40).toByte()
                }
                number += 5
            }
            printApduCmd(card, rspApdu.toHex(), CHANGE_RSP)
        }/*else if((softsimDirHPLMNwAct && (request[1] == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))){
            val apduTemp = byteArrayOf(0x32,0xF4.toByte(),0x05,0x80.toByte(),0x00,0x32,0xF4.toByte(),0x05,0x00,0x80.toByte(),0x90.toByte(),0x00)
            rspApdu = apduTemp
            logd("softsimDirHPLMNwAct apduTemp:" + apduTemp + "rspApdu:" + rspApdu)
        }*/
        apdu.rsp = rspApdu
    }

    /*
    把0...9，a...f转化为 byte
     */
    fun Char.parseByte(): Byte {
        if (this in '0'..'9') {
            return (this - 48).toByte()
        }
        if (this in 'A'..'F') {
            return (this - 55).toByte()
        }
        if (this in 'a'..'f') {
            return (this - 87).toByte()
        }
        return this.toByte()
    }

    private fun initCardEplmnList(card: Card): Int {
        val ehplmnBuilder: ByteArrayBuilder = ByteArrayBuilder()
        val uplmnBuilder: ByteArrayBuilder = ByteArrayBuilder()
        val rplmnBuilder: ByteArrayBuilder = ByteArrayBuilder()
        var ret: Int = 0
        var rat1: Int
        var rat2: Int

        ehplmnBuff = null
        card.eplmnrat = 0
        val eplmnlist = card.eplmnlist

        if (eplmnlist == null) {
            JLog.loge("card.eplmnlist.size is null! ")
            return -1
        }

        logd("setCardEplmnList: mcc${card.imsi.substring(0, 3)}, eplmnlist:$eplmnlist")

        for (i in eplmnlist.indices) {
            rat1 = 0
            rat2 = 0

            if (eplmnlist[i].supportedRat == 0) {
                continue
            }

            card.eplmnrat = eplmnlist[i].supportedRat or card.eplmnrat
            logd("setCardEplmnList card.eplmnrat:" + card.eplmnrat)

            if (rplmnBuilder.toByteArray().isEmpty()) {
                if (eplmnlist[i].plmn.length == 5) {
                    rplmnBuilder.append((eplmnlist[i].plmn[0].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[1].toInt() - '0'.toInt()) * 0x10)
                    rplmnBuilder.append((eplmnlist[i].plmn[2].toInt() - '0'.toInt()) + 0xf0)
                    rplmnBuilder.append((eplmnlist[i].plmn[3].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[4].toInt() - '0'.toInt()) * 0x10)
                } else if (eplmnlist[i].plmn.length == 6) {
                    rplmnBuilder.append((eplmnlist[i].plmn[0].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[1].toInt() - '0'.toInt()) * 0x10)
                    rplmnBuilder.append((eplmnlist[i].plmn[2].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[5].toInt() - '0'.toInt()) * 0x10)
                    rplmnBuilder.append((eplmnlist[i].plmn[3].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[4].toInt() - '0'.toInt()) * 0x10)
                } else {
                    logd("setCardRplmnList: length err")
                    continue
                }
            }

            logd("mcc1 len:" + card.imsi.substring(0, 3).toInt() + ",mcc2 len:" +
                    eplmnlist[i].plmn.substring(0, 3).toInt() + ",mcc1:" +
                    MccTypeMap[card.imsi.substring(0, 3)] + ",mcc2:" +
                    MccTypeMap[eplmnlist[i].plmn.substring(0, 3)])

            if (card.imsi.substring(0, 3) == eplmnlist[i].plmn.substring(0, 3) ||
                    (MccTypeMap[card.imsi.substring(0, 3)] == MccTypeMap[eplmnlist[i].plmn.substring(0, 3)] &&
                            MccTypeMap[card.imsi.substring(0, 3)] != null)) {
                if (eplmnlist[i].plmn.length == 5) {
                    ehplmnBuilder.append((eplmnlist[i].plmn[0].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[1].toInt() - '0'.toInt()) * 0x10)
                    ehplmnBuilder.append((eplmnlist[i].plmn[2].toInt() - '0'.toInt()) + 0xf0)
                    ehplmnBuilder.append((eplmnlist[i].plmn[3].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[4].toInt() - '0'.toInt()) * 0x10)
                } else if (eplmnlist[i].plmn.length == 6) {
                    ehplmnBuilder.append((eplmnlist[i].plmn[0].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[1].toInt() - '0'.toInt()) * 0x10)
                    ehplmnBuilder.append((eplmnlist[i].plmn[2].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[5].toInt() - '0'.toInt()) * 0x10)
                    ehplmnBuilder.append((eplmnlist[i].plmn[3].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[4].toInt() - '0'.toInt()) * 0x10)
                } else {
                    logd("setCardEplmnList: length err")
                    continue
                }
            } else {
                if (eplmnlist[i].plmn.length == 5) {
                    uplmnBuilder.append((eplmnlist[i].plmn[0].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[1].toInt() - '0'.toInt()) * 0x10)
                    uplmnBuilder.append((eplmnlist[i].plmn[2].toInt() - '0'.toInt()) + 0xf0)
                    uplmnBuilder.append((eplmnlist[i].plmn[3].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[4].toInt() - '0'.toInt()) * 0x10)
                } else if (eplmnlist[i].plmn.length == 6) {
                    uplmnBuilder.append((eplmnlist[i].plmn[0].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[1].toInt() - '0'.toInt()) * 0x10)
                    uplmnBuilder.append((eplmnlist[i].plmn[2].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[5].toInt() - '0'.toInt()) * 0x10)
                    uplmnBuilder.append((eplmnlist[i].plmn[3].toInt() - '0'.toInt()) + (eplmnlist[i].plmn[4].toInt() - '0'.toInt()) * 0x10)
                } else {
                    logd("setCarduplmnList: length err")
                    continue
                }
                //RAT_CDMA_RADIO          --0
                //RAT_HDR_RADIO            --1
                //RAT_GSM_RADIO            --2
                //RAT_WCDMA_RADIO         --3
                //RAT_LTE_RADIO             --4
                //RAT_TDS_RADIO             --5
                if ((eplmnlist[i].supportedRat and 0x04) == 0x04) {
                    rat2 = rat2 or 0x80
                }
                if ((eplmnlist[i].supportedRat and 0x01) == 0x01 ||
                        (eplmnlist[i].supportedRat and 0x02) == 0x02 ||
                        (eplmnlist[i].supportedRat and 0x08) == 0x08 ||
                        (eplmnlist[i].supportedRat and 0x20) == 0x20) {
                    rat1 = rat1 or 0x80
                }
                if ((eplmnlist[i].supportedRat and 0x10) == 0x10) {
                    rat1 = rat1 or 0x40
                }
                logd("ehplmnByteArray rat1 $rat1")
                logd("ehplmnByteArray rat2 $rat2")
                uplmnBuilder.append(rat1)
                uplmnBuilder.append(rat2)
            }
        }

        val ehplmnByteArray = ehplmnBuilder.toByteArray()
        val uplmnByteArray = uplmnBuilder.toByteArray()

        logd("ehplmnByteArray size ${ehplmnByteArray.size} uplmnByteArray size ${uplmnByteArray.size}")
        if (ehplmnByteArray.isNotEmpty()) {
            ret = setCardEHplmnCache(ehplmnByteArray)
        }

        if (rplmnBuilder.toByteArray().isNotEmpty()) {
            ret += setCardRplmn(card, rplmnBuilder.toByteArray())
        }

        if (uplmnByteArray.isNotEmpty()) {
            ret += setCardUplmn(card.vslot, uplmnByteArray)
        }

        return ret
    }

    private fun getCardType(card: Card): Int {
        val builder: ByteArrayBuilder = ByteArrayBuilder()


        builder.append(0x00)
        builder.append(0xa4)
        builder.append(0x00)//6f7e
        builder.append(0x04)
        builder.append(0x02)
        builder.append(0x3f)
        builder.append(0x00)

        val request = builder.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(280)

        logd("getCardType: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("getCardType return ${HexUtil.encodeHexStr(rsp)}, ${rspLen[0]}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if ((rsp[0] == 0x90.toByte()) || (rsp[0] == 0x61.toByte())) {
                card.cardModel = CardModel.UICC
                logd("getCardType: UICC!!!!!")
                return 0
            } else if (rsp[0] == 0x6E.toByte()) {
                card.cardModel = CardModel.GSM
                logd("getCardType: GSM!!!!!")
                return 0
            } else {
                JLog.loge("getCardType:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set getCardType failed! ret:" + ret)
            return -1
        }
    }

    private fun setCardUplmn(vslot: Int, plmn: ByteArray): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb0)//6F60
        buider.append(0x00)
        buider.append(plmn.size)

        for (x in plmn) {
            buider.append(x.toInt())
        }

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(16)

        logd("setCardUplmn:" + vslot + " + request: " + HexUtil.encodeHexStr(request))
        val ret = SoftSimNative.apdu(vslot, request, rsp, rspLen)
        logd("setCardUplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen[0]}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                logd("setCardUplmn: success!!!!!")
                return 0
            } else {
                JLog.loge("setCardUplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set setCardUplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setUiccCardLoci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb4)//6f7e
        buider.append(0x00)
        buider.append(0x0B)

        while (i++ < 4) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(index = 0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                JLog.loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setUiccCardPsloci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb3)//6F73
        buider.append(0x00)
        buider.append(0x0E)

        while (i++ < 7) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen[0]}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                JLog.loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setUiccCardEpsloci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb5)//6FE3
        buider.append(0x00)
        buider.append(0x12)

        while (i++ < 12) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(index = 0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                JLog.loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setGsmCardLoci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb3)//6F7E
        buider.append(0x00)
        buider.append(0x0B)

        while (i++ < 4) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen[0]}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                JLog.loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setGsmCardLocigprs(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb4)//6F53
        buider.append(0x00)
        buider.append(0x0E)

        while (i++ < 7) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logv("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logv("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen[0]}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                JLog.loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            JLog.loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setCardRplmn(card: Card, plmn: ByteArray): Int {
        //getCardType(card)
        card.cardModel = CardModel.UICC
        if (card.cardModel == CardModel.UICC) {
            setUiccCardLoci(card, plmn)
            setUiccCardPsloci(card, plmn)
            setUiccCardEpsloci(card, plmn)
        } else if (card.cardModel == CardModel.GSM) {
            setGsmCardLoci(card, plmn)
            setGsmCardLocigprs(card, plmn)
        }
        return 0
    }

    fun setCardEHplmnCache(plmn: ByteArray): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()

        logv("setCardEHplmnCache len: ${plmn.size}")
        try {
            for (x in plmn) {
                buider.append(x.toInt())
            }
            ehplmnBuff = buider.toByteArray()
            logv("setCardEHplmnCache: + buf: ${HexUtil.encodeHexStr(ehplmnBuff)}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    fun pintfCardType(type: CardType): String {
        when (type) {
            CardType.VSIM -> {
                return "VSIM"
            }
            CardType.SOFTSIM -> {
                return "SOFTSIM"
            }
            CardType.PHYSICALSIM -> {
                return "PHYSICALSIM"
            }
            else -> {
                return "ELSE"
            }
        }
    }

    fun pintfCmdType(type: Int): String {
        when (type) {
            NATIVE_RSP -> {
                return "NATIVE_RSP"
            }
            SERVER_RSP -> {
                return "SERVER_RSP"
            }
            CHANGE_RSP -> {
                return "CHANGE_RSP"
            }
            NATIVE_REQ -> {
                return "NATIVE_REQ"
            }
            SERVER_REQ -> {
                return "SERVER_REQ"
            }
            CHANGE_REQ -> {
                return "CHANGE_REQ"
            }
            else -> {
                return "ELSE_TYPE"
            }
        }
    }

    fun printApduCmd(card: Card, rsp: String, flag: Int) {
        logv("modem_apdu ${pintfCmdType(flag)}:${pintfCardType(card.cardType)},${card.slot},$rsp")
    }
}

package com.ucloudlink.refact.utils

import android.content.Context
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logi
import java.util.*

/**
 * Created by jiaming.liang on 2016/7/19.
 */
val HEX_CHARS = "0123456789ABCDEF".toCharArray()

inline fun ByteArray.toHex(): String {
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
    }
    return result.toString()
}

inline fun ByteArray.printString(): String {
    val result = StringBuffer()
    forEach {
        result.append(it.toString() + ",")
    }
    return result.toString()
}

inline fun apnSpPutString(context: Context, key: String, value: String): Boolean {
    val sharedPreferences = context.getSharedPreferences("apn_record", 0)
    val edit = sharedPreferences.edit()
    edit.putString(key, value)
    return edit.commit()
}

inline fun decodeApns(apnStr: String, defaultNumeric: String): List<Apn>? {
    val apnParams = apnStr.split(",");

    var apns = ArrayList<Apn>();
    if (apnParams.size <= 1) {
        return null;
    }
    //APN格式:APN,用户名,密码,身份验证类型(NULL 0 PAP 1 CHAP 2 PAPorCHAP 3),APN类型(default supl ia),APN协议(IP,IPV6,IPV4V6),APN漫游协议(IP,IPV6,IPV4V6),主DNS,副DNS
    //3gwap,,,3,default,IP,,,,3gnet,,,3,ia,IPV4V6,,,
    //一组apn数据8个逗号，所以apn组数可以按下面的方法算出来
//    val cNum = CharMatcher.is(',').countIn(apnStr);
    val cNum = apnParams.size - 1;
    logd("cNum:$cNum")
    var apnNum = (cNum + 1) / 10;
    var oldApnMode = false;
    if (cNum < 8) {//兼容G1的apn数据模式
//            Timber.d("decodeApn: " + cNum + " , " + apnParams.length);
        oldApnMode = true;
        apnNum = 1;
    }
    for (i in 0..apnNum - 1) {
//    for (int i = 0; i < apnNum; i++) {
        val apn = apnParams[0 + i * 10]
        if (apn.equals("")) {
            continue
        }
        val user = apnParams[1 + i * 10]
        val password = apnParams[2 + i * 10]
        var authtype: Int
        try {
            authtype = Integer.parseInt(apnParams[3 + i * 10])
        } catch (e: NumberFormatException) {
            authtype = 0
        }
        var type = apnParams[4 + i * 10]
        if (type.equals("")) {
            type = "default"
        }
        val protocol = apnParams[5 + i * 10]
        var roaming_protocol = ""
        if (cNum >= 6) {
            roaming_protocol = apnParams[6 + i * 10]
        }
        var numeric = defaultNumeric
        var mcc = ""
        var mnc = ""
        if (!oldApnMode) {
            numeric = apnParams[9 + i * 10];
        }
        if (numeric.length == 5 || numeric.length == 6) {
            mcc = numeric.substring(0, 3);
            mnc = numeric.substring(3, numeric.length);
        }
        val name = "VSim_Apn_" + i;
        val apnObj = Apn(type, name, numeric, mcc, mnc, apn, protocol, authtype.toString(), user, roaming_protocol, password, "0");
        apns.add(apnObj);
//            Timber.d("decoded apn: " + i + "," + apnNum + " : " + apn);

    }
    logi("apns form server:$apns");
    return apns;
}

inline fun nullToString(x: Any?): String {
    x ?: return ""
    return x.toString()
}

inline fun <T> Array<T>.printContent(): String {
    val sb = StringBuilder()
    this.forEachIndexed { Index, any ->

        sb.append(any.toString())
        if (Index != this.size - 1) {
            sb.append(",")
        }
    }
    return sb.toString()
}
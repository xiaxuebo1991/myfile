package com.ucloudlink.refact.channel.enabler.simcard.ApnSetting

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.ucloudlink.framework.util.APN_TYPE_DEFAULT
import com.ucloudlink.framework.util.APN_TYPE_DUN
import com.ucloudlink.framework.util.ApnUtil
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logke
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import java.util.*

/**
 * Created by jiaming.liang on 2016/7/15.
 */

class ApnSetting(val context: Context) {

    val cslDefaultApn: Apn = Apn(type = APN_TYPE_DEFAULT)
    val cslDefaultApn21: Apn = Apn(numeric = "45421", mcc = "454", mnc = "21", type = APN_TYPE_DEFAULT)
    val cslDefaultApnJT: Apn = Apn(numeric = "23450", mcc = "234", mnc = "50", apn = "jtm2m", type = APN_TYPE_DEFAULT)
    val dashiDefaultApn: Apn = Apn(numeric = "23420", mcc = "234", mnc = "20", apn = "dashi.cloudsim-internet", type = APN_TYPE_DEFAULT)

    val defaultApns = arrayOf(cslDefaultApn, dashiDefaultApn, cslDefaultApn21, cslDefaultApnJT)

    fun setUcApnSetting(card: Card, apnType: String) {
        if (Objects.equals(apnType, APN_TYPE_DEFAULT)) {
            if (CardType.SOFTSIM == card.cardType) {
                val mccmnc = card.numeric
                var temp_apn: Apn = Apn()
                if(card.apn != null){
                    logd("do setUcApnSetting apn" + card.apn)
                    card.apn!!.forEach {
                        if (it.numeric == mccmnc) {
                            temp_apn = it
                            if(temp_apn.type.equals("default")){
                                temp_apn.type = "default,supl"
                            }
                            return@forEach
                        }
                    }
                }else{
                    logd("do setUcApnSetting defaultApns" + defaultApns)
                    defaultApns.forEach {
                        if (it.numeric == mccmnc) {
                            temp_apn = it
                            return@forEach
                        }
                    }
                }

                ApnUtil.resetApnBySubId(context, temp_apn, card.subId)
            } else {
                //fixme 2016年11月22日 应在此保证物理种子卡有一首选apn
            }
        } else {//获取种子的首选apn 注入dun设置
            var dunString = ""
            if (card.imsi.length == 15) {//先从配置表中获取
                val mncmcc = card.numeric
                dunString= SharedPreferencesUtils.getString(context,"dunConfig",mncmcc,"")
                
            }
            //清除之前记录，避免记录了一个不能用dunstr
            Configuration.temp_dunStr=""
            Configuration.temp_dun_numericStr=""
            //获取不到从首选卡中获取
            if (TextUtils.isEmpty(dunString)) {
                val temp_dun_apn = ApnUtil.getPreferredApn(context, card.subId)
                logd("temp_dun_apn:$temp_dun_apn")
                if (temp_dun_apn != null) {
                    dunString = apnToStringWithDun(temp_dun_apn)
                    logd("apnStr from PreferredApn :$dunString")
                    Configuration.temp_dunStr=dunString
                    Configuration.temp_dun_numericStr=temp_dun_apn.numeric
                }
            }
            //设置dun
            if (!TextUtils.isEmpty(dunString)) {
                logd("apnStr:$dunString")
                val resolver = ServiceManager.appContext.contentResolver
                Settings.Global.putString(resolver, Settings.Global.TETHER_DUN_APN, dunString)
            } else {
                logke("temp_dun_apn==null maybe phycard did not select a preferred apn")
            }
        }
    }

//    private fun changeToApn(ucapn: UcApn?): Apn? {
//        if (ucapn != null) {
//            val apn = Apn()
//            apn.type = ucapn.type ?: ""
//            apn.numeric = ucapn.numeric ?: "12345"
//            apn.apn = ucapn.apn ?: ""
//            apn.profile_id = 0
//            val mcc = apn.numeric.subSequence(0, 3)
//            val mnc = ucapn.numeric?.length?.let { apn.numeric.subSequence(3, it) }
//            apn.mcc = mcc.toString()
//            apn.mnc = mnc.toString()
//
//            return apn
//        }
//        return null
//    }

    private fun apnToStringWithDun(apn: Apn): String {
        val sb = StringBuilder()
        sb.append(apn.name).append(",")
                .append(apn.apn).append(",")
                .append(apn.proxy).append(",")
                .append(apn.port).append(",")
                .append(apn.user ?: "").append(",")
                .append(apn.password ?: "").append(",")
                .append("").append(",")//server
                .append(apn.mmsc).append(",")
                .append(apn.mmsproxy).append(",")
                .append(apn.mmsport).append(",")
                .append(apn.mcc).append(",")
                .append(apn.mnc).append(",")
                .append(apn.authtype).append(",")
                .append("dun")

        return sb.toString()
    }


}

//APN格式:
// APN(非空),apn
// 用户名,user
// 密码,password
// 身份验证类型(NULL 0 PAP 1 CHAP 2 PAPorCHAP 3),authtype
// APN类型(default supl ia),type
// APN协议(IP,IPV6,IPV4V6), protocol
// APN漫游协议(IP,IPV6,IPV4V6),   roaming_protocol
// 主DNS,
// 副DNS 
//带- 标识表示为apn数据表的约束索引
data class Apn(
        var type: String = APN_TYPE_DUN, //-
        var name: String = "softCard", //-
        var numeric: String = "45419", //-
        var mcc: String = "454", //-
        var mnc: String = "19", //-
        var apn: String = "21vn", //-
        var protocol: String? = null, //
        var authtype: String? = "0", //
        var user: String? = null, //
        var roaming_protocol: String? = null, //
        var password: String? = null, //
        var profile_id: String? = ""//-
        //以上是服务器有配置到的apn//
        , var proxy: String = ""//-
        , var port: String = ""//-
        , var mmsproxy: String = ""//-
        , var mmsport: String = ""//-
        , var mmsc: String = ""//-
        , var carrier_enabled: String = ""//-
        , var bearer: String = ""//-
        , var mvno_type: String = ""//-
        , var mvno_match_data: String = ""//-


) {
    override fun toString(): String {
        return "Apn(type='$type', name='$name', numeric='$numeric', mcc='$mcc', mnc='$mnc', apn='$apn', protocol=$protocol, authtype=$authtype, user=$user, roaming_protocol=$roaming_protocol, password=$password, profile_id=$profile_id, proxy='$proxy', port='$port', mmsproxy='$mmsproxy', mmsport='$mmsport', mmsc='$mmsc', carrier_enabled='$carrier_enabled', bearer='$bearer', mvno_type='$mvno_type', mvno_match_data='$mvno_match_data')"
    }
}

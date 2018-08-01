package com.ucloudlink.refact.business.virtimei

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.SystemProperties
import android.telephony.TelephonyManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv

/**
 * Created by haiping.liu on 2017/1/9.
 */

object VirtImeiHelper {

    //保存虚拟imei到SharedPreferences
    fun  saveImeiToShareprefence( ctx: Context) {
        var preferences: SharedPreferences = ctx.getSharedPreferences("deviceImei", MODE_PRIVATE)
        var editor = preferences.edit()
        var imei0_SP = preferences.getString("ril.imei0", "")
        var imei1_SP = preferences.getString("ril.imei1", "")
        logd("virtImei", " SP中imei:  imei0_SP=" + imei0_SP + ";;imei1_SP=" + imei1_SP)
        var imei0 = SystemProperties.get("ril.imei0", "")
        var imei1 = SystemProperties.get("ril.imei1", "")
        logd("virtImei", " 系统属性中imei:  ril.imei0=" + imei0 + ";;ril.imei1=" + imei1);
        //当 SystemProperties 中没有 imei时，保存读取的 imei，否则不保存
        if (imei0_SP.equals("") && imei1_SP.equals("")) {
            editor.putString("ril.imei0", imei0);
            editor.putString("ril.imei1", imei1);
            editor.commit();
            logd("virtImei", "第一次保存imei :  ril.imei0=" + imei0 + ";;ril.imei1=" + imei1);
        }else{
            logd("virtImei", "已经保存过imei");
        }
    }

    //获取SharedPreferences中imei
    fun  getImeiFromeShareprefence(ctx: Context, slot:Int):String {
        var preferences: SharedPreferences = ctx.getSharedPreferences("deviceImei", MODE_PRIVATE)
        var imei0_SP = preferences.getString("ril.imei0", "")
        var imei1_SP = preferences.getString("ril.imei1", "")
        logd("virtImei", " getImeiFromeShareprefence:  imei0_SP=" + imei0_SP + ";;imei1_SP=" + imei1_SP+";slot="+slot)
        var imei = ""
        if (checkValidImei(imei0_SP) || checkValidImei(imei1_SP)){
            var tempImei = if (slot ==0) imei0_SP else imei1_SP
            if (checkValidImei(tempImei)){
                 imei =tempImei
            }else{
                imei = if (checkValidImei(imei0_SP)) imei0_SP else imei1_SP
            }
        }
        logd("virtImei", " getImeiFromeShareprefence:  imei=" + imei)
        return imei
    }


    //写入虚拟imei(写入前检查imei合法性)
    fun wirteVirtImei(ctx: Context, virtImei: String): Boolean {
        try{
             var NeedStuff:Boolean = false
             var isWirteVirtImeiSuccess:Boolean = false
             val te = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
             NeedStuff = IsNeedStuff(virtImei)
             logv("virtImei: NeedStuff:" +NeedStuff)
             if(NeedStuff == true){
                 var virtImei_stuff = StuffImei(virtImei)
                 logd("virtImei","NeedStuffImei is :" +virtImei_stuff)
                 isWirteVirtImeiSuccess = te.nvWriteItem(550, virtImei_stuff, Configuration.cloudSimSlot)
             }else{
                 isWirteVirtImeiSuccess = te.nvWriteItem(550, virtImei, Configuration.cloudSimSlot)
             }
             //var isWirteVirtImeiSuccess = te.nvWriteItem(550, virtImei, Configuration.cloudSimSlot)
             logv("virtImei: 写入虚拟imei=${virtImei},结果 : isWirteVirtImeiSuccess= " + isWirteVirtImeiSuccess + ";solt=" + Configuration.cloudSimSlot)
             return isWirteVirtImeiSuccess
        }
        catch(e:NoSuchMethodError){

        }
        return true
    }
    //清除moden侧 imei缓存
    fun recoveryImei(ctx: Context): Boolean? {
        try{
             val te = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
             val isRecoveryImei0Sucess = te.nvWriteItem(550, "", 0)
             val isRecoveryImei1Sucess = te.nvWriteItem(550, "", 1)
             logv("virtImei: 清除虚拟imei结果:" + (isRecoveryImei0Sucess && isRecoveryImei1Sucess) )
              return isRecoveryImei0Sucess && isRecoveryImei1Sucess
        }catch(e:NoSuchMethodError){

        }
        return true
    }

     fun isAllzero(imei: String):Boolean {
         if(((imei.length == 1)&&(imei=="0"))
         ||((imei.length == 2)&&(imei=="00"))
         ||((imei.length == 3)&&(imei=="000"))
         ||((imei.length == 4)&&(imei=="0000"))
         ||((imei.length == 5)&&(imei=="00000"))
         ||((imei.length == 6)&&(imei=="000000"))
         ||((imei.length == 7)&&(imei=="0000000"))
         ||((imei.length == 8)&&(imei=="00000000"))
         ||((imei.length == 9)&&(imei=="000000000"))
         ||((imei.length == 10)&&(imei=="0000000000"))
         ||((imei.length == 11)&&(imei=="00000000000"))
         ||((imei.length == 12)&&(imei=="000000000000"))
         ||((imei.length == 13)&&(imei=="0000000000000"))
         ||((imei.length == 14)&&(imei=="00000000000000"))
         ||((imei.length == 15)&&(imei=="000000000000000"))){
             return true
         }else{
            return false
         }
     }

     //checkimei
     fun checkValidImei(imei: String?): Boolean {
        if (imei == null) {
            return false
        }
        logd("virtImei","virtImei length is: " + imei.length );
        if (imei.length > 15){
            return false
        }
        if(isAllzero(imei) == true){
            return false
        }

        for (c in imei.toCharArray()) {
          if (c < '0' || c > '9') {
                return false
             }
        }
        return true
     }

//stuff imei
     fun  StuffImei(imei:String):String {
          var str:String = "0"
          var i:Int = 0
          if (imei is String && (imei.length < 15)) {
              str = imei
               for (i in  1..(15 - imei.length)) {
                     str = "0" + str
               }
           }
    logd("virtImei","stuff imei result:" + str );
          return str
    }

    fun IsNeedStuff(imei:String):Boolean {
        if (imei.length < 15){
            return true
        }else{
            return false
        }
    }
}
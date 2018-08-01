package com.ucloudlink.framework.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.config.VSIM_APN_PREFIX
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.nullToString
import io.netty.util.internal.StringUtil

/**
 * Created by jiaming.liang on 2016/7/15.
 */

class UcApn {
    var type: String? = null
    var numeric: String? = null
    var apn: String? = null
    var profile_id: String? = null
}

object ApnUtil {

    val CARRIERS_TABLE_URI = Telephony.Carriers.CONTENT_URI

    val PREFERRED_APN_URI = Uri.parse("content://telephony/carriers/preferapn");
    val PREFERRED_NO_UPDATE_APN_URI = Uri.parse("content://telephony/carriers/preferapn_no_update")

    var SET_PREFERRED_APN_URI: Uri = PREFERRED_APN_URI


//    fun apnQuery(resolver: ContentResolver, apn: Apn):Cursor? {
//        val selection = "type=?,name=?,numeric=?,mcc=?,mnc=?,apn=?,protocol=?,authtype=?,user=?,roaming_protocol=?,password=?,profile_id=?"
//        val selectionArgs = arrayOf<String>(apn.type,
//                apn.name,
//                apn.numeric,
//                apn.mcc,
//                apn.mnc,
//                apn.apn,
//                nullToString(apn.protocol),
//                nullToString(apn.authtype),
//                nullToString(apn.user),
//                nullToString(apn.roaming_protocol),
//                nullToString(apn.password),
//                nullToString(apn.profile_id)
//        )
//        val cursor = resolver.query(CARRIERS_TABLE_URI, arrayOf("_id"), selection, selectionArgs, null)
//        return cursor
//    }

    fun isExists(resolver: ContentResolver, apn: Apn): String? {
        synchronized(this) {
            val selection = "type=?,name=?,numeric=?,mcc=?,mnc=?,apn=?,protocol=?,authtype=?,user=?,roaming_protocol=?,password=?,profile_id=?"
            val selectionArgs = arrayOf<String>(apn.type,
                    apn.name,
                    apn.numeric,
                    apn.mcc,
                    apn.mnc,
                    apn.apn,
                    if(StringUtil.isNullOrEmpty(apn.protocol)) "IPV4V6" else apn.protocol!!,
                    nullToString(apn.authtype),
                    nullToString(apn.user),
                    if(StringUtil.isNullOrEmpty(apn.roaming_protocol)) "IPV4V6" else apn.roaming_protocol!! ,
                    nullToString(apn.password),
                    nullToString(apn.profile_id)
            )
            val cursor = resolver.query(CARRIERS_TABLE_URI, arrayOf("_id"), selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val apnId = cursor.getString(0)
                cursor.close()
                return apnId
            }
            cursor?.close()
        }
        return null

    }

    fun addNewApn(resolver: ContentResolver, cv: ContentValues): String {
        val inserted = resolver.insert(Telephony.Carriers.CONTENT_URI, cv)
        logd("insert result-->$inserted")
        var newApnId = ContentUris.parseId(inserted)
        return newApnId.toString()
    }

    fun resetApnBySubId(context: Context, apn: Apn, subId: Int) {
        logd("do resetCurrApn $apn subid:$subId")
//        val uri1 = getUri(PREFERRED_APN_URI, subId)
        val resolver = context.contentResolver
        var apnId = isExists(resolver, apn)
        if (apnId == null) {
            val cv = getApnContentValues(apn)
            logd("copyDunApn->$apn")
            apnId = addNewApn(resolver, cv)
            logd(" resetApnBySubId ->addNewApn $apnId")
        } else {
            logd("curr apn id $apnId")
        }
        selectApnBySubId(context, apnId.toString(), subId, true)
        //有首选，并且首选不是目标，查找当前
    }

    private fun getApnContentValues(apn: Apn): ContentValues {
        val cv = ContentValues()
        cv.put(Telephony.Carriers.NUMERIC, apn.numeric)
        cv.put(Telephony.Carriers.MCC, apn.mcc)
        cv.put(Telephony.Carriers.MNC, apn.mnc)
        cv.put(Telephony.Carriers.APN, apn.apn)
        cv.put(Telephony.Carriers.TYPE, apn.type)
        cv.put(Telephony.Carriers.AUTH_TYPE, apn.authtype)
        cv.put(Telephony.Carriers.USER, apn.user)
        cv.put(Telephony.Carriers.PASSWORD, apn.password)
        cv.put(Telephony.Carriers.NAME, apn.name)
        cv.put("profile_id", apn.profile_id)
        cv.put(Telephony.Carriers.ROAMING_PROTOCOL, if(StringUtil.isNullOrEmpty(apn.roaming_protocol)) "IPV4V6" else apn.roaming_protocol)
        cv.put(Telephony.Carriers.PROTOCOL, if(StringUtil.isNullOrEmpty(apn.protocol)) "IPV4V6" else apn.protocol)
        return cv
    }

    fun InsertCloudSimApnIfNeed(context: Context, apn: Apn, subId: Int): String {
        logd("do InsertCloudSimApnIfNeed $apn subid:$subId")
//        val uri1 = getUri(PREFERRED_APN_URI, subId)
        val resolver = context.contentResolver
        var _id = iscloudsimApnOK(resolver, apn)
        logd("InsertCloudSimApnIfNeed->$_id")
        if (_id == null) {
            val cv = getApnContentValues(apn)
            logd("InsertCloudSimApnIfNeed->$apn")
            _id = addNewApn(resolver, cv).toString()
            logd(" resetApnBySubId ->addNewApn $_id")
        }
        return _id
    }

    fun InsertApnToDatabaseIfNeed(context: Context, apn: Apn):String{
        try {
            logd("do InsertApnToDatabaseIfNeed $apn ")
            val resolver = context.contentResolver
            var _id = iscloudsimApnOK(resolver, apn)
            logd("InsertApnToDatabaseIfNeed->$_id")
            if (_id == null) {
                val cv = getApnContentValues(apn)
                logd("InsertApnToDatabaseIfNeed->$apn")
                _id = addNewApn(resolver, cv)
                logd(" InsertApnToDatabaseIfNeed ->addNewApn $_id")
            }
            return _id
        } catch (e: Exception) {
            loge("InsertApnToDatabaseIfNeed failed: $e")
            return ""
        }
    }

    //检查有没有相同type numeric apn的apn,如果没有,返回null,如果有,返回对应_id
    fun iscloudsimApnOK(resolver: ContentResolver, apnFormService: Apn): String? {
//        val selection = "type=?,name=?,numeric=?,mcc=?,mnc=?,apn=?,protocol=?,authtype=?,user=?,roaming_protocol=?,password=?,profile_id=?"
        val selection = "type like ? and numeric=? and apn=?"
        val selectionArgs = arrayOf<String>(
                "%${apnFormService.type}%",
                //                apn.name,
                apnFormService.numeric,
                //                apnFormService.mcc,
//                apnFormService.mnc,
                apnFormService.apn
//               ,nullToString(apn.protocol),
//                nullToString(apn.authtype),
//                nullToString(apn.user),
//                nullToString(apn.roaming_protocol),
//                nullToString(apn.password),
//                nullToString(apn.profile_id)
        )
        synchronized(this) {
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(CARRIERS_TABLE_URI, arrayOf("_id"), selection, selectionArgs, null)
                if (cursor == null || cursor.count == 0) {
                    cursor?.close()
                    return null
                } else {
                    cursor.moveToFirst()
                    val id = cursor.getString(cursor.getColumnIndex("_id"))
                    cursor.close()
                    return id
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            } finally {
                cursor?.close()
            }
        }

    }

    fun getAuthInfoByCursor(cursor: Cursor): Apn {
        val ucApn = Apn()
        ucApn.type = cursor.getString(0)?:"default"
        ucApn.name = cursor.getString(1)?:""
        ucApn.numeric = cursor.getString(2)?:""
        ucApn.mcc = cursor.getString(3)?:""
        ucApn.mnc = cursor.getString(4)?:""
        ucApn.apn = cursor.getString(5)?:""
        ucApn.protocol = cursor.getString(6)?:"IPV4V6"
        ucApn.authtype = cursor.getString(7)?:"0"
        ucApn.user = cursor.getString(8)?:""
        ucApn.roaming_protocol = cursor.getString(9)?:"IPV4V6"
        ucApn.password = cursor.getString(10)?:""
        ucApn.profile_id = cursor.getString(11)?:""
        ucApn.proxy = cursor.getString(12)?:""
        ucApn.port = cursor.getString(13)?:""
        ucApn.mmsproxy = cursor.getString(14)?:""
        ucApn.mmsport = cursor.getString(15)?:""
        ucApn.mmsc = cursor.getString(16)?:""
        ucApn.carrier_enabled = cursor.getString(17)?:""
        ucApn.bearer = cursor.getString(18)?:""
        ucApn.mvno_type = cursor.getString(19)?:""
        ucApn.mvno_match_data = cursor.getString(20)?:""
        return ucApn
    }

    fun getPreferredApn(context: Context, subId: Int): Apn? {
        synchronized(this) {
            val resolver = context.contentResolver
            //这里与获取数据转换apn对象有顺序相关性，修改时请注意
            val prejection = arrayOf<String>(
                    Telephony.Carriers.TYPE
                    , Telephony.Carriers.NAME
                    , Telephony.Carriers.NUMERIC
                    , Telephony.Carriers.MCC
                    , Telephony.Carriers.MNC
                    , Telephony.Carriers.APN
                    , Telephony.Carriers.PROTOCOL
                    , Telephony.Carriers.AUTH_TYPE
                    , Telephony.Carriers.USER
                    , Telephony.Carriers.ROAMING_PROTOCOL
                    , Telephony.Carriers.PASSWORD
                    , "profile_id"
                    , Telephony.Carriers.PROXY
                    , Telephony.Carriers.PORT
                    , Telephony.Carriers.MMSPROXY
                    , Telephony.Carriers.MMSPORT
                    , Telephony.Carriers.MMSC
                    , Telephony.Carriers.CARRIER_ENABLED
                    , Telephony.Carriers.BEARER
                    , Telephony.Carriers.MVNO_TYPE
                    , Telephony.Carriers.MVNO_MATCH_DATA
            )
            val cursor = resolver.query(
                    getUri(SET_PREFERRED_APN_URI, subId), prejection, null, null, null)
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst()
                val preferredApn = getAuthInfoByCursor(cursor)
                cursor.close()
                return preferredApn
            }
            cursor?.close()
        }
        return null
    }

    fun selectApnBySubId(context: Context, id: String, subId: Int, isNeedUpdata: Boolean = false) {
        var uri = getUri(SET_PREFERRED_APN_URI, subId)
        if (!isNeedUpdata) {
            uri = getUri(PREFERRED_NO_UPDATE_APN_URI, subId)
        }
        logd("selectApnBySubId: uri:$uri")
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put("apn_id", id)
        var updateCount: Int = 0
        try {
            updateCount = resolver.update(uri, values, null, null)
        } catch(e: Exception) {
            e.printStackTrace()
        }
        logd("APNUtil selectApnById apn_id:$id,updateCount:$updateCount")
    }

    private fun getUri(uri: Uri, subId: Int): Uri {
        return Uri.withAppendedPath(uri, "subId/" + subId)
    }


    fun clearApnByNumeric(context: Context, numeric: String) {
        val SELECT_BY_REFERENCE = "numeric=? AND name like ?";

        val cr = context.contentResolver
        val count = cr.delete(CARRIERS_TABLE_URI, SELECT_BY_REFERENCE, arrayOf(numeric, "%${VSIM_APN_PREFIX}%"))
        logd("clearApnByNumeric numeric:$numeric  count:$count")
    }

    fun setSPValue(context: Context, key: String, value: String): Boolean {
        val sharedPreferences = context.getSharedPreferences("apn_record", 0)
        val edit = sharedPreferences.edit()
        edit.putString(key, value)
        return edit.commit()
    }


    fun getDefaultApnFromList(apns:List<Apn>?): Apn?{
        if (apns == null) {
            logd("server not provided for apn");
            return null
        }
        val setApn = run {
            var secondApn: Apn? = null
            apns.forEach {
                if (it.type.contains(APN_TYPE_DEFAULT)) {
                    return@run it
                }
                if (secondApn == null) {
                    secondApn = it
                }
            }
            return@run secondApn
        }
        return setApn
    }
}

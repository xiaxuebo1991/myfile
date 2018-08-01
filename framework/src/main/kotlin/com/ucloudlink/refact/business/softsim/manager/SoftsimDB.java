package com.ucloudlink.refact.business.softsim.manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;
import com.ucloudlink.refact.business.softsim.struct.UserOrderInfo;
import com.ucloudlink.refact.utils.JLog;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shiqianhua on 2016/12/6.
 */

public class SoftsimDB extends SQLiteOpenHelper {
    private              String  TAG              = "SoftsimDB";
//    private static       boolean mainTmpDirSet    = false;
    private static final int     DATABASE_VERSION = 3;

    private String usersimDbTable   = "usersim";// TODO: 2017/5/10 数据库加密
    private String softsimDbTable   = "softsim";
    private String softsimBinTable  = "binFile";
    private String softsimFlowTable = "softsimFlowStat";

    public SoftsimDB(Context ctx) {
        super(ctx, "SoftsimDB.db", null, DATABASE_VERSION);
        JLog.logd(TAG, "SoftsimDB: start softsim db");
    }

//    @Override
//    public SQLiteDatabase getReadableDatabase() {
//        if (!mainTmpDirSet) {
//            String tmpDir = "/data/data/" + ServiceManager.appContext.getPackageName() + "/databases/main";
//            boolean rs = new File(tmpDir).mkdir();
//            JLog.logd("getReadableDatabase, tmp dir create:" + rs + "," + tmpDir);
//            super.getReadableDatabase().execSQL("PRAGMA temp_store_directory = '" + tmpDir + "'");
//            mainTmpDirSet = true;
//            return super.getReadableDatabase();
//        }
//        return super.getReadableDatabase();
//    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        JLog.logd(TAG, "onCreate: start to create table!1" + db.getPath());
        try {
            db.execSQL("create table if not exists " + usersimDbTable + "(" + "id integer primary key autoincrement," + "username varchar," + "orderid varchar," + "value text);");
        } catch (SQLException e) {
            e.printStackTrace();
            JLog.loge(TAG, "create " + usersimDbTable + " failed:" + e.getMessage());
        }

        try {
            db.execSQL("create table if not exists " + softsimDbTable + "(" + "imsi varchar primary key," + "value text);");
        } catch (SQLException e) {
            e.printStackTrace();
            JLog.loge(TAG, "create " + softsimDbTable + " failed:" + e.getMessage());
        }

        try {
            db.execSQL("create table if not exists " + softsimBinTable + "(" + "str varchar primary key," + "type int," + "value blob);");
        } catch (SQLException e) {
            e.printStackTrace();
            JLog.loge(TAG, "create " + softsimBinTable + " failed:" + e.getMessage());
        }

        try {
            db.execSQL("create table if not exists " + softsimFlowTable + "(" + "id integer primary key autoincrement," + "value text);");
        } catch (SQLException e) {
            e.printStackTrace();
            JLog.loge(TAG, "create " + softsimFlowTable + " failed:" + e.getMessage());
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        JLog.logd(TAG, "onUpgrade: sqlite3 upgrade" + oldVersion + " -> " + newVersion);
        if (oldVersion < 3 && newVersion >= 3) {
            ArrayList<UserOrderInfo> infoList = new ArrayList<>();
            ContentValues values = new ContentValues();

            Cursor cursor = db.rawQuery("select * from " + usersimDbTable, null);
            if (cursor.moveToNext()) {
                String json = cursor.getString(cursor.getColumnIndex("value"));
                UserOrderInfo info = parseFromJsonUserOrderInfo(json);
                infoList.add(info);
            } else {
                JLog.logd(TAG, "nothing in " + usersimDbTable);
            }

            db.execSQL("drop table " + usersimDbTable);

            db.execSQL("create table if not exists " + usersimDbTable + "(" + "id integer primary key autoincrement," + "username varchar," + "orderid varchar," + "value text);");
            for (UserOrderInfo info : infoList) {
                if (info.getOrderList() != null) {
                    for (OrderInfo order : info.getOrderList()) {
                        values.put("username", info.getUsername());
                        values.put("orderid", order.getOrderId());
                        values.put("value", getOrderInfoJson(order));
                        db.insert(usersimDbTable, null, values);
                    }
                }
            }
        }
    }

    public void quit() {

    }

    interface iterHandler {
        int callSingleObj(Object param, String idx, Object o);
    }

    public void iterAllOrderList(iterHandler handler, Object param) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("select * from " + usersimDbTable, null);
            while (cursor.moveToNext()) {
                String username = cursor.getString(cursor.getColumnIndex("username"));
                String orderid = cursor.getString(cursor.getColumnIndex("orderid"));
                String json = cursor.getString(cursor.getColumnIndex("value"));
                int ret = handler.callSingleObj(param, username, parseFromJsonOrderInfo(json));
                JLog.logd(TAG, "iterAllOrderList: callSingleObj " + username + " ret:" + ret);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
    }

    public void iterAllSoftsimList(iterHandler handler, Object param) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("select * from " + softsimDbTable, null);
            while (cursor.moveToNext()) {
                String imsi = cursor.getString(cursor.getColumnIndex("imsi"));
                String json = cursor.getString(cursor.getColumnIndex("value"));
                int ret = handler.callSingleObj(param, imsi, parseFromJsonToSoftsimInfo(json));
                JLog.logd(TAG, "iterAllOrderList: callSingleObj " + imsi + " ret:" + ret);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
    }

    public void iterAllBinList(iterHandler handler, Object param) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("select * from " + softsimBinTable, null);
            while (cursor.moveToNext()) {
                String str = cursor.getString(cursor.getColumnIndex("str"));
                byte[] result = cursor.getBlob(cursor.getColumnIndex("value"));
                int ret = handler.callSingleObj(param, str, result);
                JLog.logd(TAG, "iterAllOrderList: callSingleObj " + str + " ret:" + ret);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
    }

    public void updateSoftsimInfo(SoftsimLocalInfo softsimLocalInfo) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {softsimLocalInfo.getImsi()};
        ContentValues values = new ContentValues();
        Cursor cursor = null;

        try {
            cursor = db.query(softsimDbTable, null, "imsi=?", args, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                //add
                values.put("imsi", softsimLocalInfo.getImsi());
                values.put("value", genSoftsimInfoJson(softsimLocalInfo));
                db.insert(softsimDbTable, null, values);
            } else {
                // update
                values.put("value", genSoftsimInfoJson(softsimLocalInfo));
                db.update(softsimDbTable, values, "imsi=?", args);
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
    }

    public SoftsimLocalInfo getSoftsimInfoByImsi(String imsi) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {imsi};
        Cursor cursor = null;

        try {
            cursor = db.query(softsimDbTable, null, "imsi=?", args, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                String json = cursor.getString(cursor.getColumnIndex("value"));
                return parseFromJsonToSoftsimInfo(json);
            } else {
                JLog.logd(TAG, "getSoftsimInfoByImsi: cannot find imsi info" + imsi);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }

        return null;
    }

    public void delSoftsimInfo(String imsi) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {imsi};
        try {
            db.delete(softsimDbTable, "imsi=", args);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void updateUserOrderInfo(String username, OrderInfo orderInfo) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {username, orderInfo.getOrderId()};
        ContentValues values = new ContentValues();
        Cursor cursor = null;

        try {
            cursor = db.query(usersimDbTable, null, "username=? and orderid=? ", args, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                //add
                values.put("username", username);
                values.put("orderid", orderInfo.getOrderId());
                values.put("value", getOrderInfoJson(orderInfo));
                long insert = db.insert(usersimDbTable, null, values);
                JLog.logd("updateUserOrderInfo", "" + insert);
            } else {
                // update
                values.put("value", getOrderInfoJson(orderInfo));
                int update = db.update(usersimDbTable, values, "username=? and orderid=?", args);
                JLog.logd("updateUserOrderInfo", "" + update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
    }

    public ArrayList<OrderInfo> getUserOrderInfoListByUsername(String username) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {username};
        ArrayList<OrderInfo> infoList = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = db.query(usersimDbTable, null, "username=?", args, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String json = cursor.getString(cursor.getColumnIndex("value"));
                    OrderInfo info = parseFromJsonOrderInfo(json);
                    infoList.add(info);
                }
                return infoList;
            } else {
                JLog.logd(TAG, "getUserOrderInfoByUsername: cannot find " + username);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }

        return null;
    }

    public OrderInfo getOrderInfoByUsernameOrderid(String username, String orderId) {
        //SQLiteDatabase db = getWritableDatabase();

        SQLiteDatabase db = null;
        try {
            db = getReadableDatabase();
        } catch (Exception e) {
            e.printStackTrace();
            if (db != null) {
                try {
                    db.close();
                    db = null;
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }

        }
        if (db == null)
            return null;

        String[] args = {username, orderId};
        Cursor cursor = null;

        try {
            cursor = db.query(usersimDbTable, null, "username=? and orderid=?", args, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                String json = cursor.getString(cursor.getColumnIndex("value"));
                return parseFromJsonOrderInfo(json);
            } else {
                JLog.logd(TAG, "getOrderInfoByUsernameOrderid: cannot find " + username);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }

        return null;
    }

    public void delUserOrderInfo(String username, String orderid) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {username, orderid};
        try {
            db.delete(usersimDbTable, "username=? and orderid=?", args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void delAllUserOrderInfo(String username) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {username};
        try {
            db.delete(usersimDbTable, "username=?", args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] getSoftsimBinByRef(String str) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {str};
        Cursor cursor = null;

        try {
            cursor = db.query(softsimBinTable, null, "str=?", args, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                byte[] result = cursor.getBlob(cursor.getColumnIndex("value"));
                return result;
            } else {
                JLog.logd(TAG, "getSoftsimBinByRef: cannot find bin" + str);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }

        return null;
    }

    public boolean isSoftsimBinRefIn(String str) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {str};
        Cursor cursor = null;

        try {
            cursor = db.query(softsimBinTable, null, "str=?", args, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
        return false;
    }

    public void updateSoftsimBinData(String str, int type, byte[] data) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {str};
        ContentValues values = new ContentValues();
        Cursor cursor = null;
        values.put("str", str);
        values.put("type", type);
        values.put("value", data);
        String s = new String(data);
        Log.d(TAG, "updateSoftsimBinData: value:" + s + " str:" + str);
        try {
            cursor = db.query(softsimBinTable, null, "str=?", args, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                db.insert(softsimBinTable, null, values);
            } else {
                int i = db.update(softsimBinTable, values, "str=?", args);
                JLog.logd(TAG, "bin data " + str + "already in! update:" + i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeCursor(cursor);
        }
    }

    private String genSoftsimInfoJson(SoftsimLocalInfo softsimLocalInfo) {
        Gson gson = new Gson();

        return gson.toJson(softsimLocalInfo);
    }

    private SoftsimLocalInfo parseFromJsonToSoftsimInfo(String json) {
        Log.d(TAG, "parseFromJsonToSoftsimInfo: " + json);
        try {
            return new Gson().fromJson(json, SoftsimLocalInfo.class);
        } catch (JsonSyntaxException e) {
            // 兼容老的格式
            e.printStackTrace();
            Pattern r = Pattern.compile("\\\"date\\\":\\\".+?\\\"");
            Matcher m = r.matcher(json);
            String result = m.replaceAll("\\\"date\\\":0");
            Log.d(TAG, "parseFromJsonToSoftsimInfo: result:" + result);

            SoftsimLocalInfo info = new Gson().fromJson(result, SoftsimLocalInfo.class);
            updateSoftsimInfo(info);
            return info;
        }
    }

    //    private String genUserOrderInfoJson(UserOrderInfo orderInfo){
    //        return new Gson().toJson(orderInfo);
    //    }
    //
    private UserOrderInfo parseFromJsonUserOrderInfo(String json) {
        return new Gson().fromJson(json, UserOrderInfo.class);
    }

    private OrderInfo parseFromJsonOrderInfo(String json) {
        return new Gson().fromJson(json, OrderInfo.class);
    }

    private String getOrderInfoJson(OrderInfo orderInfo) {
        return new Gson().toJson(orderInfo);
    }

    private <T> ArrayList<T> fromJsonList(String json, Class<T> cls) {
        ArrayList<T> mList = new ArrayList<T>();
        JsonArray array = new JsonParser().parse(json).getAsJsonArray();
        for (final JsonElement elem : array) {
            mList.add(new Gson().fromJson(elem, cls));
        }
        return mList;
    }

    //    public void addSoftsimFlowInfo(SoftsimFlowStateInfo info){
    //        SQLiteDatabase db = getWritableDatabase();
    //        ContentValues values = new ContentValues();
    //        String json;
    //
    //        try {
    //            json = new Gson().toJson(info, SoftsimFlowStateInfo.class);
    //            values.put("value", json);
    //            db.insert(softsimFlowTable, null, values);
    //        }catch (Exception e){
    //            e.printStackTrace();
    //        }
    //    }
    //
    //    public ArrayList getFirstSoftsimFlowInfo(){
    //        ArrayList result = new ArrayList();
    //        SQLiteDatabase db = getWritableDatabase();
    //        String text;
    //
    //        try {
    //            Cursor cursor = db.rawQuery("select * from " + softsimFlowTable + " limit 1;", null);
    //            if (cursor == null) {
    //                return null;
    //            } else {
    //                cursor.moveToFirst();
    //                result.add(cursor.getInt(cursor.getColumnIndex("id")));
    //                text = cursor.getString(cursor.getColumnIndex("value"));
    //                SoftsimFlowStateInfo info = new Gson().fromJson(text, SoftsimFlowStateInfo.class);
    //                result.add(info);
    //                cursor.close();
    //                return result;
    //            }
    //        }catch (Exception e){
    //            e.printStackTrace();
    //        }
    //        return null;
    //    }

    public void addSoftsimFlowInfo(SoftsimFlowStateInfo info) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        String json;

        JLog.logi("SCFlowLog: addSoftsimFlowInfo  " + (info == null ? "null" : info.toString()));

        json = new Gson().toJson(info, SoftsimFlowStateInfo.class);
        values.put("value", json);
        db.insert(softsimFlowTable, null, values);
    }

    public ArrayList getFirstSoftsimFlowInfo() {
        ArrayList result = new ArrayList();
        SQLiteDatabase db = null;
        String text;
        try {
            db = getReadableDatabase();
        } catch (Exception e) {
            if (db != null) {
                db.close();
                db = null;
            }
        }
        if (db == null) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select * from " + softsimFlowTable + " limit 1;", null);

            if (cursor == null || cursor.getCount() <= 0) {
                JLog.logi("SCFlowLog: getFirstSoftsimFlowInfo  cursor == null || cursor.getCount() <= 0");
                return null;
            } else {
                cursor.moveToFirst();
                result.add(cursor.getInt(cursor.getColumnIndex("id")));
                text = cursor.getString(cursor.getColumnIndex("value"));
                SoftsimFlowStateInfo info = new Gson().fromJson(text, SoftsimFlowStateInfo.class);
                JLog.logi("SCFlowLog: getFirstSoftsimFlowInfo  -info: " + (info == null ? "null" : info.toString()));
                result.add(info);
                return result;
            }
        } catch (Exception e) {

        } finally {
            closeCursor(cursor);
        }

        return null;
    }

    private void closeCursor(Cursor cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception e) {

            }
        }
    }

    public void delSoftsimFlowInfoById(int id) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {"" + id};

        try {
            db.delete(softsimFlowTable, "id=?", args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

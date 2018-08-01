package com.ucloudlink.refact.product.mifi;

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
import com.ucloudlink.framework.protocol.protobuf.SoftsimInfo;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;
import com.ucloudlink.refact.utils.JLog;

import java.io.File;
import java.util.ArrayList;

import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * Created by shiqianhua on 2016/12/6.
 */
public class ExtSoftsimDB extends SQLiteOpenHelper {
    private              String  TAG                = "ExtSoftsimDB";
//    private static       boolean mainTmpDirSet      = false;
    private static final int     DATABASE_VERSION   = 3;
    private              String  extSoftsimDbTable  = "extsoftsim";
    private              String  extSoftsimBinTable = "extbinFile";

    //不要修改数据位置，会影响数据备份
    public static final String DATABASE_NAME = "ExtSoftsimDB.db";

    public static boolean isDBExist(Context context) {
        File file = context.getDatabasePath(DATABASE_NAME);
        return file.exists();
    }

    public ExtSoftsimDB(Context ctx) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        JLog.logd(TAG, "ExtSoftsimDB: start extsoftsim db");
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
            db.execSQL("create table if not exists " + extSoftsimDbTable + "(" + "imsi varchar primary key," + "value text);");
        } catch (SQLException e) {
            e.printStackTrace();
            JLog.loge(TAG, "create " + extSoftsimDbTable + " failed:" + e.getMessage());
        }

        try {
            db.execSQL("create table if not exists " + extSoftsimBinTable + "(" + "str varchar primary key," + "type int," + "value blob);");
        } catch (SQLException e) {
            e.printStackTrace();
            JLog.loge(TAG, "create " + extSoftsimBinTable + " failed:" + e.getMessage());
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        JLog.logd(TAG, "onUpgrade: sqlite3 upgrade" + oldVersion + " -> " + newVersion);
        if (oldVersion < 3 && newVersion >= 3) {
            //            ArrayList<UserOrderInfo> infoList = new ArrayList<>();
            //            ContentValues values = new ContentValues();
            //
            //            Cursor cursor = db.rawQuery("select * from " + usersimDbTable, null);
            //            if(cursor.moveToNext()){
            //                String json = cursor.getString(cursor.getColumnIndex("value"));
            //                UserOrderInfo info = parseFromJsonUserOrderInfo(json);
            //                infoList.add(info);
            //            }else{
            //                JLog.logd(TAG, "nothing in " + usersimDbTable);
            //            }
            //
            //            db.execSQL("drop table " + usersimDbTable);
            //
            //            db.execSQL("create table if not exists " + usersimDbTable + "(" +
            //                    "id integer primary key autoincrement," +
            //                    "username varchar," +
            //                    "orderid varchar," +
            //                    "value text);");
            //            for(UserOrderInfo info: infoList){
            //                if(info.getOrderList() != null){
            //                    for(OrderInfo order:info.getOrderList()){
            //                        values.put("username", info.getUsername());
            //                        values.put("orderid", order.getOrderId());
            //                        values.put("value", getOrderInfoJson(order));
            //                        db.insert(usersimDbTable, null, values);
            //                    }
            //                }
            //            }
        }
    }

    public void quit() {

    }

    interface iterHandler {
        int callSingleObj(Object param, String idx, Object o);
    }

    //更新软卡数据库，加卡、更新同一个方法
    public Boolean updataExtSoftsimDb(SoftsimInfo softsimInfo) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {softsimInfo.imsi.toString()};
        ContentValues values = new ContentValues();
        values.put("imsi", softsimInfo.imsi);
        values.put("value", getExtSoftsimInfoJson(softsimInfo));

        Cursor cursor = null;

        try {
            cursor = db.query(extSoftsimDbTable, null, "imsi=?", args, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                long insert = db.insert(extSoftsimDbTable, null, values);
                Log.d(TAG, "updataExtSoftsimDb: insert:" + insert);
                return insert > 0;
            } else {
                int update = db.update(extSoftsimDbTable, values, "imsi=?", args);
                JLog.logd(TAG, "softsimInfo " + softsimInfo.imsi + " already in! do update :update");
                return update > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            closeCursor(cursor);
        }
    }

    public SoftsimInfo getSimInfoByImsi(String imsi) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select * from " + extSoftsimDbTable + " where imsi=?", new String[]{imsi});
            int count = cursor.getCount();
            if (count > 1) {
                Log.e(TAG, "getSimInfoByImsi: too much count : " + count);
            }
            while (cursor.moveToNext()) {
                String json = cursor.getString(cursor.getColumnIndex("value"));
                logv("json == " + json);
                return parseFromJsonToSoftsimInfo(json);
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            closeCursor(cursor);
        }
    }

    //查询所有软卡
    public ArrayList<SoftsimInfo> getAllExtSoftsim() {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;
        ArrayList<SoftsimInfo> softsimList = new ArrayList<SoftsimInfo>();
        try {
            cursor = db.rawQuery("select * from " + extSoftsimDbTable, null);
            while (cursor.moveToNext()) {
                //                String imsi = cursor.getString(cursor.getColumnIndex("imsi"));
                String json = cursor.getString(cursor.getColumnIndex("value"));
                logv("json == " + json);
                softsimList.add(parseFromJsonToSoftsimInfo(json));
                //                int ret = handler.callSingleObj(param, imsi, parseFromJsonToSoftsimInfo(json));
                //                JLog.logd(TAG, "iterAllOrderList: callSingleObj " + imsi + " ret:" + ret);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JLog.logk("e == " + e);
        } finally {
            closeCursor(cursor);
        }
        return softsimList;
    }

    public void iterAllBinList(iterHandler handler, Object param) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("select * from " + extSoftsimBinTable, null);
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

    public void delSoftsimInfo(String imsi) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {imsi};
        try {
            int delete = db.delete(extSoftsimDbTable, "imsi=", args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void delSoftsimInfo(String[] imsis) {
        SQLiteDatabase db = getWritableDatabase();
        if (imsis.length > 0) {
            StringBuffer whereClause = new StringBuffer("imsi=?");

            for (int i = 0; i < imsis.length; i++) {
                if (i != imsis.length - 1) {
                    whereClause.append(" or imsi=?");
                }
            }
            logv(whereClause.toString());
            int delete = db.delete(extSoftsimDbTable, whereClause.toString(), imsis);
        }
    }

    public byte[] getExtSoftsimBinByRef(String str) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {str};
        Cursor cursor = null;

        try {
            cursor = db.query(extSoftsimBinTable, null, "str=?", args, null, null, null);
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

    public boolean isExtSoftsimBinRefIn(String str) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {str};
        Cursor cursor = null;

        try {
            cursor = db.query(extSoftsimBinTable, null, "str=?", args, null, null, null);
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

    public void updateExtSoftsimBinData(String str, int type, byte[] data) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = {str};
        ContentValues values = new ContentValues();
        Cursor cursor = null;

        try {
            cursor = db.query(extSoftsimBinTable, null, "str=?", args, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                values.put("str", str);
                values.put("type", type);
                values.put("value", data);
                db.insert(extSoftsimBinTable, null, values);
            } else {
                JLog.logd(TAG, "bin data " + str + "already in!");
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

    private SoftsimInfo parseFromJsonToSoftsimInfo(String json) {

        return new Gson().fromJson(json, SoftsimInfo.class);
    }

    private String getExtSoftsimInfoJson(SoftsimInfo softsimInfo) {
        return new Gson().toJson(softsimInfo);
    }

    private <T> ArrayList<T> fromJsonList(String json, Class<T> cls) {
        ArrayList<T> mList = new ArrayList<T>();
        JsonArray array = new JsonParser().parse(json).getAsJsonArray();
        for (final JsonElement elem : array) {
            mList.add(new Gson().fromJson(elem, cls));
        }
        return mList;
    }

    private void closeCursor(Cursor cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception e) {

            }
        }
    }

}

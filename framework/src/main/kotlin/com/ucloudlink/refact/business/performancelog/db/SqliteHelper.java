package com.ucloudlink.refact.business.performancelog.db;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.ucloudlink.refact.utils.JLog;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by haiping.liu on 2018/3/23.
 * 仅在 DBHelper 中实例化此类
 */

public class SqliteHelper extends SQLiteOpenHelper {
    private static final String TAG = "SqliteHelper";

    public static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "UkelinkDB.db";

    public SqliteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        logd("SqliteHelper");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        logd("onCreate "+db.getPath());
        try {
            db.execSQL(PerfLogEntry.CreateTableSQL);
        }catch (SQLException e){
            e.printStackTrace();
            JLog.loge(TAG, "create table failed:" + e.getMessage());
        }

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        JLog.logd(TAG, "onUpgrade table oldVersion:" + oldVersion+",newVersion="+newVersion);
        switch (oldVersion){
            case 1:{
                db.execSQL("alter table "+PerfLogEntry.TABLE_NAME+" add column "+ PerfLogEntry.IS_FREQ_EVENT+" integer");
            }
        }
    }

    //性能日志
    public static class PerfLogEntry {
        public static final String TABLE_NAME = "PerfLog";                   //性能日志表名
        public static final String COLUMN_NAME_TYPE = "type";               //事件类型
        public static final String COLUMN_NAME_RETRYCOUNT = "retryCount";  //重试次数
        public static final String COLUMN_NAME_DATA = "data";               //数据
        public static final String IS_FREQ_EVENT = "isfreq";               //是否频繁触发的事件

        public static final String CreateTableSQL = "create table if not exists " + PerfLogEntry.TABLE_NAME +
                " (id integer primary key autoincrement," +
                COLUMN_NAME_TYPE + " integer," +
                COLUMN_NAME_RETRYCOUNT + " integer," +
                IS_FREQ_EVENT + " integer," +
                COLUMN_NAME_DATA + " text);";
    }
}

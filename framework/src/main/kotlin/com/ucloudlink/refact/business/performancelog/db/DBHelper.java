package com.ucloudlink.refact.business.performancelog.db;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;

import com.ucloudlink.refact.utils.JLog;

import java.util.ArrayList;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * Created by haiping.liu on 2018/3/23.
 *  对 SqliteHelper 操作
 */

public class DBHelper {
    private static DBHelper sDBHelper = null;
    private SqliteHelper mSqliteHelper = null;

    private DBHelper() {
    }
    //将DBHelper设置为单例模式
    public synchronized static DBHelper instance() {
        if (sDBHelper == null) {
            sDBHelper = new DBHelper();
        }
        return sDBHelper;
    }

    /**
     * 在应用的开启时进行数据库打开初始化，应用退出时进行数据库的关闭及资源释放
     * @param context application Context
     */
    public synchronized void open(Context context) {
        //在应用开启时（中进行），先对数据库进行关闭再开启，以保障上次资源的释放
        logd("open");
        close();
        mSqliteHelper = new SqliteHelper(context);
    }

    public synchronized void insert(String sql) {
        if (mSqliteHelper == null) {
            loge("insert mSqliteHelper == null");
            return;
        }
        mSqliteHelper.getWritableDatabase().execSQL(sql);
    }

    public synchronized long insert(String table, ContentValues values) {
        if (mSqliteHelper == null) {
            loge("insert mSqliteHelper == null");
            return -1;
        }
        return mSqliteHelper.getWritableDatabase().insert(table, null, values);
    }

    public synchronized int update(String table, ContentValues values,
                                   String whereClause, String[] whereArgs) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
            return -1;
        }
        return mSqliteHelper.getWritableDatabase().update(table, values,
                whereClause, whereArgs);
    }

    public synchronized void update(String sql, Object[] bindArgs) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
        }
        mSqliteHelper.getReadableDatabase().execSQL(sql,bindArgs);
    }



    public synchronized Cursor query(String table, String[] columns,
                                     String selection, String[] selectionArgs, String groupBy,
                                     String having, String orderBy) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
            return null;
        }
        return mSqliteHelper.getReadableDatabase().query(table, columns,
                selection, selectionArgs, groupBy, having, orderBy);
    }

    public synchronized Cursor queryAll(String table) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
            return null;
        }
        return mSqliteHelper.getReadableDatabase().query(table, null,
                null, null, null, null, null);
    }

    public synchronized Cursor query(String sql) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
            return null;
        }
        return mSqliteHelper.getReadableDatabase().rawQuery(sql, null);
    }

    public synchronized int delete(String table, String whereClause,
                                   String[] whereArgs) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
            return -1;
        }
        return mSqliteHelper.getReadableDatabase().delete(table, whereClause,
                whereArgs);
    }

    public synchronized void delete(String sql, Object[] bindArgs) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
        }
         mSqliteHelper.getReadableDatabase().execSQL(sql,bindArgs);
    }

    /**
     * 查询数据库中的总条数
     * @return
     */
    public synchronized long getAllNumByTable(String tableName){
        String sql = "select count(*) from "+ tableName;
        Cursor cursor = mSqliteHelper.getReadableDatabase().rawQuery(sql, null);
        cursor.moveToFirst();
        long count = cursor.getLong(0);
        cursor.close();
        return count;
    }

    /**
     * 查询数据库中的总条数
     * 0 :非频繁触发的事件
     * 1 :频繁触发的事件
     * @return
     */
    public synchronized int getAllCaseNum(String tableName ,int isFreq){
        String sql = "select * from "+ tableName+" where "+ SqliteHelper.PerfLogEntry.IS_FREQ_EVENT +" = '"+isFreq+"' order by id";
        Cursor cursor = mSqliteHelper.getReadableDatabase().rawQuery(sql, null);
        cursor.moveToFirst();
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    /**
     * 删除多余的记录
     * tableName
     * isFreq 是否频繁触发的事件 1 :频繁触发的事件 0：非频繁触发的事件
     * limit 条数，超过这个数量的将删除
     */
    public synchronized void delCase(String tableName ,int isFreq,int limit){
        JLog.logd("delCase tableName="+tableName+",isFreq = "+isFreq+", limit="+limit) ;
        String sql = "select * from "+ tableName+" where "+ SqliteHelper.PerfLogEntry.IS_FREQ_EVENT +" = '"+isFreq+"' order by id";
        Cursor cursor = mSqliteHelper.getReadableDatabase().rawQuery(sql, null);
        ArrayList list =new ArrayList<Integer>();
        cursor.moveToFirst();
        int totalcount = cursor.getCount();
        JLog.logd("delCase total count="+totalcount) ;
        if (totalcount < limit){
            return;
        }
        int count  =0;
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndex("id"));
                count++;
                if (count > limit) {
                    JLog.logd("delCase count=" + count +",id="+id);
                    list.add(id);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        if (list.size()>0){
            for (int i = 0;i<list.size();i++){
                mSqliteHelper.getWritableDatabase().execSQL("delete from " + tableName+ " where id ="+list.get(i));
            }
        }
    }


    /**
     * 删除前 count 条数据
     * @param count
     */
    public void deleteByCount(int count,String tableName){
        if (count <=0){
            loge("deleteByCount count <=0");
            return;
        }
        if (mSqliteHelper == null) {
            loge("deleteByCount == null");
        }
        String sql="DELETE FROM "+ tableName+" where id in (SELECT id  FROM "+ tableName+" order by id limit 0,"+count+")";
        mSqliteHelper.getWritableDatabase().execSQL(sql);
    }


    /**
     * 清空某一个表
     * @param tableName
     */
    public synchronized void deleteAll(String tableName) {
        if (mSqliteHelper == null) {
            loge("mSqliteHelper == null");
        }
        mSqliteHelper.getWritableDatabase().execSQL("delete from "+tableName);
    }



    public synchronized void close() {
        if (mSqliteHelper != null) {
            mSqliteHelper.close();
            mSqliteHelper = null;
        }
    }
}

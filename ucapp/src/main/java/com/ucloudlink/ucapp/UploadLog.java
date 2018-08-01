package com.ucloudlink.ucapp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import com.ucloudlink.refact.business.s2ccmd.CmdPerform;
import com.ucloudlink.refact.business.s2ccmd.UpLogArgs;
import com.ucloudlink.refact.business.s2ccmd.UpQxLogArgs;
import com.ucloudlink.refact.business.log.FilterLogs;
import com.ucloudlink.refact.business.log.TimeConver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static com.ucloudlink.refact.utils.JLog.logv;

public class UploadLog extends Activity {

    private final static int QUICK_UP = 1;
    private final static int SELECT_UP = 2;
    private final static int QX_QUICK_UP = 3;
    private final static int QX_SELECT_UP = 4;
    private TextView tx_starttime, tx_endtime, tx_upload_progress;
    private String mStartTime, mEndTime;
    private String mStartPickTime = "", mEndPickTime = "";
    private Button btn_upload1, btn_upload3, btn_upload5, btn_upload7, btn_upload;
    private Button btn_uploadqx1, btn_uploadqx3, btn_uploadqx5, btn_uploadqx7, btn_uploadqx;
    private Toast mToast;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {//此方法在ui线程运行
            logv("mHandler uploadui msg.what:" + msg.what);
            tx_upload_progress.setText(getTipByCode(msg.what));
            if (msg.what == CmdPerform.UPLOAD_ADB_LOG_SUCCEED ||
                    msg.what == CmdPerform.UPLOAD_QXDM_LOG_SUCCEED) {
                resetView();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_log);
        initView();
    }

    private void initView() {
        tx_starttime = (TextView) findViewById(R.id.tx_starttime);
        tx_endtime = (TextView) findViewById(R.id.tx_endtime);
        tx_upload_progress = (TextView) findViewById(R.id.tx_upload_progress);

        btn_upload1 = (Button) findViewById(R.id.btn_upload1);
        btn_upload3 = (Button) findViewById(R.id.btn_upload3);
        btn_upload5 = (Button) findViewById(R.id.btn_upload5);
        btn_upload7 = (Button) findViewById(R.id.btn_upload7);
        btn_upload = (Button) findViewById(R.id.btn_upload);

        btn_uploadqx1 = (Button) findViewById(R.id.btn_uploadqx1);
        btn_uploadqx3 = (Button) findViewById(R.id.btn_uploadqx3);
        btn_uploadqx5 = (Button) findViewById(R.id.btn_uploadqx5);
        btn_uploadqx7 = (Button) findViewById(R.id.btn_uploadqx7);
        btn_uploadqx = (Button) findViewById(R.id.btn_uploadqx);

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);//初始化成员变量toast
    }

    private void changeView() {
        btn_upload1.setEnabled(false);
        btn_upload3.setEnabled(false);
        btn_upload5.setEnabled(false);
        btn_upload7.setEnabled(false);
        btn_upload.setEnabled(false);

        btn_uploadqx1.setEnabled(false);
        btn_uploadqx3.setEnabled(false);
        btn_uploadqx5.setEnabled(false);
        btn_uploadqx7.setEnabled(false);
        btn_uploadqx.setEnabled(false);
    }

    private void resetView() {
        btn_upload1.setEnabled(true);
        btn_upload3.setEnabled(true);
        btn_upload5.setEnabled(true);
        btn_upload7.setEnabled(true);
        btn_upload.setEnabled(true);

        btn_uploadqx1.setEnabled(true);
        btn_uploadqx3.setEnabled(true);
        btn_uploadqx5.setEnabled(true);
        btn_uploadqx7.setEnabled(true);
        btn_uploadqx.setEnabled(true);
    }

    private String getTipByCode(int what) {
        String tips = "";
        switch (what) {
            case CmdPerform.COMPRESS_ADB_LOG_START:
                tips = "adb log 开始压缩";
                break;
            case CmdPerform.COMPRESS_ADB_LOG_COMPLETE:
                tips = "adb log 压缩完成";
                break;
            case CmdPerform.UPLOAD_ADB_LOG_START:
                tips = "adb log 开始上传";
                break;
            case CmdPerform.UPLOAD_ADB_LOG_SUCCEED:
                tips = "adb log 上传成功";
                break;
            case CmdPerform.UPLOAD_ADB_LOG_FAILED:
                tips = "adb log 上传失败";
                break;
            case CmdPerform.COMPRESS_QXDM_LOG_START:
                tips = "qxdm log 开始压缩";
                break;
            case CmdPerform.COMPRESS_QXDM_LOG_COMPLETE:
                tips = "qxdm log 压缩完成";
                break;
            case CmdPerform.UPLOAD_QXDM_LOG_START:
                tips = "qxdm log 开始上传";
                break;
            case CmdPerform.UPLOAD_QXDM_LOG_SUCCEED:
                tips = "qxdm log 上传成功";
                break;
            case CmdPerform.UPLOAD_QXDM_LOG_FAILED:
                tips = "qxdm log 上传失败";
                break;
        }
        return tips;
    }

    public void onUpload1(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(0);
        mEndTime = getDateToday();

        logv("UploadLog", "onUpload1 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QUICK_UP);
    }

    public void onUpload3(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(-2);
        mEndTime = getDateToday();

        logv("UploadLog", "onUpload3 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QUICK_UP);
    }

    public void onUpload5(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(-4);
        mEndTime = getDateToday();

        logv("UploadLog", "onUpload5 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QUICK_UP);
    }

    public void onUpload7(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(-6);
        mEndTime = getDateToday();

        logv("UploadLog", "onUpload7 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QUICK_UP);
    }

    public void onUploadQx1(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(0);
        mEndTime = getDateToday();

        logv("onUploadQx1 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QX_QUICK_UP);
    }

    public void onUploadQx3(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(-2);
        mEndTime = getDateToday();

        logv("onUploadQx3 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QX_QUICK_UP);
    }

    public void onUploadQx5(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(-4);
        mEndTime = getDateToday();

        logv("onUploadQx5 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QX_QUICK_UP);
    }

    public void onUploadQx7(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }
        changeView();

        mStartTime = getStartDateBefore(-6);
        mEndTime = getDateToday();

        logv("onUploadQx7 select mStartTime:" + mStartTime + ", mEndTime:" + mEndTime);
        uploadAlllogs(QX_QUICK_UP);
    }

    private DatePickerDialog.OnDateSetListener mStartdateListener = new DatePickerDialog.OnDateSetListener() {

        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear,
                              int dayOfMonth) {
            int naturalMonth = monthOfYear + 1;
            tx_starttime.setText(year + "/" + naturalMonth + "/" + dayOfMonth);
            mStartPickTime = year + "-" + naturalMonth + "-" + dayOfMonth + " 00:00:00";
            logv("OnDateSet", "onStartTime select year:" + year + ";naturalMonth:" + naturalMonth + ";day:" + dayOfMonth);
        }
    };

    private DatePickerDialog.OnDateSetListener mEnddateListener = new DatePickerDialog.OnDateSetListener() {

        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear,
                              int dayOfMonth) {
            int naturalMonth = monthOfYear + 1;
            tx_endtime.setText(year + "/" + naturalMonth + "/" + dayOfMonth);
            mEndPickTime = year + "-" + naturalMonth + "-" + dayOfMonth + " 23:59:59";
            logv("OnDateSet", "onEndTime select year:" + year + ";naturalMonth:" + naturalMonth + ";day:" + dayOfMonth);
        }
    };

    public void onStartTime(View view) {
        final Calendar ca = Calendar.getInstance();
        int mYear = ca.get(Calendar.YEAR);
        int mMonth = ca.get(Calendar.MONTH);
        int mDay = ca.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, mStartdateListener, mYear, mMonth, mDay).show();
    }

    public void onEndTime(View view) {
        final Calendar ca = Calendar.getInstance();
        int mYear = ca.get(Calendar.YEAR);
        int mMonth = ca.get(Calendar.MONTH);
        int mDay = ca.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, mEnddateListener, mYear, mMonth, mDay).show();
    }

    public void onStartUpload(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }

        //检查开始结束日期的合法性，给出提示或者上传
        if (mStartPickTime.equals("")) {
            mToast.setText("请选择开始日期");//刷新文字内容
            mToast.show();
            return;
        }

        if (mEndPickTime.equals("")) {
            mToast.setText("请选择结束日期");//刷新文字内容
            mToast.show();
            return;
        }

        Date startPickTime = TimeConver.converString2Time(mStartPickTime, TimeConver.Dataformat);
        Date endPickTime = TimeConver.converString2Time(mEndPickTime, TimeConver.Dataformat);

        if (!FilterLogs.isDateAvailable(startPickTime, endPickTime)) {
            mToast.setText("错误:开始日期大于结束日期！");//刷新文字内容
            mToast.show();
            return;
        } else {
            logv("UploadLog", "onUpload7 select mStartTime:" + startPickTime + ", mEndTime:" + endPickTime);
            changeView();
            //调用上传日志接口
            uploadAlllogs(SELECT_UP);
        }
    }

    public void onStartUploadQx(View view) {
        if (!isNetworkOK()) {
            mToast.setText("网络无连接，不能上传log");//刷新文字内容
            mToast.show();
            return;
        }

        //检查开始结束日期的合法性，给出提示或者上传
        if (mStartPickTime.equals("")) {
            mToast.setText("请选择开始日期");//刷新文字内容
            mToast.show();
            return;
        }

        if (mEndPickTime.equals("")) {
            mToast.setText("请选择结束日期");//刷新文字内容
            mToast.show();
            return;
        }

        Date startPickTime = TimeConver.converString2Time(mStartPickTime, TimeConver.Dataformat);
        Date endPickTime = TimeConver.converString2Time(mEndPickTime, TimeConver.Dataformat);

        if (!FilterLogs.isDateAvailable(startPickTime, endPickTime)) {
            mToast.setText("错误:开始日期大于结束日期！");//刷新文字内容
            mToast.show();
            return;
        } else {
            logv("UploadLog", "onUpload7 select mStartTime:" + startPickTime + ", mEndTime:" + endPickTime);
            changeView();
            //调用上传日志接口
            uploadAlllogs(QX_SELECT_UP);
        }
    }


    /**
     * 检查网络是否可用
     *
     * @return
     */
    public boolean isNetworkOK() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /**
     * 获取当天"yyyy-MM-dd HH:mm:ss"
     *
     * @return
     */
    public String getDateToday() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String date = format.format(new Date(System.currentTimeMillis()));

        return date + " 23:59:59";
    }

    /**
     * 获取开始时间 "yyyy-MM-dd HH:mm:ss"
     *
     * @param day 几天前就传入负数，比如一天前就是getStartDateBefore(-1)
     *            几天后就传入正数，比如一天后就是getStartDateBefore(1)
     * @return
     */
    public static String getStartDateBefore(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String time = sdf.format(new Date());
        Calendar cd = Calendar.getInstance();

        try {
            cd.setTime(sdf.parse(time));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        cd.add(Calendar.DATE, day);//增加一天
        Date date = cd.getTime();
        String startDate = sdf.format(date);
        return startDate + " 00:00:00";
    }

    /**
     * 获取结束时间 "yyyy-MM-dd HH:mm:ss"
     *
     * @param day 几天前就传入负数，比如一天前就是getEndDateBefore(-1)
     *            几天后就传入正数，比如一天后就是getEndDateBefore(1)
     * @return
     */
    public static String getEndDateBefore(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String time = sdf.format(new Date());
        Calendar cd = Calendar.getInstance();

        try {
            cd.setTime(sdf.parse(time));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        cd.add(Calendar.DATE, day);
        Date date = cd.getTime();
        String endDate = sdf.format(date);
        return endDate + " 23:59:59";
    }

    private void uploadAlllogs(final int select) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                UpQxLogArgs paramOfQxUpload = new UpQxLogArgs();
                UpLogArgs paramOfUpload = new UpLogArgs();
                CmdPerform.INSTANCE.setProgressHint(mHandler);
                switch (select) {
                    case QUICK_UP:
                        paramOfUpload.setStartTime(mStartTime);
                        paramOfUpload.setEndTime(mEndTime);
                        CmdPerform.INSTANCE.uploadlogForUI(paramOfUpload);
                        break;
                    case SELECT_UP:
                        paramOfUpload.setStartTime(mStartPickTime);
                        paramOfUpload.setEndTime(mEndPickTime);
                        CmdPerform.INSTANCE.uploadlogForUI(paramOfUpload);
                        break;
                    case QX_QUICK_UP:
                        paramOfQxUpload.setStartTime(mStartTime);
                        paramOfQxUpload.setEndTime(mEndTime);

                        CmdPerform.INSTANCE.upqxloadlog2(paramOfQxUpload);
                        break;
                    case QX_SELECT_UP:
                        paramOfQxUpload.setStartTime(mStartPickTime);
                        paramOfQxUpload.setEndTime(mEndPickTime);

                        CmdPerform.INSTANCE.upqxloadlog2(paramOfQxUpload);
                        break;
                    default:
                        break;
                }
            }
        }).start();
    }
}

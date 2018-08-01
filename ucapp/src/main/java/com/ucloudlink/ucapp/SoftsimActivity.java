package com.ucloudlink.ucapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.framework.ui.IUcloudDownloadSoftsimCallback;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.softsim.SoftsimEntry;
import com.ucloudlink.refact.business.softsim.download.SoftsimDownloadState;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by shiqianhua on 2017/1/9.
 */

public class SoftsimActivity extends Activity{
    private final static String TAG = "SoftsimActivity";
    private SoftsimEntry softsimEntry = ServiceManager.INSTANCE.getAccessEntry().softsimEntry;

    private EditText usernameText;
    private EditText passwordText;
    private EditText orderText;

    private TextView resultText;

    private Handler mhander;

    private String spName = "testSp";
    private String spUsername = "spUsername";
    private String spPassword = "spPassword";
    private String spOrder = "spOrder";

    private static final int EVENT_RESULT = 1;
    private static final int EVENT_PERSENT = 2;
    private static final int EVENT_MSG = 3;


    private IUcloudDownloadSoftsimCallback cb = new IUcloudDownloadSoftsimCallback() {
        @Override
        public void eventSoftsimDownloadResult(String order, int errorCode) throws RemoteException {
            mhander.obtainMessage(EVENT_RESULT, errorCode, 0, order).sendToTarget();
        }

        @Override
        public IBinder asBinder() {
            return null;
        }

        @Override
        public void eventStartSeedSimResult(String packageName, int errcode) throws RemoteException {

        }
    };

    private class LocalResult{
        public LocalResult(String order, String msg, int errcode) {
            this.order = order;
            this.msg = msg;
            this.errcode = errcode;
        }

        public String order;
        public String msg;
        public int errcode;
    }

    private SoftsimDownloadState.SoftsimEventCb localCb = new SoftsimDownloadState.SoftsimEventCb(){
        @Override
        public void downloadResult(String order, int result, String msg) {
            mhander.obtainMessage(EVENT_RESULT, 0, 0, new LocalResult(order, msg, result)).sendToTarget();
        }

        @Override
        public void errorUpdate(int errcode, String msg) {
            mhander.obtainMessage(EVENT_MSG, 0, 0, new LocalResult("", msg, errcode)).sendToTarget();
        }

        @Override
        public void persentUpdate(int persent) {
            mhander.obtainMessage(EVENT_MSG, 0, 0, new LocalResult("", "persent!", persent)).sendToTarget();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_softsim);

        usernameText = (EditText)findViewById(R.id.s_username);
        passwordText = (EditText)findViewById(R.id.s_passwd);
        orderText = (EditText)findViewById(R.id.s_order);
        resultText = (TextView)findViewById(R.id.s_view);

        SharedPreferences ucappSp = getSharedPreferences(spName, 0);
        String username = ucappSp.getString(spUsername, "");
        String password = ucappSp.getString(spPassword, "");
        String order = ucappSp.getString(spOrder, "");

        usernameText.setText(username);
        passwordText.setText(password);
        orderText.setText(order);


        mhander = new Handler(Looper.myLooper()){
            @Override
            public void handleMessage(Message msg) {
                LocalResult result;
                switch (msg.what){
                    case EVENT_RESULT:
                        result = (LocalResult)msg.obj;
                        if(result.errcode == 0){
                            resultText.setText("order " + result.order + " download succ!!!");
                        }else{
                            resultText.setText("order " + result.order + " download failed!!!! errcode:" + result.errcode + " message:" + result.msg);
                        }
                        break;
                    case EVENT_PERSENT:
                        result = (LocalResult)msg.obj;
                        resultText.setText("persent " + result.errcode +  "%");
                        break;
                    case EVENT_MSG:
                        result = (LocalResult)msg.obj;
                        resultText.setText("event: " + result.errcode  + " msg:" + result.msg);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public void onStart(View view){
        Log.d(TAG, "onStart: onStart!!!!");
        String username = usernameText.getText().toString().toString();
        String password = passwordText.getText().toString().toString();
        String order = orderText.getText().toString().toString();

        if(username == null || username.isEmpty()){
            resultText.setText("username is null!");
            return;
        }
        if(password == null || password.isEmpty()){
            resultText.setText("password is null!");
            return;
        }

        if(order == null || order.isEmpty()){
            resultText.setText("order is null!");
            return;
        }
        saveParam(username,password, order);
        Long startTime = (new Date()).getTime()/1000;
        Long endTime = (new Date()).getTime()/1000 + 1000000;
        //softsimEntry.registerCb(cb);
        softsimEntry.regLocalCb(localCb);
        Log.d(TAG, "onStart: start download" + username + " " + password + " " + order);
        int [] mccList = {460, 110};
        ArrayList<FlowOrder> list = new ArrayList<>();
        list.add(new FlowOrder(order, mccList, new Date().getTime(), 30, 1));
        softsimEntry.startDownloadSoftsim(username, password, list);
    }

    private void saveParam(String username, String passwd, String order){
        SharedPreferences ucappSp = getSharedPreferences(spName, 0);
        SharedPreferences.Editor edit = ucappSp.edit();
        edit.putString(spUsername, username);
        edit.putString(spPassword, passwd);
        edit.putString(spOrder, order);
        edit.commit();
    }

    public void onStop(View view){
        Log.d(TAG, "onStop: onStop!!!");
        softsimEntry.stopDownloadAll();
    }

    public void onUpdate(View view){
        Log.d(TAG, "onUpdate: update!!!!");
        softsimEntry.startUpdateAllSoftsim("12345678901234567", "460", "01");
    }

    public void onChangeActivity(View view){
        Intent intent = new Intent(this, InitActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}

package com.ucloudlink.ucapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.ucloudlink.framework.access.TestAccount;
import com.ucloudlink.framework.access.TestAccountKt;
import com.ucloudlink.framework.mbnload.MbnLoad;
import com.ucloudlink.refact.business.netcheck.Ncsi;
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.Framework;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEntry;
import com.ucloudlink.refact.access.AccessState;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.access.ui.AccessEntryService;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.utils.TcpdumpHelper;

import java.util.ArrayList;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
//import com.ucloudlink.refact.AccessManager;

/**
 * Created by chentao on 2016/7/7.
 */
public class InitActivity extends Activity {
    private final static String      TAG         = "InitActivity";
    //private AccessManager accessManager=ServiceManager.INSTANCE.getAccessManager();
    private              AccessEntry accessEntry;

    private        RadioGroup mApduSelect;
    private        RadioGroup mSlotSelect;
    private        Spinner    accountSelect;
    private static MbnLoad    mMbnLoad;
    SpinnerAdapter adapter = null;
    Account        User    = null;
    private View      switchcloudsim;
    private View      btn_loadmbn;
    private View      btn_exitloadmbn;
    private EditText  username;
    private EditText  password;
    private ViewGroup userLayout;
    private CheckBox  check_isUse;
    private CheckBox  debugServer;
    private CheckBox  debugRplmn;
    private TextView  systemPersent;
    private TextView  seedPersent;
    private TextView  systemError;
    private TextView  seedError;
    private TextView  title_tv;
    private CheckBox  qxdmLog;
    private CheckBox  tcpdumpLog;
    //private Button qxdm_clean;

    private AdapterView.OnItemSelectedListener mListener;
    private final String SP_MCHECK_ISUSE     = "check_isUse";
    private final String SP_CHECK_QXUSE      = "check_qxUse";
    private final String SP_CHECK_TCPDUMP    = "tcpdump_use";
    private final String SP_CHECK_ASSUSE     = "check_AssServerUse";
    private final String SP_CHECK_RPLMN      = "check_Rplmn";
    private final String SP_PASSWORD         = "password";
    private final String SP_USERNAME         = "username";
    private final String UCAPP_SP_NAME       = "ucappSp";
    private final String SP_APDU_MODE_SELECT = "apdu_mode_select";
    private final String SP_SLOT_MODE_SELECT = "slot_mode_select";
    int       logid  = 0;

    private static final int HANDLE_MSG_EVENT_SYS_PERSENT  = 1;
    private static final int HANDLE_MSG_EVENT_SEED_PERSENT = 2;
    private static final int HANDLE_MSG_EVENT_ERROR        = 3;
    private static final int HANDLE_MSG_EVENT_SYS_SUCC     = 4;
    private static final int HANDLE_MSG_EVENT_SYS_RESET    = 5;
    private static final int HANDLE_MSG_EVENT_SEED_ERR     = 6;
    private static final int HANDLE_MSG_EVENT_MBN          = 7;
    private static final int HANDLE_MSG_EVENT_SIM_NET_NOTIFY   = 8;
    private Context context;
    private NetworkRequest mBuild;
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Framework.INSTANCE.environmentInit(this.getApplicationContext());
        startService(new Intent(this, AccessEntryService.class));

        super.onCreate(savedInstanceState);
        //        Intent intent=new Intent(this ,MainActivity.class);
        //        startActivity(intent);
        setContentView(R.layout.activity_init);
        mApduSelect = (RadioGroup) findViewById(R.id.apduModeSelect);
        mSlotSelect = (RadioGroup) findViewById(R.id.slotModeSelect);
        accountSelect = (Spinner) findViewById(R.id.accountSelect);
        switchcloudsim = (View) findViewById(R.id.btn_switchcloudsim);
        btn_loadmbn = (View) findViewById(R.id.btn_loadmbn);
        btn_exitloadmbn = (View) findViewById(R.id.btn_exitloadmbn);
        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);
        userLayout = (ViewGroup) findViewById(R.id.user_layout);
        check_isUse = (CheckBox) findViewById(R.id.check_isUse);
        systemPersent = (TextView) findViewById(R.id.sysPersent);
        seedPersent = (TextView) findViewById(R.id.seedPersent);
        systemError = (TextView) findViewById(R.id.systemError);
        seedError = (TextView) findViewById(R.id.seedError);
        title_tv = (TextView) findViewById(R.id.title_tv);
        qxdmLog = (CheckBox) findViewById(R.id.qxdmLog);
        debugServer = (CheckBox) findViewById(R.id.debugServer);
        debugRplmn = (CheckBox) findViewById(R.id.debugRplmn);
        tcpdumpLog = (CheckBox) findViewById(R.id.tcpdump);
        init();
        context = this;
        Message msg = new Message();
        msg.what = HANDLE_MSG_EVENT_SYS_PERSENT;
        msg.arg1 = accessEntry.getSystemPersent();
        mHandler.sendMessage(msg);
        accessEntry.registerAccessStateListen(mAccessListener);

//        int mode = 0;
//        int persent = accessEntry.getSystemPersent();
//        if (persent == 0) {
//            mode = NoticeCtrl.OFF;
//        } else if (persent > 0 && persent < 100) {
//            mode = NoticeCtrl.RUNNING;
//        } else if (persent == 100) {
//            mode = NoticeCtrl.ON;
//        }
//
//        try {
//            NoticeCtrl.getInstance().setMode(mode);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        Message msg2 = new Message();
        msg2.what = HANDLE_MSG_EVENT_SEED_PERSENT;
        msg2.arg1 = accessEntry.getSeedPersent();
        mHandler.sendMessage(msg2);
        if (accessEntry.isInExceptionState()) {
            ArrayList<Integer> result = accessEntry.getExceptionArray();
            loge("get is exception!!! " + accessEntry.isInExceptionState() + " " + result);
            Message msg3 = new Message();
            msg3.what = HANDLE_MSG_EVENT_ERROR;
            msg3.arg1 = (result != null && result.size() != 0) ? result.get(0) : 0;
            msg3.obj = ErrorCode.INSTANCE.getErrMsgByCode(msg3.arg1);
            mHandler.sendMessage(msg3);
        }
        //开始PSExcTest
        //        HandlerThread test = new HandlerThread("test");
        //        test.start();
        //        new PSExcTest(this,test.getLooper());
    }

    private void init() {
        accessEntry = ServiceManager.INSTANCE.getAccessEntry();
//        try {
//            NoticeCtrl.getInstance().setMode(NoticeCtrl.OFF);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        PackageManager pm = getPackageManager();//context为当前Activity上下文
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo("com.ucloudlink.ucapp", 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String version = pi.versionName;
        title_tv.setText("Cloudsim v" + version);
        String[] users = null;
        if (Configuration.INSTANCE.isForChina()) {
            users = new String[]{"test_100", "test_101", "QUAL_US8005", "QUAL_US8006", "QUAL_IN1975", "QUAL_IN2163", "AIRTEL_IN7892", "ATT_US8051", "ATT_US8118", "test10", "test_102", "test_103", "wangqingli", "qinyongbin", "shiqianhua"};
        } else {
            users = new String[]{/*"shaofeng", "wangliang",*/ "QUAL_US8005", "QUAL_US8006", "QUAL_IN1975", "QUAL_IN2163", "AIRTEL_IN7892", "ATT_US8051", "ATT_US8118"};
            //            btn_loadmbn.setVisibility(View.GONE);
            //            btn_exitloadmbn.setVisibility(View.GONE);
            userLayout.setVisibility(View.GONE);
        }
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, users);
        accountSelect.setAdapter(adapter);
        mListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                logd(": accountSelect:");
                if (Configuration.INSTANCE.isForChina()) {
                } else {
                    i = i + 2;
                }
                switch (i) {
                    case 0:
                        User = new Account("test_APP_100@163.com", "123456");
                        break;
                    case 1://wangliang
                        User = new Account("test_APP_101@163.com", "123456");
                        break;
                    case 2://QUAL_US8005
                        User = new Account("test_APP_03@163.com", "123456");
                        break;
                    case 3://QUAL_US8006
                        User = new Account("test_APP_04@163.com", "123456");
                        break;
                    case 4://QUAL_IN1975
                        User = new Account("test_APP_05@163.com", "123456");
                        break;
                    case 5://QUAL_IN2163
                        User = new Account("test_APP_06@163.com", "123456");
                        break;
                    case 6://AIRTEL_IN7892
                        User = new Account("test_APP_07@163.com", "123456");
                        break;
                    case 7://ATT_US8051
                        User = new Account("test_APP_01@163.com", "123456");
                        break;
                    case 8://ATT_US8118
                        User = new Account("test_APP_02@163.com", "123456");
                        break;
                    case 9://test10
                        User = new Account("test_APP_10@163.com", "123456");
                        break;
                    case 10://qingli
                        User = new Account("test_APP_102@163.com", "123456");
                        break;
                    case 11://test100
                        User = new Account("test_APP_103@163.com", "123456");
                        break;
                    case 12://wangqingli
                        User = new Account("test_wangqingli@163.com", "123456");
                        break;
                    case 13://qinyongbin
                        User = new Account("qinyongbin@ukelink.com", "qyb#123456");
                        break;
                    case 14: //shiqianhua
                        User = new Account("test_shiqianhua@163.com", "123456");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                User = new Account("test_shenshaofeng@163.com", "123456");
            }
        };
        accountSelect.setOnItemSelectedListener(mListener);
        SharedPreferences ucappSp = getSharedPreferences("ucappSp", 0);
        String username = ucappSp.getString(SP_USERNAME, "");
        String password = ucappSp.getString(SP_PASSWORD, "");
        //        boolean check_isUse = ucappSp.getBoolean(SP_MCHECK_ISUSE, true);
        this.username.setText(username);
        this.password.setText(password);
        this.check_isUse.setChecked(true);//自动选定
        this.debugServer.setChecked(ucappSp.getBoolean(SP_CHECK_ASSUSE, false));
        this.debugRplmn.setChecked(ucappSp.getBoolean(SP_CHECK_RPLMN, false));
        Configuration.INSTANCE.setOpenRplmnTest(debugRplmn.isChecked());
        this.onQxdmlogOpen();
        this.onSoltModeSelectInit();
        this.onApduModeSelectInit();
        this.onTcpdumpSelectInit();
    }

    public void onInit(View view) {
        logd("onInit");
        initConfig();
        Account myAccout = null;
        String username = this.username.getText().toString().toString();
        String password = this.password.getText().toString().toString();
        if (check_isUse.isChecked()) {
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
                //UserName 与 password 都不为空才使用自己用户名登录
                //todo 做持久化保存优化
                saveAtSP(username, password);
                myAccout = new Account(username, password);
            } else {
                showToast("用户名密码格式有问题");
                return;
            }
        }
        saveBooleanSP(SP_MCHECK_ISUSE, check_isUse.isChecked());
        //        if (User == null) {
        //            showToast( "choose an account,pls!");
        //            return;
        //        }
        //        AutoRun.INSTANCE.setAutoListen(new AutoRunListen());
        showToast("Auto Run Start");
        if (!check_isUse.isChecked()) {
            doLogin(User.userName, User.pw);
        } else {
            doLogin(myAccout.userName, myAccout.pw);
        }
    }

    private void doLogin(String userName, final String pw){
        if(!Configuration.INSTANCE.getUseServerSoftsim()) {
            if (Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()) {
                if (!setSoftSimImsi(userName)) {
                    return;
                }
            }
        }else{
            Log.d(TAG, "doLogin: no need softsim here");
        }
        final String name = userName;
        final String psw = pw;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //accessManager.startCloudSimSession(name,psw);
                accessEntry.loginReq(name, psw);
            }
        }).start();
    }

    private AccessStateListener mAccessListener = new AccessStateListener();

    private class AccessStateListener implements AccessState.AccessStateListen {
        @Override
        public void errorUpdate(int errorCode, String message) {
            Message msg = new Message();
            msg.what = HANDLE_MSG_EVENT_ERROR;
            msg.arg1 = errorCode;
            msg.obj = message;
            mHandler.sendMessage(msg);
        }

        @Override
        public void processUpdate(int persent) {
            Message msg = new Message();
            msg.what = HANDLE_MSG_EVENT_SYS_PERSENT;
            msg.arg1 = persent;
            mHandler.sendMessage(msg);
        }

        @Override
        public void eventCloudSIMServiceStop(int reason, String message) {
            Message msg = new Message();
            msg.what = HANDLE_MSG_EVENT_SYS_RESET;
            msg.arg1 = reason;
            msg.obj = message;
            mHandler.sendMessage(msg);
        }

        @Override
        public void eventCloudsimServiceSuccess() {
            Message msg = new Message();
            msg.what = HANDLE_MSG_EVENT_SYS_SUCC;
            mHandler.sendMessage(msg);
        }
        @Override
        public void eventSeedState(int persent) {
            Message msg = new Message();
            msg.what = HANDLE_MSG_EVENT_SEED_PERSENT;
            msg.arg1 = persent;
            mHandler.sendMessage(msg);
        }

        @Override
        public void eventSeedError(int code, String message) {
            Message msg = new Message();
            msg.what = HANDLE_MSG_EVENT_SEED_ERR;
            msg.arg1 = code;
            msg.obj = message;
            mHandler.sendMessage(msg);
        }
    }

    private boolean setSoftSimImsi(String userName) {
        logd("setSoftSimImsi: userName:" + userName);
        for (TestAccount testAccount : TestAccountKt.getTestAccountList()) {
            if (testAccount.getName().equals(userName)) {
                String softsim = testAccount.getSoftsim();
                logd("setSoftSimImsi: softsim:" + softsim);
                Configuration.INSTANCE.setSoftSimImsi(softsim);
                return true;
            }
        }
        Message msg = new Message();
        msg.what = HANDLE_MSG_EVENT_ERROR;
        msg.arg1 = ErrorCode.INSTANCE.getLOCAL_SOFT_CARD_NOT_EXIST();
        msg.obj = ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getLOCAL_SOFT_CARD_NOT_EXIST());
        mHandler.sendMessage(msg);
        return false;
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void saveAtSP(String username, String password) {
        SharedPreferences ucappSp = getSharedPreferences(UCAPP_SP_NAME, 0);
        SharedPreferences.Editor edit = ucappSp.edit();
        edit.putString(SP_USERNAME, username);
        edit.putString(SP_PASSWORD, password);
        edit.commit();
    }

    private void saveIntSP(String key, int value) {
        SharedPreferences ucappSp = getSharedPreferences(UCAPP_SP_NAME, 0);
        SharedPreferences.Editor edit = ucappSp.edit();
        edit.putInt(key, value);
        edit.commit();
    }

    private void saveBooleanSP(String key, boolean value) {
        SharedPreferences ucappSp = getSharedPreferences("ucappSp", 0);
        SharedPreferences.Editor edit = ucappSp.edit();
        edit.putBoolean(key, value);
        edit.commit();
    }

    //换卡 switchCloudSim
    public void onSwitchCloudsim(View view) {
        //        Framework.INSTANCE.getAccessManager().switchCloudsim(0, "resever");
        accessEntry.switchVsimReq(2);
    }

    public void onNcsiTest(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Ncsi.getInstance().testMobileNcsi("init test", ConnectivityManager.TYPE_MOBILE);
            }
        }).start();
    }

    public void onReset(View view) {
        logd("onReset.....");
        Toast.makeText(this, "reset", Toast.LENGTH_SHORT).show();
        //        accessManager.stopCloudSimSession();
        accessEntry.logoutReq(3);
    }

    private void initConfig() {
        int apduMode = 0;
        switch (mApduSelect.getCheckedRadioButtonId()) {
            case R.id.softMode:
                apduMode = Configuration.INSTANCE.getApduMode_soft();
                break;
            case R.id.phyMode:
                apduMode = Configuration.INSTANCE.getApduMode_Phy();
                break;
        }
        Configuration.INSTANCE.setApduMode(apduMode);
        int seedslot = 0;
        int cloudslot = 0;
        switch (mSlotSelect.getCheckedRadioButtonId()) {
            case R.id.AVMode:
                seedslot = 0;
                cloudslot = 1;
                break;
            case R.id.VAMode:
                seedslot = 1;
                cloudslot = 0;
                break;
        }
        Configuration.INSTANCE.setSlots(seedslot, cloudslot);
        logd("apduMode: " + apduMode + " seedslot： " + seedslot + " cloudslot： " + cloudslot);
        int assMode;
        if (debugServer.isChecked()) {
            assMode = 101;
        } else {
            assMode = 100;
        }
        logd("assMode:" + assMode);
        ServerRouter.INSTANCE.setIpMode(assMode);
    }

    public void onLoadMBN(View view) {
        if (mMbnLoad == null) {
            mMbnLoad = new MbnLoad(getApplicationContext());
        }
        Message msg = new Message();
        msg.what = HANDLE_MSG_EVENT_MBN;
        msg.obj = "";
        mHandler.sendMessage(msg);
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    mMbnLoad.enterMbnLoad();
                    int count = 0;
                    while (count++ < 10) {
                        Thread.sleep(500);
                        if (mMbnLoad.checkResult()) {
                            Message msg = new Message();
                            msg.what = HANDLE_MSG_EVENT_MBN;
                            msg.obj = "Load mbn success!";
                            mHandler.sendMessage(msg);
                            return;
                        }
                    }
                    Message msg = new Message();
                    msg.what = HANDLE_MSG_EVENT_MBN;
                    msg.obj = "Load mbn failed!";
                    mHandler.sendMessage(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void onExitLoadMBN(View view) {
        if (mMbnLoad == null) {
            mMbnLoad = new MbnLoad(getApplicationContext());
        }
        mMbnLoad.exitMbnLoad();
        Message msg = new Message();
        msg.what = HANDLE_MSG_EVENT_MBN;
        msg.obj = "exit loadmbn completed";
        mHandler.sendMessage(msg);
    }

    public void onSoltModeSelectClick(View view) {
        logd("modeclick onSoltModeSelectClick:" + mSlotSelect.getCheckedRadioButtonId());
        saveIntSP(SP_SLOT_MODE_SELECT, mSlotSelect.getCheckedRadioButtonId());
    }

    public void onSoltModeSelectInit() {
        SharedPreferences ucappSp = getSharedPreferences("ucappSp", 0);
        logd("modeclick onSoltModeSelectInit:" + ucappSp.getInt(SP_SLOT_MODE_SELECT, R.id.AVMode));
        mSlotSelect.check(ucappSp.getInt(SP_SLOT_MODE_SELECT, R.id.AVMode));
    }

    public void onApduModeSelectClick(View view) {
        logd("modeclick onApduModeSelectClick:" + mApduSelect.getCheckedRadioButtonId());
        saveIntSP(SP_APDU_MODE_SELECT, mApduSelect.getCheckedRadioButtonId());
    }

    public void onApduModeSelectInit() {
        SharedPreferences ucappSp = getSharedPreferences("ucappSp", 0);
        logd("modeclick onApduModeSelectInit:" + ucappSp.getInt(SP_APDU_MODE_SELECT, R.id.softMode));
        mApduSelect.check(ucappSp.getInt(SP_APDU_MODE_SELECT, R.id.softMode));
    }

    public void onQxdmlogOpen() {
        SharedPreferences ucappSp = getSharedPreferences("ucappSp", 0);
        boolean check_isUse = ucappSp.getBoolean(SP_CHECK_QXUSE, true);
        logd("onQxdmlogOpen setChecked:" + check_isUse);
        qxdmLog.setChecked(check_isUse);
        if (check_isUse) {
            ServiceManager.systemApi.startModemLog(QxdmLogSave.QXDM_APP_CMD, 0, null);
        }
    }

    public void onQxdmlogClick(View view) {
        logd("onQxdmlogClick:" + qxdmLog.isChecked());
        saveBooleanSP(SP_CHECK_QXUSE, qxdmLog.isChecked());
        if (qxdmLog.isChecked()) {
            logd("onQxdmlog OPEN");
            ServiceManager.systemApi.startModemLog(QxdmLogSave.QXDM_APP_CMD, 0, null);
        } else {
            logd("onQxdmlog CLOSE");
            ServiceManager.systemApi.stopModemLog(QxdmLogSave.QXDM_APP_CMD, 0, null);
        }
    }

    public void onTcpdumpClick(View view){
        logd("onTcpdumpClick:" + tcpdumpLog.isChecked());
        saveBooleanSP(SP_CHECK_TCPDUMP, tcpdumpLog.isChecked());
        if(tcpdumpLog.isChecked()){
            Log.d(TAG, "onTcpdumpClick: tcp is checked");
            Configuration.INSTANCE.setTcpdumpEnable(true);
            TcpdumpHelper.getInstance().sendStartTcpdump(true);
        }else {
            Log.d(TAG, "onTcpdumpClick: tcp is not enabled");
            TcpdumpHelper.getInstance().sendStartTcpdump(false);
        }
    }

    public void onTcpdumpSelectInit(){
        SharedPreferences ucappSp = getSharedPreferences("ucappSp", 0);
        boolean check_isUse = ucappSp.getBoolean(SP_CHECK_TCPDUMP, Configuration.INSTANCE.getTcpdumpEnable());
        logd("onQxdmlogOpen setChecked:" + check_isUse);
        tcpdumpLog.setChecked(check_isUse);
        if (check_isUse) {
            Configuration.INSTANCE.setTcpdumpEnable(true);
        }
    }

    public void onQxdmlogCleanZip(View view) {
        logd("onQxdmlogCleanZip");
        ServiceManager.systemApi.clearModemLog(0,0, null);
        /*int afterSetPrefer = 0;
        boolean ret = false;
        TelephonyManager telemanager = TelephonyManager.from(ServiceManager.appContext);
        try {
            ret = telemanager.setPreferredNetworkType(ServiceManager.cloudSimEnabler.getCard().getSubId(),14);
            logd("onQxdmlogCleanZip1:" + ret);
            afterSetPrefer = TelephonyManager.getIntWithSubId(ServiceManager.appContext.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, ServiceManager.cloudSimEnabler.getCard().getSubId());
        } catch (Exception e) {
            logd("onQxdmlogCleanZip", e);
        }
        logd("onQxdmlogCleanZip2:" + afterSetPrefer);*/

    }
    //    class AutoRunListen implements AutoListen {
    //        @Override
    //        @WorkerThread
    //        public void newEvent(@NotNull RunStep currStep, @NotNull EventNotice newEvent) {
    //            logd("get newEvent currStep:" + currStep + ",EventNotice:" + newEvent);
    //            if (currStep == RunStep.FIRST_AUTH && newEvent == EventNotice.EV_CLOUDSIM_READY) {
    //                String text = "do set DDS in 10s!!";
    //                showToast(text);
    //            } else if (currStep == RunStep.FIRST_AUTH && newEvent == EventNotice.DDS_AT_CLOUDSIM) {
    //                showToast("DDS_AT_CLOUDSIM");
    //            }
    //        }
    //
    //        private void showToast(final String text) {
    //            InitActivity.this.runOnUiThread(new Runnable() {
    //                @Override
    //                public void run() {
    //                    Toast.makeText(InitActivity.this, text, Toast.LENGTH_SHORT).show();
    //                }
    //            });
    //        }
    //
    //        @Override
    //        public void getException(@NotNull Ept_Event exceptionEvent) {
    //        }
    //    }

    public void onSelectASSServer(View view) {
        saveBooleanSP(SP_CHECK_ASSUSE, debugServer.isChecked());
    }

    public void onStartUploadLogs(View view) {
        Intent intent = new Intent(this, UploadLog.class);
        startActivity(intent);
    }

    public void onSelectRPLMNTest(View view) {
        logd("onSelectRPLMNTest:" + debugRplmn.isChecked());
        saveBooleanSP(SP_CHECK_RPLMN, debugRplmn.isChecked());
        Configuration.INSTANCE.setOpenRplmnTest(debugRplmn.isChecked());
    }

    private String getPersentStr(int persent) {
        switch (persent) {
            case 5:
                return "in recovery...";
            case 10:
                return "seed network check...";
            case 15:
                return "seed network ok";
            case 20:
                return "polling server ip...";
            case 25:
                return "ip udpating...";
            case 30:
                return "ip update succ";
            case 35:
                return "login...";
            case 40:
                return "login succ";
            case 45:
                return "dispatch vsim...";
            case 50:
                return "dispatch vsim succ";
            case 55:
                return "download vsim bin...";
            case 60:
                return "download vsim bin succ";
            case 65:
                return "start up vsim...";
            case 70:
                return "vsim ready";
            case 75:
                return "vsim register...";
            case 80:
                return "start to send auth package...";
            case 81:
            case 82:
            case 83:
            case 84:
            case 85:
            case 86:
            case 87:
            case 88:
            case 89:
                return "recv auth reply count " + (persent - 80) + " ...";
            case 90:
                return "register ok, datacall...";
            case 100:
                return "vsim datacall ok!";
            default:
                return "";
        }
    }

    private String getSeedPersentStr(int persent) {
        switch (persent) {
            case 10:
                return "start seedsim...";
            case 20:
                return "insert seedsim ok";
            case 30:
                return "card ready";
            case 40:
                return "start auth...";
            case 50:
                return "auth reply...";
            case 60:
                return "card register ok";
            case 70:
                return "start dun datacall...";
            case 75:
                return "dun datacall over";
            case 80:
                return "datacall ok!";
            case 90:
                return "socket connecting...";
            case 100:
                return "socket ok!";
            default:
                return "";
        }
    }

    private Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_MSG_EVENT_SYS_PERSENT:
                    int persent = msg.arg1;
                    systemPersent.setText("System running " + persent + "% ... " + getPersentStr(persent));
//                    if (persent > 0 && persent < 100) {
//                        try {
//                            NoticeCtrl.getInstance().setMode(NoticeCtrl.RUNNING);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    } else if (persent == 100) {
//                        try {
//                            NoticeCtrl.getInstance().setMode(NoticeCtrl.ON);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    } else if (persent == 0) {
//                        try {
//                            NoticeCtrl.getInstance().setMode(NoticeCtrl.OFF);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    } else {
//                        Log.e(TAG, "handleMessage: unexcept percent:" + persent);
//                    }
                    break;
                case HANDLE_MSG_EVENT_SEED_PERSENT:
                    seedPersent.setText("Seedsim running " + msg.arg1 + "% ... " + getSeedPersentStr(msg.arg1));
                    break;
                case HANDLE_MSG_EVENT_ERROR:
                    systemError.setText("System last err:Code " + msg.arg1 + ", " + (String) msg.obj);
                    break;
                case HANDLE_MSG_EVENT_SYS_SUCC:
                    systemPersent.setText("System running OK! You can use cloudsim data!");
                    systemError.setText("");
//                    try {
//                        NoticeCtrl.getInstance().setMode(NoticeCtrl.ON);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
                    break;
                case HANDLE_MSG_EVENT_SYS_RESET:
                    systemPersent.setText("System logout! Code:" + msg.arg1 + ", " + (String) msg.obj);
                    seedPersent.setText("");
                    systemError.setText("");
                    seedError.setText("");
//                    try {
//                        NoticeCtrl.getInstance().setMode(NoticeCtrl.OFF);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
                    break;
                case HANDLE_MSG_EVENT_SEED_ERR:
                    seedError.setText("Seed card last err:Code " + msg.arg1 + ", " + (String) msg.obj);
                    break;
                case HANDLE_MSG_EVENT_MBN:
                    Toast.makeText(context, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    public void resetDDS(View v) {
        ConnectivityManager connectivityManager = ConnectivityManager.from(this.getApplicationContext());
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        mBuild = builder.build();

        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "onAvailable: ");
                super.onAvailable(network);
            }
        };
        connectivityManager.requestNetwork(mBuild, mNetworkCallback);
    }

    public void reStartRadio(View v) {
        if (mNetworkCallback!=null) {
            ConnectivityManager connectivityManager = ConnectivityManager.from(this.getApplicationContext());
            connectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
    }

    public void reStartRadioPower(View v) {
//        final TelephonyManager telephonyManager = TelephonyManager.from(this);
//        telephonyManager.setRadioPower(false);
//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                telephonyManager.setRadioPower(true);
//            }
//        }, 10000);
    }

    @Override
    public void onDestroy() {
        if (mAccessListener != null) {
            accessEntry.unregisterAccessStateListen(mAccessListener);
        }
        super.onDestroy();
    }
}

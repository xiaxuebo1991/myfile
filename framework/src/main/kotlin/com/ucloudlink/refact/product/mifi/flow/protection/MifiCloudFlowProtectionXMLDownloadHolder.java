package com.ucloudlink.refact.product.mifi.flow.protection;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.net.HttpReqHelper;
import com.ucloudlink.refact.business.flow.protection.CloudFlowProtectionMgr;
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.GetProCfgRequestData;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.OemProduct;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.ProCfgResponseData;
import com.ucloudlink.refact.utils.CloseUtils;
import com.ucloudlink.refact.utils.EncryptUtils;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.List;

public  class MifiCloudFlowProtectionXMLDownloadHolder{

    public final static String UCLOUD_U3C_DOWNLOAD_FILE_FLOW_PROTECTION_WORD     = "ucloud-u3c-flow-03-05_B";//glocalme-service-11-23_B

    public static final String UC_FLOWFILTER_FILE_NAME = "uc_flowfilter.conf";

    private static final String DEFAULT_URL = "http://uff.ucloudlink.com";
    //private static final String DEFAULT_URL_FILE = "/persist/ucloud/server_ip.cfg";
    //private static final String DEFAULT_URL_FILE = "/productinfo/ucloud/server_ip.cfg";
    private static final String DEFAULT_URL_FILE_NAME = "server_ip.cfg";
    private static final String KEY_DEFAULT_URL = "pro_data_ip";
    private static String BASE_URL = DEFAULT_URL;
    //private static final String KEY_PROP_IMEI = "persist.service.ext.imei";
    private static final String KEY_PRO_CFG_VERSION = "pro_cfg_version";
    private static final String DEFAULT_PRO_CFG_VERSION = "00.00.00";
    private static final String PROPERTY_BUILD_ID = "ro.build.display.id";

    //BW state key
    public static final String KEY_PRO_DATA_STATUS = "pro_data_status"; //这个记录的是底层最新的状态
    public static final String KEY_PRO_DATA_SET_STATE = "pro_data_set_state"; //这个记录的是下发的最新的状态
    public static final String KEY_PRO_DATA_SERVER_STATE = "pro_data_server_state"; //这个记录的是服务器最后一次下发的状态
    public static final String KEY_PRO_DATA_USER_STATE = "pro_data_user_state"; //这个记录的是用户最后一次下发的状态
    public static final String KEY_PRO_SYNC_SIGNAL = "pro_sync_signal"; //这个用于记录同步信号

    //流量防护 关闭0，打开1， 配置文件更新2.
    public static final int STATUS_FIRST = -1;
    public static final int STATE_DISABLE = 0;
    public static final int STATE_ENABLE = 1;
    public static final int STATE_CFG_CHANGE = 2;

    // xml配置项：getEconDataMode
    //0 默认开，每次开机以服务器配置为准
    //1 默认开，每次开机以用户设置为准
    //2 默认关，每次开机以服务器配置为准
    //3 默认关，每次开机以用户设置为准
    public static final int STRATEGY_ZERO = 0;
    public static final int STRATEGY_ONE = 1;
    public static final int STRATEGY_TWO = 2;
    public static final int STRATEGY_THREE = 3;

    private static final long MIN_CONFIG_CHECK_TIME = 4 * 60 * 60 * 1000;
    private static long lastCfgCheckTime = SystemClock.elapsedRealtime();
    private static boolean PRO_DATA_CONFIG_CHECKED = false;
    private static byte[] lockCfgChange = new byte[0];
    private String proDataVersion = "";

    private OemProduct oemProduct;
    private OemProduct getOemProductConfig() {
        //无需加锁，调用始终在同一个线程
        if(oemProduct == null){
            oemProduct = new OemProduct();
            oemProduct.initFromXml();
        }
        return oemProduct;
    }

    public static void setPRO_DATA_CONFIG_CHECKED(boolean ret){
        synchronized (lockCfgChange){
            PRO_DATA_CONFIG_CHECKED = ret;
        }
    }

    public void handleActionGetConfig(Context context, ICloudFlowProtectionCtrl cloudFlowProtectionCtrl){
        JLog.logi(MifiXMLUtils.TAG + ", handleActionGetConfig() -> PRO_DATA_CONFIG_CHECKED = "+ PRO_DATA_CONFIG_CHECKED);
        long curcfgCheckTime = SystemClock.elapsedRealtime();
        if(PRO_DATA_CONFIG_CHECKED && (curcfgCheckTime - lastCfgCheckTime < MIN_CONFIG_CHECK_TIME)){
            return;
        }
        setBaseUrl();
        GetProCfgRequestData data = intiRequestData(context);
        JLog.logi(MifiXMLUtils.TAG + ", data=" + data.toString());
        postProCfgRequest(data,cloudFlowProtectionCtrl);
    }

    private void setBaseUrl() {
        File file = null;
        try{
            //file = new File(DEFAULT_URL_FILE);
            String path = getUcFlowFilterFilePath() + File.separator + DEFAULT_URL_FILE_NAME;
            JLog.logi(MifiXMLUtils.TAG + ", server_ip.cfg.path = "+path);
            file = new File(path);
        }catch (Exception e){
            e.printStackTrace();
        }
        if (file==null || !file.exists() || file.isDirectory()) {
            JLog.logi(MifiXMLUtils.TAG + ", IpCfg File doesn't exist.");
        } else {
            JLog.logi(MifiXMLUtils.TAG + ", IpCfg File exist, read content begin.");
            try {
                InputStream instream = new FileInputStream(file);
                if (instream != null) {
                    InputStreamReader inputreader = new InputStreamReader(instream);
                    BufferedReader buffreader = new BufferedReader(inputreader);
                    String line;
                    //分行读取
                    while ((line = buffreader.readLine()) != null) {
                        String[] con = line.split("=");
                        if (con.length > 1) {
                            if (!Strings.isNullOrEmpty(con[0]) && !Strings.isNullOrEmpty(con[1])) {
                                if (con[0].equals(KEY_DEFAULT_URL)) {
                                    BASE_URL = con[1];
                                }
                            }
                        }
                    }
                    try{
                        instream.close();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                JLog.loge(MifiXMLUtils.TAG + ", setBaseUrl -> Exception: " + e.toString());
            }
        }
        JLog.logi(MifiXMLUtils.TAG + ", setBaseUrl() -> BASE_URL = "+ (BASE_URL==null?"null":BASE_URL));
    }

    private GetProCfgRequestData intiRequestData(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        GetProCfgRequestData data = new GetProCfgRequestData();

        try {
            String localImei = getExtImei(context);
            JLog.logi(MifiXMLUtils.TAG + ", Get local imei :" + localImei);
            data.setImei(localImei);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
           /* if (true) { //Note E1现在默认为GTBU，且暂时未有分辨是否为GTBU的方法
                data.setUsername("a");
            } else {
                data.setUsername(Configuration.INSTANCE.getUsername());
            }*/
            data.setUsername(Configuration.INSTANCE.getUsername());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (telephonyManager != null) {
            String mccmnc = telephonyManager.getNetworkOperator(); // 返回MCC + MNC
            if (mccmnc != null && mccmnc.length() > 3) {
                data.setMcc(mccmnc.substring(0, 3));
                data.setMnc(mccmnc.substring(3));
            }
        }

        useCellLocation(data, telephonyManager);//CardStateMonitor.updateCellInfo()

        data.setBu(getOemProductConfig().getId());
        data.setCfgVersion(SharedPreferencesUtils.getString(context, KEY_PRO_CFG_VERSION, DEFAULT_PRO_CFG_VERSION));
        data.setSoftVersion(getSystemVersion(context));
        return data;
    }

    public static String getExtImei(Context context) {
        String extImei = Configuration.INSTANCE.getImei(context);//SystemProperties.get(KEY_PROP_IMEI);
        if (extImei == null || extImei.equals("")) {
            extImei = "000000000000000";
        }
        return extImei;
    }

    /**
     * 设置cellid（小区识别码）
     * 设置lac（位置区号码）
     * @param data
     * @param telephonyManager
     */
    private static void useCellLocation(GetProCfgRequestData data, TelephonyManager telephonyManager) {

        List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
        if(cellInfos == null){
            JLog.loge( MifiXMLUtils.TAG + ", cellinfos is null");
        }else{
            int i = 0;
            int cellid = 0;
            int lac = 0;
            while (i < cellInfos.size()) {
                if (cellInfos.get(i).isRegistered()) {
                    if (cellInfos.get(i) instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma)cellInfos.get(i);
                        CellIdentityWcdma cellIdentityWcdma = cellInfoWcdma.getCellIdentity();

                        cellid = cellIdentityWcdma.getCid();
                        lac = cellIdentityWcdma.getLac();
                        data.setLac(lac + "");
                        data.setCellId(cellid + "");
                    } else if (cellInfos.get(i) instanceof CellInfoGsm) {
                        CellInfoGsm cellInfoGsm = (CellInfoGsm)cellInfos.get(i);
                        CellIdentityGsm cellIdentityGsm = cellInfoGsm.getCellIdentity();
                        cellid = cellIdentityGsm.getCid();
                        lac = cellIdentityGsm.getLac();
                        data.setLac(lac + "");
                        data.setCellId(cellid + "");
                    } else if (cellInfos.get(i) instanceof CellInfoLte) {
                        CellInfoLte cellInfoLte = (CellInfoLte)cellInfos.get(i);
                        CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();
                        cellid = cellIdentityLte.getCi();
                        lac = cellIdentityLte.getTac();
                        data.setLac(lac + "");
                        data.setCellId(cellid + "");
                    } else if (cellInfos.get(i) instanceof CellInfoCdma) {
                        CellInfoCdma cellInfoCdma = (CellInfoCdma)cellInfos.get(i);
                        CellIdentityCdma cellIdentityCdma = cellInfoCdma.getCellIdentity();
                        cellid = cellIdentityCdma.getBasestationId();
                        lac = cellIdentityCdma.getSystemId();
                        data.setLac(lac + "");
                        data.setCellId(cellid + "");
                    } else {
                        data.setLac(lac + "");
                        data.setCellId(cellid + "");
                    }
                }
                i++;
            }
        }
    }

    private static String getSystemVersion(Context context) {
        String currentVersion = "";

        try {
            ClassLoader classLoader = context.getClassLoader();
            Class cla = classLoader.loadClass("android.os.SystemProperties");
            Class[] clas = new Class[]{String.class};
            Method method = cla.getMethod("get", clas);
            currentVersion = (String)method.invoke(cla, new Object[]{PROPERTY_BUILD_ID});
        } catch (Exception e) {
            e.printStackTrace();
        }
        JLog.logi(MifiXMLUtils.TAG + ", currentVersion is " + currentVersion);
        return currentVersion;
    }

    private void postProCfgRequest(GetProCfgRequestData getIdRequestData,ICloudFlowProtectionCtrl cloudFlowProtectionCtrl) {
        String url = BASE_URL + "/checkBlacklist/version.do";
        JLog.logi(MifiXMLUtils.TAG + ", postProCfgRequest() -> url = " + url);

        String charsetName = "UTF-8";
        try {
            url = url + "?" + GetProCfgRequestData.IMEI + "=" + URLEncoder.encode(getIdRequestData.getImei(), charsetName) + "&"
                    + GetProCfgRequestData.USERNAME + "=" + URLEncoder.encode(getIdRequestData.getUsername(), charsetName) + "&"
                    + GetProCfgRequestData.MCC + "=" + URLEncoder.encode(getIdRequestData.getMcc(), charsetName) + "&"
                    + GetProCfgRequestData.MNC + "=" + URLEncoder.encode(getIdRequestData.getMnc(), charsetName) + "&"
                    + GetProCfgRequestData.LAC + "=" + URLEncoder.encode(getIdRequestData.getLac(), charsetName) + "&"
                    + GetProCfgRequestData.CELLID + "=" + URLEncoder.encode(getIdRequestData.getCellId(), charsetName) + "&"
                    + GetProCfgRequestData.BU + "=" + URLEncoder.encode(getIdRequestData.getBu(), charsetName) + "&"
                    + GetProCfgRequestData.CFG_VERSION + "=" + URLEncoder.encode(getIdRequestData.getCfgVersion(), charsetName) + "&"
                    + GetProCfgRequestData.SOF_TVERSION + "=" + URLEncoder.encode(getIdRequestData.getSoftVersion(), charsetName);
        }catch (Exception e){
            JLog.logi(MifiXMLUtils.TAG + ", postProCfgRequest() -> param encode error! Exception: " + e.toString());
            e.printStackTrace();
        }

        HttpURLConnection conn = null;
        String httpResponse = null;
        int responseCode = -1;
        ProCfgResponseData mProCfgResponseData = null;
        try{
            conn = HttpReqHelper.getHttpURLConnection(url);
            responseCode = conn.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_OK){
                setPRO_DATA_CONFIG_CHECKED(true);
            }
            httpResponse = HttpReqHelper.fromInStreamToString(conn.getInputStream());
        }catch (Exception e){
            JLog.logi(MifiXMLUtils.TAG + ", postProCfgRequest() -> getHttpURLConnection error! Exception: " + e.toString());
            e.printStackTrace();
        } finally {
            try{
                if(conn!=null){
                    conn.disconnect();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        //String httpResponse = HttpReqHelper.get(allUrl);
        JLog.logi(MifiXMLUtils.TAG + ", test postProCfgRequest() -> allUrl = " + url
                + ", responseCode = "+responseCode+ ", httpResponse = "+(httpResponse==null?"null":httpResponse));

        try {
            mProCfgResponseData = new Gson().fromJson(httpResponse, ProCfgResponseData.class);
        }catch (Exception e){
            JLog.logi(MifiXMLUtils.TAG + ", postProCfgRequest() -> fromJson error! Exception: " + e.toString());
        }

        if (mProCfgResponseData == null) {
            JLog.loge(MifiXMLUtils.TAG + ", postAdById onResponse AdInfoData == null");
            handlerStrategyDefault();
            return;
        }

        handlerGetXMLInfoSuccess(mProCfgResponseData,cloudFlowProtectionCtrl);

    }

    private void handlerStrategyDefault(){
        JLog.loge(MifiXMLUtils.TAG + ",did not get config file,use default config");
        boolean userState = SharedPreferencesUtils.getBoolean(ServiceManager.appContext, "web_flow_protection_enable", true);
        int userstate_new= 1;
        if (userState){
            userstate_new = 1;
        }else {
            userstate_new = 0;
        }

        String mode = getOemProductConfig().getEconDataMode();
        int strategy = STRATEGY_ZERO;
        try {
            strategy = Integer.parseInt(mode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        switch (strategy) {
            case STRATEGY_ONE:
            case STRATEGY_THREE: //以用户配置为准
                JLog.loge(MifiXMLUtils.TAG + ",Default status, Stragegy one or three ,it depence on web set");
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, userstate_new);
                break;
            case STRATEGY_ZERO:
                JLog.loge(MifiXMLUtils.TAG + ",Default status, Strategy zero, enable");
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, 1); //使用默认值，打開
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",true);  //web更新
                break;
            case STRATEGY_TWO:
                JLog.loge(MifiXMLUtils.TAG + ",Default status, Strategy two, disable");
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, 0); //使用默认值，关闭
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",false);  //web更新
                break;
        }
    }

    private void handlerGetXMLInfoSuccess(ProCfgResponseData data,ICloudFlowProtectionCtrl cloudFlowProtectionCtrl){
        if (data == null) {
            JLog.loge(MifiXMLUtils.TAG + ", handler XML info Success , data = null, return");
            return;
        }


        //记录服务器下发的状态，并发送流量防护是否enable的状态，下载完文件后再会通知下面。
        int state = data.getEnable();
        SharedPreferencesUtils.putInt(ServiceManager.appContext, KEY_PRO_DATA_SERVER_STATE, state);
        //后续增加需求，用户关闭时会有时间限制，时间过后，会再下发打开命令，所以，获取到服务器指令时得检测是不是还在用户设置的关闭时间段，如果是则不能处理。
        int oldState = SharedPreferencesUtils.getInt(ServiceManager.appContext, KEY_PRO_DATA_STATUS, STATUS_FIRST);
        JLog.logi(MifiXMLUtils.TAG + ", response=" + data.toString() + ", oldState=" + oldState+", state = "+state);

   //     int userState = SharedPreferencesUtils.getInt(ServiceManager.appContext, KEY_PRO_DATA_USER_STATE, STATUS_FIRST);
        boolean userState = SharedPreferencesUtils.getBoolean(ServiceManager.appContext, "web_flow_protection_enable", true);
        int userstate_new= 1;
        if (userState){
            userstate_new = 1;
        }else {
            userstate_new = 0;
        }
        String mode = getOemProductConfig().getEconDataMode();

        int first_enter =  SharedPreferencesUtils.getInt(ServiceManager.appContext, "pro_data_first_state", 0);

        int strategy = STRATEGY_ZERO;
        try {
            strategy = Integer.parseInt(mode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (0 == first_enter) { //如果是第一次进入该逻辑，查看oem配置，该逻辑一次开机只走一次
            JLog.logi(MifiXMLUtils.TAG + ", first enter =" + first_enter );
            switch (strategy) {
                case STRATEGY_ONE:   //策略1:以用户设置为准
                case STRATEGY_THREE: //策略3:以用户设置为准
                    SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, userstate_new);  //设置为1，enable
                    SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_SERVER_STATE, state);      //初始化状态
                    SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, userstate_new);
                    SharedPreferencesUtils.putInt(ServiceManager.appContext, "pro_data_first_state", 1);
                    JLog.logi(MifiXMLUtils.TAG + ", strategy one and three ,enable = 1"  );

                    //if (userState == STATUS_FIRST && state != oldState) { //用户没有下发命令，并且服务器下发指令和当前指令不同
                    //GlocalmeApplication.getInstance().getNetworkAccessService().setProDataCfg(state);
                    // setProDataCfg(state);      //将服务器指令设置为最新下发指令
                    //}
                    break;
                case STRATEGY_ZERO:
                case STRATEGY_TWO: //策略2， 以为服务器为准
                    if (0 == state) { //收到服务器disable
                        SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, 0);
                        SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_SERVER_STATE, 0);
                        SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, 0);
                        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",false);  //web更新
                        SharedPreferencesUtils.putInt(ServiceManager.appContext, "pro_data_first_state", 1);
                        JLog.logi(MifiXMLUtils.TAG + ", strategy zero ,disable"  );
                    }
                    if ( 1 == state){  //服务器指令为1，enable
                        SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, 1);
                        SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_SERVER_STATE, 1);
                        SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, 1);
                        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable", true);  //web更新
                        SharedPreferencesUtils.putInt(ServiceManager.appContext, "pro_data_first_state", 1);
                        JLog.logi(MifiXMLUtils.TAG + ", strategy zero ,enable"  );
                    }
                    break;
                default:
                    if (state != oldState) {
                        //GlocalmeApplication.getInstance().getNetworkAccessService().setProDataCfg(state);
                        setProDataCfg(state);
                    }
                    break;
            }
        }
        boolean isCfgExist = isCfgExist();
        boolean isLatestCfg = isLatestCfg(data.getVersion());

        if (!isCfgExist || !isLatestCfg) {
            String dataUrl = data.getUrl();
            JLog.logi(MifiXMLUtils.TAG + ", dataUrl=" + dataUrl+", isCfgExist = "+isCfgExist+", isLatestCfg = "+isLatestCfg);
            if (dataUrl != null && !dataUrl.equals("")) {
                String url = BASE_URL + dataUrl;
                JLog.logi(MifiXMLUtils.TAG + ", url=" + url);
                proDataVersion = data.getVersion();

                downloadProCfgFile(url);

            } else {
                CloudFlowProtectionMgr.Companion.handlerServiceEnableFlowProtection();
            }
        } else {
            JLog.logi(MifiXMLUtils.TAG + ", isCfgExist = " + isCfgExist + ", isLatestCfg = "+isLatestCfg);
            CloudFlowProtectionMgr.Companion.handlerServiceEnableFlowProtection();
        }
        update_flow_protection_state(data);

        int status = SharedPreferencesUtils.getInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, 0);
        int signal = SharedPreferencesUtils.getInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_SYNC_SIGNAL, 0);
        if (status == 1 && signal == 1) {
            cloudFlowProtectionCtrl.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(10)");
            cloudFlowProtectionCtrl.setRetrict("NetworkStateListen-CONNECTED-isNotEmpty(11)");
        } else {
            cloudFlowProtectionCtrl.clearRetrict("NetworkStateListen-CONNECTED-isNotEmpty(12)");
        }
        SharedPreferencesUtils.putInt(ServiceManager.appContext, MifiCloudFlowProtectionXMLDownloadHolder.KEY_PRO_SYNC_SIGNAL, 0);
    }

    public String getUcFlowFilterFilePath(){
//        return "/data/misc/wifi/";
        return ServiceManager.appContext.getFilesDir().getAbsolutePath();// + File.separator + "wifi";
    }

    private boolean isCfgExist() {
        String file = getUcFlowFilterFilePath() + File.separator + UC_FLOWFILTER_FILE_NAME;
        return isFileExist(file);
    }

    public static boolean isFileExist(String strFile) {
        try{
            File f = new File(strFile);
            if(!f.exists()){
                return false;
            }
        }catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean isLatestCfg(String version) {
        String v = SharedPreferencesUtils.getString(ServiceManager.appContext, KEY_PRO_CFG_VERSION);
        if (version != null && !version.equals("")) {
            if (v.equals(version)) {
                return true;
            }
        }
        return false;
    }

    private void downloadProCfgFile(String url) {
        //String path = "/data/misc/wifi";
        String path = getUcFlowFilterFilePath();
        String fileName = UC_FLOWFILTER_FILE_NAME;
        JLog.logi(MifiXMLUtils.TAG + ", path = " + path + ", fileName = "+fileName);

        File file = null;
        HttpURLConnection conn = null;
        String charsetName = "UTF-8";

        FileOutputStream fos = null;
        InputStream ins = null;
        BufferedInputStream bufferReader = null;
        int responseCode = -1;
        boolean isSucc = false;
        try{
            file = new File(path, fileName);
            conn = HttpReqHelper.getHttpURLConnection(url);
            conn.setRequestProperty("Charset", charsetName);
            conn.connect();
            responseCode = conn.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_OK){
                fos = new FileOutputStream(file);
                ins = conn.getInputStream();
                //int contentLength = conn.getContentLength();
                bufferReader = new BufferedInputStream(ins);
                int readLen;
                int readTotal = 0;
                byte[] bytes = new byte[1024];
                while ((readLen = bufferReader.read(bytes)) != -1) {
                    readTotal += readLen;
                    fos.write(bytes, 0, readLen);
                    JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download readTotal = " + readTotal + ", readLen = "+readLen);
                }
                fos.flush();
                isSucc = true;
            }
            JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download finish!  responseCode = "+responseCode);

        }catch (Exception e){
            JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> getHttpURLConnection error! Exception: " + e.toString());
            e.printStackTrace();
        } finally {
            try{
                if(conn!=null){
                    conn.disconnect();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            CloseUtils.closeIO(fos, ins, bufferReader);
        }

        try{
            if(isSucc && file!=null && file.exists()){
                if (!proDataVersion.equals("")) {
                    SharedPreferencesUtils.putString(ServiceManager.appContext, KEY_PRO_CFG_VERSION, proDataVersion);
                }
                //GlocalmeApplication.getInstance().getNetworkAccessService().setProDataCfg(STATE_CFG_CHANGE);
                setProDataCfg(STATE_CFG_CHANGE);
                String xml = MifiXMLUtils.readU3CFlowProtectionFromFile(file.getAbsolutePath());
                if(FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().getMICloudFlowProtectionCtrl()!=null){
                    FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().getMICloudFlowProtectionCtrl().updateXML(xml);
                    CloudFlowProtectionMgr.Companion.handlerServiceEnableFlowProtection();
                }

                String encodeRet = EncryptUtils.encyption(ServiceManager.appContext, xml
                        , MifiXMLUtils.UCLOUD_U3C_FLOW_PROTECTION_SP_NAME, UCLOUD_U3C_DOWNLOAD_FILE_FLOW_PROTECTION_WORD);
                JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download finish encodeRet: len = " + (encodeRet==null?0:encodeRet.length()));

                MifiXMLUtils.writeToFilesDir(file.getAbsolutePath(), encodeRet);
            }
        }catch (Exception e){
            JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download finish handler error! Exception: " + e.toString());
            e.printStackTrace();
        }

    }

    /**
     * BW
     * 可以看成所有设置流量防护状态的入口
     * 记录设置的最后状态
     * 重置需要重试的标识
     *
     * @param state
     */
    public void setProDataCfg(int state) {
        JLog.logi(MifiXMLUtils.TAG + ", setProDataCfg state=" + state);
        //glocalmeServiceDelegate.setProDataCfg(state);
        SharedPreferencesUtils.putInt(ServiceManager.appContext, KEY_PRO_DATA_SET_STATE, state);
        //DownCfgService.PRO_DATA_SET_TWICE = false;
    }

    public void update_flow_protection_state(ProCfgResponseData data ){
        JLog.logi(MifiXMLUtils.TAG + ", here is upate flow protection state"  );
        int first_enter =  SharedPreferencesUtils.getInt(ServiceManager.appContext, "pro_data_first_state", 0);
        JLog.logi(MifiXMLUtils.TAG + ",check first state"+ first_enter );

        int status_local= SharedPreferencesUtils.getInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, 0);
        boolean userState_get = SharedPreferencesUtils.getBoolean(ServiceManager.appContext, "web_flow_protection_enable", true);
        int user_local=SharedPreferencesUtils.getInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, 1);
        int service_new = data.getEnable();
        int service_local= SharedPreferencesUtils.getInt(ServiceManager.appContext,KEY_PRO_DATA_SERVER_STATE, 0);
        int user_new;
        if(userState_get){
            user_new = 1;
        }else {
            user_new = 0;
        }
        if ( (user_new != user_local)&& ( service_new == service_local)){ //获取的web指令和本地存储的指令不同，代表web指令出现了更新
            JLog.logi(MifiXMLUtils.TAG + ", web command changed"  );
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, user_new);
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_SYNC_SIGNAL, 1); //设置同步信号
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, user_new);
        }
        if (( service_new != service_local)&& (user_new == user_local)){ //获取的service指令和本地存储的指令不同，代表service指令出现了更新
            JLog.logi(MifiXMLUtils.TAG + ", service command changed"  );
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, service_new);
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_SYNC_SIGNAL, 1); //设置同步信号
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_SERVER_STATE, service_new);
            SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, service_new);
            if(service_new == 1) {
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",true);  //web更新
            }else {
                SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",false);  //web更新
            }
        }
        if (( service_new != service_local)&& (user_new != user_local)){ //sevice 和 web 都出现了更新，正常不应该走到这里
            JLog.logi(MifiXMLUtils.TAG + ", both of service and web changed"  );
 //           SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, user_new);
            if(service_new != status_local) {
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, service_new);
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_SYNC_SIGNAL, 1); //设置同步信号
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_SERVER_STATE, service_new);
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, service_new);
                if(service_new == 1) {
                    SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",true);  //web更新
                }else {
                    SharedPreferencesUtils.putBoolean(ServiceManager.appContext, "web_flow_protection_enable",false);  //web更新
                }
            }
            if (user_new != status_local) {
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_STATUS, user_new);
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_SYNC_SIGNAL, 1); //设置同步信号
                SharedPreferencesUtils.putInt(ServiceManager.appContext,KEY_PRO_DATA_USER_STATE, user_new);
            }
        }
    }

}

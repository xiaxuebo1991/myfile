package com.ucloudlink.refact.product.mifi.downloadcfg;

import android.app.IntentService;
import android.content.Context;
import android.telephony.TelephonyManager;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.net.HttpReqHelper;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.product.mifi.flow.protection.MifiXMLUtils;
import com.ucloudlink.refact.utils.CloseUtils;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;


/**
 * 业务逻辑说明：
 * BW 流量防护：根据设备上报的imei号的归属（例如归属org，mvo等）来确定相应的配置文件，然后将url传给终端下载
 * ROAM 实体卡漫游列表：根据设备上报的imei号的归属（例如归属org，mvo等）来确定相应的配置文件，然后将url传给终端下载
 * FOAMFEE 种子卡漫游资费列表：根据上报的种子卡的imsi号或iccid号来确定相应的配置文件，然后将url传给终端下载
 */
public class DownCfgService {

    public static final String ACTION_DOWNCFG = "com.glocalme.service.events.action.DOWNCFG";
    private static final String LOG_TAG = DownCfgUtil.TAG + "Service";
    private static String BASE_URL = DownCfgUtil.DEFAULT_URL;
    private static final String FUNTYPE_OEMCFG = "OEMCFG"; //设备定制文件
    private static final String KEY_OEM_CFG_VERSION = "oem_cfg_version";


//    //流量防护 关闭0，打开1， 配置文件更新2.
//    public static final int STATUS_FIRST = -1;
//    public static final int STATE_DISABLE = 0;
//    public static final int STATE_ENABLE = 1;
//    public static final int STATE_CFG_CHANGE = 2;


    private static final String DEFAULT_PRO_CFG_VERSION = "0000000000";
    private static final String OEMCFG_CFG_PATH = "/data/ucloud/cfg/oem/";
    private static final String OEMCFG_CFG_FILENAME = "oem_config.cfg";
    private static final String PROPERTY_BUILD_ID = "ro.build.display.id";
    private static final String KEY_PRO_CFG_VERSION = "pro_omecfg_version";

    //检查配置文件错误，则重试一次。
    public static boolean DOWN_CFG_CHECKED = false;
    private static byte[] lockCfgChange = new byte[0];
    private static boolean PRO_DATA_CONFIG_CHECKED = false;
    private String proDataVersion = "";
    private String OME_CFG_DEFAULT_PATH = "/productinfo/ucloud/oem_config.cfg";


    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */

    public void onHandleIntent() {
        handleActionGetConfig(ServiceManager.appContext);
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionGetConfig(Context context) {
        BASE_URL = DownCfgUtil.setBaseUrl();
        final DownCfgReqData data = intiRequestData(context);
        JLog.logd(LOG_TAG + " data=" + data.toString());
        new Thread(new Runnable() {
            @Override
            public void run() {
                postProCfgRequest(data);
            }
        }).start();
    }


    private DownCfgReqData intiRequestData(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        DownCfgReqData data = new DownCfgReqData();

        try {
            String localImei = getExtImei(context);
            JLog.logi(MifiXMLUtils.TAG + ", Get local imei :" + localImei);
            data.setImei(localImei);
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
        //OEMCFG 设备定制文件
        String oemCfgVer = SharedPreferencesUtils.getString(ServiceManager.appContext, KEY_OEM_CFG_VERSION, DEFAULT_PRO_CFG_VERSION);
        ReqListData OEMCFG = new ReqListData(FUNTYPE_OEMCFG, getExtImei(context), oemCfgVer);
        data.addFunReqListDatas(OEMCFG);
        return data;
    }

    public static String getExtImei(Context context) {
        String extImei = Configuration.getImei(context);//SystemProperties.get(KEY_PROP_IMEI);
        if (extImei == null || extImei.equals("")) {
            extImei = "000000000000000";
        }
        return extImei;
    }

    /**
     * 获取配置文件信息
     *
     * @param getIdRequestData
     */
    private void postProCfgRequest(DownCfgReqData getIdRequestData) {
        String strUrl = BASE_URL + "/bss/terminalfunction/noauth/QueryTmlFuncVersionListInfo";

        JLog.logi(MifiXMLUtils.TAG + ",postProCfgRequest，Request->" + strUrl);

        HttpURLConnection conn = null;
        String httpResponse = null;
        int responseCode = -1;
        DownCfgRespData mProCfgResponseData = null;
        try {
            Gson gs = new Gson();
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20 * 1000);
            conn.setReadTimeout(20 * 1000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json"); // 设置接收数据的格式
            conn.setRequestProperty("Content-Type", "application/json"); // 设置发送数据的格式
            conn.connect();
            OutputStreamWriter out = new OutputStreamWriter(
                    conn.getOutputStream(), "UTF-8"); // utf-8编码
            out.append(gs.toJson(getIdRequestData));
            out.flush();
            out.close();
            responseCode = conn.getResponseCode();
            JLog.logd("responseCode == " + responseCode);
            JLog.logd("object == " + conn.getContent().toString());
            if (responseCode == HttpURLConnection.HTTP_OK) {
                setPRO_DATA_CONFIG_CHECKED(true);
            }
            httpResponse = HttpReqHelper.fromInStreamToString(conn.getInputStream());
        } catch (Exception e) {
            JLog.logi(MifiXMLUtils.TAG + ", postProCfgRequest() -> getHttpURLConnection error! Exception: " + e.toString());
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        JLog.logd("postProCfgRequest json: " + httpResponse);
        try {
            mProCfgResponseData = new Gson().fromJson(httpResponse, DownCfgRespData.class);
        } catch (Exception e) {
            JLog.logi(MifiXMLUtils.TAG + ", postProCfgRequest() -> fromJson error! Exception: " + e.toString());
        }

        if (mProCfgResponseData == null) {
            JLog.loge(MifiXMLUtils.TAG + ", postAdById onResponse AdInfoData == null");
            return;
        }

        handlerGetXMLInfoSuccess(mProCfgResponseData);
    }

    public static void setPRO_DATA_CONFIG_CHECKED(boolean ret) {
        synchronized (lockCfgChange) {
            PRO_DATA_CONFIG_CHECKED = ret;
        }
    }

    private void handlerGetXMLInfoSuccess(DownCfgRespData data) {
        if (data == null) {
            JLog.loge(MifiXMLUtils.TAG + ", handler XML info Success , data = null, return");
            return;
        }

        JLog.logd(MifiXMLUtils.TAG + ",handlerGetXMLInfoSuccess start");
        List<RespListData> dataList = data.getRespListDatas();
        boolean isLatestCfg = true;
        boolean isCfgExist = isOEMCfgExist();
        for (int i = 0; i < dataList.size(); i++) {
            if (dataList.get(i).getFunType().equals(FUNTYPE_OEMCFG)) {
                String version = dataList.get(i).getVersion();
                JLog.logi(MifiXMLUtils.TAG + ", OEMCFG Version=" + version);
                isLatestCfg = isOEMLatestCfg(version);
                if (!isCfgExist || !isLatestCfg) {
                    String dataUrl = dataList.get(i).getResUrl();
                    JLog.logi(MifiXMLUtils.TAG + ", dataUrl=" + dataUrl + ", isCfgExist = " + isCfgExist + ", isLatestCfg = " + isLatestCfg);
                    if (dataUrl != null && !dataUrl.equals("")) {
                        proDataVersion = version;
                        downloadProCfgFile(dataUrl);
                    }
                } else {
                    JLog.logi(MifiXMLUtils.TAG + ", isCfgExist = " + isCfgExist + ", isLatestCfg = " + isLatestCfg);
                }
            }
        }
    }

    private void downloadProCfgFile(String url) {
        String path = OEMCFG_CFG_PATH;
        String fileName = OEMCFG_CFG_FILENAME;
        JLog.logi(MifiXMLUtils.TAG + ", path = " + path + ", fileName = " + fileName);

        File file = null;
        HttpURLConnection conn = null;
        String charsetName = "UTF-8";

        FileOutputStream fos = null;
        InputStream ins = null;
        BufferedInputStream bufferReader = null;
        int responseCode = -1;
        boolean isSucc = false;
        try {
            file = new File(path, fileName);
            conn = HttpReqHelper.getHttpURLConnection(url);
            conn.setRequestProperty("Charset", charsetName);
            conn.connect();
            responseCode = conn.getResponseCode();
            JLog.logd("download file responseCode == " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
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
                    JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download readTotal = " + readTotal + ", readLen = " + readLen);
                }
                fos.flush();
                isSucc = true;
            }
            JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download finish!  responseCode = " + responseCode);

        } catch (Exception e) {
            JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> getHttpURLConnection error! Exception: " + e.toString());
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            CloseUtils.closeIO(bufferReader, ins, fos);
        }

        try {
            if (isSucc && file != null && file.exists()) {
                boolean copyResults = copyFile(OEMCFG_CFG_PATH + OEMCFG_CFG_FILENAME, OME_CFG_DEFAULT_PATH);
                if (copyResults && !proDataVersion.equals("")) {
                    SharedPreferencesUtils.putString(ServiceManager.appContext, KEY_OEM_CFG_VERSION, proDataVersion);
                }
            }
        } catch (Exception e) {
            JLog.logi(MifiXMLUtils.TAG + ", downloadProCfgFile() -> download finish handler error! Exception: " + e.toString());
            e.printStackTrace();
        }

    }

    private boolean isOEMLatestCfg(String version) {
        String v = SharedPreferencesUtils.getString(ServiceManager.appContext, KEY_OEM_CFG_VERSION);
        if (!Strings.isNullOrEmpty(version)) {
            if (v.equals(version)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOEMCfgExist() {
        String file = OEMCFG_CFG_PATH + OEMCFG_CFG_FILENAME;
        return DownCfgUtil.isFileExist(file);
    }

    public boolean copyFile(String oldPath, String newPath) {
        JLog.logd("copy file");
        InputStream inStream = null;
        FileOutputStream fs = null;
        boolean re = false;
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时
                inStream = new FileInputStream(oldPath); //读入原文件
                fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                int length;
                while ((byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; //字节数 文件大小
                    System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
            }
            re = true;
        } catch (Exception e) {
            JLog.logd("copy file error == " + e);
            e.printStackTrace();
        } finally {
            CloseUtils.closeIO(inStream, fs);
        }

        return re;
    }
}

package com.ucloudlink.refact.product.mifi.downloadcfg;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemProperties;

//import com.glocalme.glocalmeservice.GlocalmeApplication;
//import com.glocalme.glocalmeservice.data.config.DataChannelConfig;
//import com.glocalme.glocalmeservice.util.Timber;
import com.google.common.base.Strings;
import com.ucloudlink.refact.utils.JLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by ouyangjunchao on 2016/12/8.
 */
public class DownCfgUtil {

    public static final String TAG = "DownCfg_";
    public static  String DEFAULT_URL = "https://saas.ucloudlink.com";
//    public static final String DEFAULT_URL = "https://saas3.ukelink.com";
    private static final String DEFAULT_URL_FILE = "/productinfo/ucloud/downcfg_server_ip.cfg";
    private static final String KEY_DEFAULT_URL = "downcfg_server_ip";
    private static final String KEY_PROP_IMEI = "persist.service.ext.imei";
    private static final String KEY_PROP_IMSI = "persist.service.ext.imsi";

    public static String setBaseUrl() {
        String baseUrl = DEFAULT_URL;
        File file = new File(DEFAULT_URL_FILE);
        if (!file.exists() || file.isDirectory()) {
            JLog.logd(TAG + " IpCfg File doesn't exist.");
        } else {
            try {
                InputStream instream = new FileInputStream(file);
                if (instream != null) {
                    InputStreamReader inputreader = new InputStreamReader(instream);
                    BufferedReader buffreader = new BufferedReader(inputreader);
                    String line;
                    //分行读取
                    while ((line = buffreader.readLine()) != null) {
                        JLog.logd(TAG + ", UrlFile line=" + line);
                        String[] con = line.split("=");
                        if (con.length > 1) {
                            if (!Strings.isNullOrEmpty(con[0]) && !Strings.isNullOrEmpty(con[1])) {
                                if (con[0].equals(KEY_DEFAULT_URL)) {
                                    baseUrl = con[1];
                                }
                            }
                        }
                    }
                    instream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return baseUrl;
    }

//    public static boolean needToDownCfg(Context context) {
//        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//        NetworkInfo ni = cm.getActiveNetworkInfo();
//        if (ni != null && ni.isConnected() && needToCheckCfg()) {
//            return true;
//        }
//        return false;
//    }

//    private static boolean needToCheckCfg() {
//        int dataChannel = GlocalmeApplication.getInstance().getConfiguration().getCurrentDataChannel();
//        if (dataChannel == DataChannelConfig.VSIM && !DownCfgService.DOWN_CFG_CHECKED) {
//            return true;
//        }
//        return false;
//    }

    public static String getExtImei() {
        String extImei = SystemProperties.get(KEY_PROP_IMEI);
        if (extImei == null || extImei.equals("")) {
            extImei = "000000000000000";
        }
        return extImei;
    }

    public static String getExtImsi() {
        String extImsi = SystemProperties.get(KEY_PROP_IMSI);
        return extImsi;
    }

    /**
     * 如果目录存在则清除目录下的文件
     * 如果目录不存在则新建目录
     * @param path
     */
    public static void initFolder(String path) {
        try{
            File folder = new File(path);
            if(!folder.exists() || folder.isFile()){
                folder.mkdirs();
            } else {
                deleteChildFile(path);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 递归删除子文件
     * @param path    要删除的根目录
     */
    public static void deleteChildFile(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            File[] childFile = file.listFiles();
            if (childFile == null || childFile.length <= 10) {
                return;
            }
            for (File f : childFile) {
                deleteFile(f.getPath());
            }
        }
    }


    /**
     * 递归删除文件和文件夹
     * @param path    要删除的根目录
     */
    public static void deleteFile(String path) {
        File file = new File(path);
        if (file.isFile()) {
            file.delete();
            return;
        }
        if (file.isDirectory()) {
            File[] childFile = file.listFiles();
            if (childFile == null || childFile.length == 0) {
                file.delete();
                return;
            }
            for (File f : childFile) {
                deleteFile(f.getPath());
            }
            file.delete();
        }
    }

    public static boolean isFileExist(String strFile) {
        try {
            File f = new File(strFile);
            if (!f.exists()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static String getOldRFListFilename(String filepath) {
        String filename = "";
        if (!Strings.isNullOrEmpty(filepath)) {
            try {
                File f = new File(filepath);
                File[] fs = f.listFiles();
                if (fs.length > 0) {
                    filename = fs[0].getName();
                }
            } catch (Exception e) {

            }
        }
        return filename;
    }
}

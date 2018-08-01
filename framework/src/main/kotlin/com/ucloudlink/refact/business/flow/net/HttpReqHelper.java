package com.ucloudlink.refact.business.flow.net;

import com.ucloudlink.refact.utils.CloseUtils;
import com.ucloudlink.refact.utils.JLog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by jianguo.he on 2018/3/19.
 */

public class HttpReqHelper {


    public static final HttpURLConnection getHttpURLConnection(String urlStr) throws Exception{
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20 * 1000);
        conn.setReadTimeout(20 * 1000);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        return conn;
    }


    public static final String get(String urlStr) {
        long startTime = System.currentTimeMillis();
        try {
            JLog.logi("request url：" + (urlStr==null?"null":urlStr));
            HttpURLConnection conn = getHttpURLConnection(urlStr);
            String result = fromInStreamToString(conn.getInputStream());
            JLog.logi("request url：" + (urlStr==null?"null": urlStr)
                    + ", response time:" + (System.currentTimeMillis() - startTime) + " (ms)"
                    + " \n, return: " + result);
            return result;
        } catch (Exception e) {
            JLog.logi("request error! url: "+(urlStr==null?"null":urlStr));
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 从InputStream中获取byte数组
     */
    public static final byte[] fromInStreamToByte(InputStream in) {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }
            CloseUtils.closeIO(in);
            return outStream.toByteArray();
        } catch (Exception e) {
            JLog.loge("InputStream as byte[] error! return 0 byte");
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * 从InputStream中获取字符串
     */
    @SuppressWarnings("unused")
    private static final String fromInStreamToString2(InputStream in) {
        try {
            return new String(fromInStreamToByte(in), "UTF-8");
        } catch (Exception e) {
            JLog.loge("InputStream as String error! cann't find UTF-8");
            e.printStackTrace();
            return new String(fromInStreamToByte(in));
        }
    }

    /**
     * 从InputStream中获取字符串
     */
    public static final String fromInStreamToString(InputStream in) {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }
            CloseUtils.closeIO(in);
            return new String(outStream.toByteArray(), "UTF-8");
        } catch (Exception e) {
            JLog.loge("InputStream as byte[] error! return 0 byte");
            e.printStackTrace();
            return null;
        }
    }

}

package com.ucloudlink.refact.product.mifi.flow.protection;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionItem;
import com.ucloudlink.refact.product.mifi.flow.protection.entity.MifiCloudFlowProtectionXML;
import com.ucloudlink.refact.utils.CloseUtils;
import com.ucloudlink.refact.utils.EncryptUtils;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by jianguo.he on 2018/2/5.
 */

public class MifiXMLUtils {

    public static final String TAG = "CloudFlowProtectionLog";

    public static final int U3C_XML_PARSE_INT_ERROR_VALUE = - (65535 - 10000);
    public static final int U3C_XML_DEF_DPORT = 53;
    public static final String U3C_XML_DEF_PROTOCOL = "udp";

    public final static String SPRD_U3C_CUR_UCLOUD_IFNAME_SP_KEY = "sprd_u3c_cur_ucloud_ifname";
    public final static String UCLOUD_U3C_FLOW_PROTECTION_WORD     = "ucloud-u3c-flow-02-05_B";//glocalme-service-11-23_B
    public static final String UCLOUD_U3C_FLOW_PROTECTION_SP_NAME  = "ucloud_u3c_flow_protection";
    public static final String UCLOUD_U3C_FLOW_PROTECTION_SP_KEY   = "ucloud_u3c_flow_protection_key";


    public static final String getU3CFlowProtectionFilePath(){
        return File.separator + "productinfo"+File.separator+"ucloud"+File.separator+"uc_flowfilter.conf";
    }

    public static final String readU3CFlowProtectionDefaultFile(){
        JLog.loge("CloudFlowProtectionLog", "readU3CFlowProtectionDefaultFile() -> begin ++");
        return readU3CFlowProtectionFromFile(MifiXMLUtils.getU3CFlowProtectionFilePath());

    }

    final public static String readU3CFlowProtectionFromFile(File file){
        String ret = "";
        if(file!=null && file.exists()){
            JLog.logi("CloudFlowProtectionLog", "readU3CFlowProtectionFromFile(file) -> file.path = " + (file.getAbsolutePath()));
            InputStream instream = null;
            InputStreamReader inputreader = null;
            BufferedReader buffreader = null;
            try{
                // Note 是否需要先copy再读
//                    String dataPath = Environment.getDataDirectory().getAbsolutePath();
//                    if(FileUtils.copyFile(path, dataPath)){}
                instream = new FileInputStream(file);
                if (instream != null){
                    inputreader = new InputStreamReader(instream);
                    buffreader = new BufferedReader(inputreader);
                    String line;
                    //分行读取
                    while (( line = buffreader.readLine()) != null) {
                        ret += line + "\n";
                    }
                    JLog.loge("CloudFlowProtectionLog", "readU3CFlowProtectionDefaultFile() -> ret.len = "+(ret==null ? 0 : ret.length()));
                    return ret;

                }
            }catch (Exception e){
                JLog.logi("CloudFlowProtectionLog","getFileContent() -> Error: " + e.toString());
            } finally {
                CloseUtils.closeIO(instream, inputreader, buffreader);
            }
        } else {
            JLog.logi("CloudFlowProtectionLog", "readU3CFlowProtectionFromFile(file) -> file not exist!");
        }
        return ret;
    }

    final public static String readU3CFlowProtectionFromFile(String path){
        JLog.logi("CloudFlowProtectionLog", "readU3CFlowProtectionFromFile() -> path = " + (path==null?"null":path));
        File file = null;
        try{
            file = new File(path);
        }catch (Exception e){
            e.printStackTrace();
        }
        return readU3CFlowProtectionFromFile(file);
    }

    /**
     * 写入data/data/file目录下
     * @param context
     * @param xml
     * @param filename
     */
    public static void writeToDataFile(Context context, String xml, String filename){
        FileOutputStream outputStream = null;
        try {
            outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(xml.getBytes());
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(outputStream!=null){
                try{
                    outputStream.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public static void writeToFilesDir(String path, String xml){
        JLog.logi("CloudFlowProtectionLog,  writeToFilesDir() -> path = "+(path==null?"null":path));
        FileOutputStream fos = null;
        try{
            File file = new File(path);
            fos = new FileOutputStream(file);
            fos.write(xml.getBytes("UTF-8"));
        }catch (Exception e){
            e.printStackTrace();
            JLog.logi("CloudFlowProtectionLog,  writeToFilesDir() -> Exception : "+e.toString());
        } finally {
            CloseUtils.closeIO(fos);
        }
    }

    public static String readFromDataFile(Context context, String filename){
        String ret = "";
        FileInputStream inStream = null;
        try{
            inStream = context.openFileInput(filename);
            int lenght = inStream.available();
            byte[] buffer = new byte[lenght];
            inStream.read(buffer);
            ret = new String(buffer, "UTF-8");
            JLog.loge("CloudFlowProtectionLog,  readFromDataFile(1) -> ret = "+ret);
            return ret;

        } catch (Exception e){
            e.printStackTrace();
            JLog.loge("CloudFlowProtectionLog,  readFromDataFile(-1) -> Exception: "+e.toString());
        } finally {
            CloseUtils.closeIO(inStream);
        }
        JLog.logi(MifiXMLUtils.TAG + ", readFromDataFile(-1)  ret = " + ret);
        return ret;
    }

    public static final MifiCloudFlowProtectionXML getU3CFlowProtectionXML(String xml){
        MifiCloudFlowProtectionXML mXMLU3CFlowProtection = null;
        ArrayList<MifiCloudFlowProtectionItem> list = new ArrayList<>();
        if(!TextUtils.isEmpty(xml)){
            mXMLU3CFlowProtection = new MifiCloudFlowProtectionXML();
            mXMLU3CFlowProtection.listFlowname = new ArrayList<>();
            mXMLU3CFlowProtection.listItem = new ArrayList<>();
            MifiCloudFlowProtectionItem mU3CFlowProtectionItem = null;
            String[] xmlLineContext = xml.split("\n");
            if(xmlLineContext!=null && xmlLineContext.length > 0){
                JLog.logi("CloudFlowProtectionLog","getXMLU3CFlowProtection() -> size = " + xmlLineContext.length);
                JLog.logd("CloudFlowProtectionLog","getXMLU3CFlowProtection() -> " + xmlLineContext.toString());
                int len = xmlLineContext.length;
                String lineContent;
                String tempStr;
                for(int i = 0 ; i < len ; i++){
                    lineContent = xmlLineContext[i];
                    if(TextUtils.isEmpty(lineContent)){
                       continue;
                    }

                    if(lineContent.startsWith("version=")){
                        mXMLU3CFlowProtection.version = lineContent.substring("version=".length());
                    } else if(lineContent.startsWith("[")){
                        tempStr = lineContent.substring("[".length(), lineContent.lastIndexOf("]"));
                        if(!TextUtils.isEmpty(tempStr)){
                            mXMLU3CFlowProtection.listFlowname.add(tempStr);
                        }
                    } else if(lineContent.startsWith("ruleid=")){
                        mU3CFlowProtectionItem = new MifiCloudFlowProtectionItem();
                        try{
                            mU3CFlowProtectionItem.ruleid = Integer.parseInt(lineContent.substring("ruleid=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse ruleid error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.ruleid = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }

                    } else if(lineContent.startsWith("flowname=")) {
                        mU3CFlowProtectionItem.flowname = lineContent.substring("flowname=".length());
                    } else if(lineContent.startsWith("sip=")){
                        mU3CFlowProtectionItem.sip = lineContent.substring("sip=".length());
                    } else if(lineContent.startsWith("dip=")){
                        mU3CFlowProtectionItem.dip = lineContent.substring("dip=".length());
                    } else if(lineContent.startsWith("sport=")){
                        try{
                            mU3CFlowProtectionItem.sport = Integer.parseInt(lineContent.substring("sport=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse sport error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.sport = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("dport=")){
                        try{
                            mU3CFlowProtectionItem.dport = Integer.parseInt(lineContent.substring("dport=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse dport error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.dport = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("protocol=")){
                        mU3CFlowProtectionItem.protocol_type = lineContent.substring("protocol=".length());
                    } else if(lineContent.startsWith("actiontype=")){
                        try{
                            mU3CFlowProtectionItem.actiontype = Integer.parseInt(lineContent.substring("actiontype=".length()));
                            mXMLU3CFlowProtection.listItem.add(mU3CFlowProtectionItem);
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse actiontype error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.actiontype = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("dnsreq=")){
                        mU3CFlowProtectionItem.dnsreq = lineContent.substring("dnsreq=".length());
                    } else if(lineContent.startsWith("dnsreqlen=")){
                        try{
                            mU3CFlowProtectionItem.dnsreqlen = Integer.parseInt(lineContent.substring("dnsreqlen=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse dnsreqlen error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.dnsreqlen = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("cmpmode=")){
                        try{
                            mU3CFlowProtectionItem.cmpmode = Integer.parseInt(lineContent.substring("cmpmode=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse cmpmode error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.cmpmode = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("speedlimit=")){
                        try{
                            mU3CFlowProtectionItem.speedlimit = Integer.parseInt(lineContent.substring("speedlimit=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse speedlimit error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.speedlimit = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("dnsreqdone=")){
                        try{
                            mU3CFlowProtectionItem.dnsreqdone = Integer.parseInt(lineContent.substring("dnsreqdone=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse dnsreqdone error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.dnsreqdone = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("learnip=")){
                        try{
                            mU3CFlowProtectionItem.learnip = Integer.parseInt(lineContent.substring("learnip=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse learnip error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.learnip = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    } else if(lineContent.startsWith("trytimes=")){
                        try{
                            mU3CFlowProtectionItem.trytimes = Integer.parseInt(lineContent.substring("trytimes=".length()));
                        } catch (Exception e){
                            JLog.loge("CloudFlowProtectionLog","parse trytimes error: lineContent = "+lineContent);
                            mU3CFlowProtectionItem.trytimes = U3C_XML_PARSE_INT_ERROR_VALUE;
                        }
                    }

                }

                JLog.logi("CloudFlowProtectionLog","getXMLU3CFlowProtection() -> "+ mXMLU3CFlowProtection.toString());

            } else {
                JLog.logi("CloudFlowProtectionLog","getXMLU3CFlowProtection() -> xmlLineContext = null");
            }
        }

        return mXMLU3CFlowProtection;
    }

    public static File createNewFile(File file){
        if(file!=null && !file.exists()){
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                file = null;
            }
        }
        return file;
    }

    public static boolean bytesToFile(byte[] data,String path){
        FileOutputStream outStream = null;
        try{
            File file = new File(path);
            createNewFile(file);
            outStream = new FileOutputStream(file);
            outStream.write(data);
            return true;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        } finally {
            try{
                if(outStream!=null){
                    outStream.close();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    final public static MifiCloudFlowProtectionXML readDefaultFile(){
        // 从默认文件读
        String defaultXML = MifiXMLUtils.readU3CFlowProtectionDefaultFile();
        MifiCloudFlowProtectionXML mU3CFlowProtectionXML = MifiXMLUtils.getU3CFlowProtectionXML(defaultXML);
        return mU3CFlowProtectionXML;
    }

    final public static void saveToSP(MifiCloudFlowProtectionXML mU3CFlowProtectionXML){
        if(mU3CFlowProtectionXML==null){
            SharedPreferencesUtils.putString(ServiceManager.appContext, UCLOUD_U3C_FLOW_PROTECTION_SP_NAME
                    , UCLOUD_U3C_FLOW_PROTECTION_SP_KEY, "");
            JLog.logi("CloudFlowProtectionLog", "saveToSP()-> mU3CFlowProtectionXML = null ");
        } else {
            try{
                Gson gson = new Gson();
                String encodeRet = EncryptUtils.encyption(ServiceManager.appContext, gson.toJson(mU3CFlowProtectionXML)
                        ,UCLOUD_U3C_FLOW_PROTECTION_SP_NAME, UCLOUD_U3C_FLOW_PROTECTION_WORD);
                SharedPreferencesUtils.putString(ServiceManager.appContext, UCLOUD_U3C_FLOW_PROTECTION_SP_NAME
                        , UCLOUD_U3C_FLOW_PROTECTION_SP_KEY, encodeRet);
                JLog.logi("CloudFlowProtectionLog", "saveToSP() -> encodeRet.len = "+(encodeRet==null ? 0 : encodeRet.length()));
            } catch (Exception e){
                JLog.logi("CloudFlowProtectionLog", "saveToSP() -> Exception: "+e.toString());
            }
        }
    }

    final public static MifiCloudFlowProtectionXML readFromSP(){
        MifiCloudFlowProtectionXML mU3CFlowProtectionXML = null;
        try{
            String spValue = SharedPreferencesUtils.getString(ServiceManager.appContext
                    , UCLOUD_U3C_FLOW_PROTECTION_SP_NAME, UCLOUD_U3C_FLOW_PROTECTION_SP_KEY, "");
            if(!TextUtils.isEmpty(spValue)){
                String decodeRet = EncryptUtils.decyption(ServiceManager.appContext,spValue
                        ,UCLOUD_U3C_FLOW_PROTECTION_SP_NAME, UCLOUD_U3C_FLOW_PROTECTION_WORD);

                if(!TextUtils.isEmpty(decodeRet)){
                    Gson gson = new Gson();
                    mU3CFlowProtectionXML = gson.fromJson(decodeRet, MifiCloudFlowProtectionXML.class);
                }
            }
        } catch (Exception e){
            JLog.logi("CloudFlowProtectionLog", "decode from xml error: "+e.toString());
        }

        return mU3CFlowProtectionXML;
    }

}

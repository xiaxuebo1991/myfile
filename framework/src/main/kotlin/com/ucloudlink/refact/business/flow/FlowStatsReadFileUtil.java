package com.ucloudlink.refact.business.flow;

import android.text.TextUtils;

import com.ucloudlink.refact.utils.JLog;

import java.io.IOException;
import java.io.RandomAccessFile;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * Created by jianguo.he on 2018/1/20.
 */

public class FlowStatsReadFileUtil {

    public static final int RM0TX = 1;
    public static final int RM0RX = 2;
    public static final int RM1TX = 3;
    public static final int RM1RX = 4;
    public static final int WLAN0TX = 5;
    public static final int WLAN0RX = 6;

    public static final String WLAN0 = "wlan0";
    public static final String RNDIS0 = "rndis0";


    private static final String RMNET0_TX_STATS_FILE = "/sys/class/net/rmnet_data0/statistics/tx_bytes";
    private static final String RMNET0_RX_STATS_FILE = "/sys/class/net/rmnet_data0/statistics/rx_bytes";
    private static final String RMNET1_TX_STATS_FILE = "/sys/class/net/rmnet_data1/statistics/tx_bytes";
    private static final String RMNET1_RX_STATS_FILE = "/sys/class/net/rmnet_data1/statistics/rx_bytes";
    private static final String WLAN0_TX_STATS_FILE = "/sys/class/net/wlan0/statistics/tx_bytes";
    private static final String WLAN0_RX_STATS_FILE = "/sys/class/net/wlan0/statistics/rx_bytes";

    /** 获取指定iface的uid的流量数据文件全路径 */
    public static String getUidStatsFilePath(String ifName, int uid, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/uids/"+uid+"/"+(isTxBytes?"tx":"rx")+"bytes";
    }

    /** 获取指定的iface的uid的数据包个数文件全路径 */
    public static String getUidStatsPackageNumFilePath(String ifName, int uid, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/uids/"+uid+"/"+(isTxBytes?"tx":"rx")+"pkts";
    }

    /** 获取指定iface名称的android系统的流量数据文件全路径 */
    public static String getAndroidSysStatsFilePath(String ifName, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/class/net/"+ifName+"/statistics/"+(isTxBytes?"tx":"rx")+"_bytes";
    }

    /** 获取指定iface名称的本地的流量数据文件全路径 */
    public static String getLocalStatsFilePath(String ifName, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/local/"+(isTxBytes?"tx":"rx")+"bytes";
    }

    /** 获取指定iface名称的本地的数据包个数数据文件全路径 */
    public static String getLocalStatsPackageNumFilePath(String ifName, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/local/"+(isTxBytes?"tx":"rx")+"pkts";
    }

    /** 获取指定iface名称的总的流量数据文件全路径 */
    public static String getTotalStatsFilePath(String ifName, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/" + ifName + "/total/"+(isTxBytes?"tx":"rx")+"bytes";
    }

    /** 获取指定iface名称的总的数据包个数数据文件全路径 */
    public static String getTotalStatsPackageNumFilePath(String ifName, boolean isTxBytes){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/" + ifName + "/total/"+(isTxBytes?"tx":"rx")+"pkts";
    }

    /** 获取指定iface名称的android系统的流量数据 */
    public static long getAndroidSysFlowStatsFromFileIn(String ifName, boolean isTxBytes){
        return getStatsFromFileIn(getAndroidSysStatsFilePath(ifName, isTxBytes));
    }

    /** 获取指定iface的本地的流量数据 */
    public static long getLocalFlowStatsFromFileIn(String ifName, boolean isTxBytes){
        return getStatsFromFileIn(getLocalStatsFilePath(ifName, isTxBytes));
    }

    /** 获取指定iface的本地的数据包个数 */
    public static long getLocalFlowStatsPackageNumFromFileIn(String ifName, boolean isTxBytes){
        return getStatsFromFileIn(getLocalStatsPackageNumFilePath(ifName, isTxBytes));
    }

    /** 获取指定iface的总的流量数据 */
    public static long getTotalFlowStatsFromFileIn(String ifName, boolean isTxBytes){
        return getStatsFromFileIn(getTotalStatsFilePath(ifName, isTxBytes));
    }

    /** 获取指定iface的总的数据包个数 */
    public static long getTotalFlowStatsPackageNumFromFileIn(String ifName, boolean isTxBytes){
        return getStatsFromFileIn(getTotalStatsPackageNumFilePath(ifName, isTxBytes));
    }

    /** 获取指定iface的本地的流量数据 */
    public static long getUidFlowStatsFromFileIn(String ifName, int uid, boolean isTxBytes){
        return getStatsFromFileIn(getUidStatsFilePath(ifName, uid, isTxBytes));
    }

    /** 获取指定iface的本地的数据包个数 */
    public static long getUidFlowStatsPackageNumFromFileIn(String ifName, int uid, boolean isTxBytes){
        return getStatsFromFileIn(getUidStatsPackageNumFilePath(ifName, uid, isTxBytes));
    }

    /**
     * 获取iface的流量数据路径
     * @param ifName 网口名称
     * @return iface流量数据的节点路径
     */
    public static String getIfNameArrayBytesFilePath(String ifName){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/fstats/iface";
    }

    /**
     * 获取iface的数组数据,
     * @param ifName
     * @return 以字符串表示数组数据，需要解析，形如：
     * [<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>]
     */
    public static String getIfNameArrayBytes(String ifName){
        return getArrayStatsFromFileIn(getIfNameArrayBytesFilePath(ifName));
    }

    /**
     * 获取iface的流量数据路径
     * @param ifName 网口名称
     * @return iface流量数据的节点路径
     */
    public static String getUidsArrayBytesFilePath(String ifName){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/fstats/iface_uids";
    }

    /**
     * 获取iface的数组数据, 前置条件 setReadBytesUids
     * @param ifName
     * @return 以字符串表示数组数据，需要解析，形如：
     *          [<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>][<uid1>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
    [<uid2>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]...[<uidn>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
     */
    public static String getUidsArrayBytes(String ifName){
        return getArrayStatsFromFileIn(getUidsArrayBytesFilePath(ifName));
    }

    /**
     * 获取iface的流量数据路径
     * @param ifName 网口名称
     * @return iface流量数据的节点路径
     */
    public static String getAllUidArrayBytesFilePath(String ifName){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/fstats/iface_all";
    }

    /**
     * 获取iface的数组数据,
     * @param ifName
     * @return 以字符串表示数组数据，需要解析, 形如：
     *          [<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>][<uid1>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
    [<uid2>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]...[<uidn>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
     */
    public static String getAllUidArrayBytes(String ifName){
        return getArrayStatsFromFileIn(getAllUidArrayBytesFilePath(ifName));
    }

    /**
     * 获取iface的流量数据路径
     * @param ifName 网口名称
     * @return iface流量数据的节点路径
     */
    public static String getReadBytesUidsFilePath(String ifName){
        return TextUtils.isEmpty(ifName) ? ifName : "/sys/kernel/uck/flowstat/"+ifName+"/fstats/iface_uids";
    }

    /**
     * 设置需要读取的uids
     * @param ifName  网口名称
     * @param strUids uid的字符串组合， 形如： <uid1>,<uid2>,...,<uidn>
     */
    public static void setReadBytesUids(String ifName, String strUids){
        writeStatsFromFileIn(getReadBytesUidsFilePath(ifName), strUids);
    }

    private static long getStatsFromFileIn(String fileName){
        long mNum = 0L;
        String szLine = null;
        if(!TextUtils.isEmpty(fileName)){
            RandomAccessFile raf = null;
            try{
                raf = new RandomAccessFile(fileName,"r");
                if (raf != null) {
                    szLine = raf.readLine();
                    mNum = Long.valueOf(szLine);
                } else {
                    JLog.logd("Failed to read from stats file." );
                }
            } catch(IOException e){
                JLog.logd("getStatsFromFileIn, IOException: "+e.toString());
                mNum = 0L;
                loge("read stats file error.");
            }catch(NumberFormatException e) {
                JLog.logd("getStatsFromFileIn, NumberFormatException: " + e.toString());
                mNum = 0L;
                loge("read:" + (szLine == null ? "null" : szLine));
            }catch (Exception e){
                JLog.logd("getStatsFromFileIn, Exception: " + e.toString());
                mNum = 0L;
                loge("read:" + (szLine == null ? "null" : szLine));
            }finally {
                try{
                    if (raf != null){
                        raf.close();
                    }
                }catch (IOException e){
                    JLog.logd("getStatsFromFileIn, close IO, IOException: "+e.toString());
                }
            }
        }
        logd("getStatsFromFileIn[Str:" + (szLine==null? "null" : szLine) + "],value:" + mNum+", fileName = "+fileName);
        return mNum;
    }


    private static String getArrayStatsFromFileIn(String fileName){
        String szLine = null;
        if(!TextUtils.isEmpty(fileName)){
            RandomAccessFile raf = null;
            try{
                raf = new RandomAccessFile(fileName,"r");
                if (raf != null) {
                    szLine = raf.readLine();
                } else {
                    JLog.logd("getArrayStatsFromFileIn -> Failed to read from stats file." );
                }
            } catch(IOException e){
                JLog.logd("getArrayStatsFromFileIn -> IOException: "+e.toString());
                szLine = "";
                loge("getArrayStatsFromFileIn -> read stats file error.");
            }catch(NumberFormatException e) {
                JLog.logd("getArrayStatsFromFileIn -> NumberFormatException: " + e.toString());
                szLine = "";
                loge("getArrayStatsFromFileIn -> read:" + (szLine == null ? "null" : szLine));
            }catch (Exception e){
                JLog.logd("getArrayStatsFromFileIn -> Exception: " + e.toString());
                szLine = "";
                loge("getArrayStatsFromFileIn -> read:" + (szLine == null ? "null" : szLine));
            }finally {
                try{
                    if (raf != null){
                        raf.close();
                    }
                }catch (IOException e){
                    JLog.logd("getArrayStatsFromFileIn -> close IO, IOException: "+e.toString());
                }
            }
        }
        logd("getArrayStatsFromFileIn -> [Str:" + (szLine==null? "null" : szLine) + "], fileName = "+fileName);
        return szLine;
    }

    private static void writeStatsFromFileIn(String fileName, String strUids){
        logd("writeStatsFromFileIn -> [fileName:" + (fileName==null? "null" : fileName) + "],strUids:" + (strUids==null?"null":strUids));

        if(!TextUtils.isEmpty(fileName) && !TextUtils.isEmpty(strUids)){
            RandomAccessFile raf = null;
            try{
                raf = new RandomAccessFile(fileName,"rw");
                if (raf != null) {
                    raf.write(strUids.getBytes());
                } else {
                    JLog.logd("writeStatsFromFileIn -> Failed to write to stats file." );
                }
            } catch(IOException e){
                JLog.logd("writeStatsFromFileIn -> IOException: "+e.toString());
                loge("writeStatsFromFileIn -> read stats file error.");
            }catch(NumberFormatException e) {
                JLog.logd("writeStatsFromFileIn -> NumberFormatException: " + e.toString());
            }catch (Exception e){
                JLog.logd("writeStatsFromFileIn -> Exception: " + e.toString());
            }finally {
                try{
                    if (raf != null){
                        raf.close();
                    }
                }catch (IOException e){
                    JLog.logd("writeStatsFromFileIn -> close IO, IOException: "+e.toString());
                }
            }
        }

    }


    public static long getStatsFromFileIn(int rmf) {
        String szLine = null;
        long mNum = 0L;
        RandomAccessFile raf = null;
        String path = null;

        try {
            switch (rmf) {
                case RM0TX:
                    path = RMNET0_TX_STATS_FILE;
                    raf = new RandomAccessFile(RMNET0_TX_STATS_FILE, "r");
                    break;
                case RM0RX:
                    path = RMNET0_RX_STATS_FILE;
                    raf = new RandomAccessFile(RMNET0_RX_STATS_FILE, "r");
                    break;
                case RM1TX:
                    path = RMNET1_TX_STATS_FILE;
                    raf = new RandomAccessFile(RMNET1_TX_STATS_FILE, "r");
                    break;
                case RM1RX:
                    path = RMNET1_RX_STATS_FILE;
                    raf = new RandomAccessFile(RMNET1_RX_STATS_FILE, "r");
                    break;
                case WLAN0TX:
                    path = WLAN0_TX_STATS_FILE;
                    raf = new RandomAccessFile(WLAN0_TX_STATS_FILE, "r");
                    break;
                case WLAN0RX:
                    path = WLAN0_RX_STATS_FILE;
                    raf = new RandomAccessFile(WLAN0_RX_STATS_FILE, "r");
                    break;
                default:
                    loge("invalid read File handle.");
                    return 0L;
            }
            if (raf != null) {
                szLine = raf.readLine();
                mNum = Long.valueOf(szLine);
            } else {
                logd("Failed to read from stats file." );
            }
        } catch(IOException e){
            e.printStackTrace();
            mNum = 0L;
            loge("read stats file error.");
        }catch(NumberFormatException e){
            e.printStackTrace();
            mNum = 0L;
            loge("read:" + szLine);
        }finally {
            try{
                if (raf != null){
                    raf.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        logd("getStatsFromFile[Str:" + szLine + "],value:" + mNum+", rmf = "+rmf+", path = "+(path==null?"null":path));
        return mNum;
    }


}

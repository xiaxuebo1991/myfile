package com.ucloudlink.refact.business.log;

import com.ucloudlink.refact.utils.JLog;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * 建议使用log压缩包命名方式为:用户名+IMEI+日期.zip
 * 示例:test_APP_100@163.com_356166060460523_201608301509.zip
 * 使用
 * FTPUtils.getInstance().initFTPSetting("223.197.68.225", 21, "gdcl", "Gdcl@Ucloud=2014");
 * FTPUtils.getInstance().uploadFile("/storage/emulated/0/uitestlogs/test_APP_100@163.com_356166060460523_201608301509.zip",
 * "test_APP_100@163.com_356166060460523_201608301509.zip");
 */
public class FTPUtils {
    private FTPClient ftpClient = null;
    private static FTPUtils ftpUtilsInstance = null;
    private String FTPUrl;
    private int FTPPort;
    private String UserName;
    private String UserPassword;

    //服务器存放log目录
    private final static String REMOTE_SAVE_PATH = "/dsdslog";

    private FTPUtils() {
        ftpClient = new FTPClient();
    }

    /*
     * 用单例模式
     */
    public static FTPUtils getInstance() {
        if (ftpUtilsInstance == null) {
            ftpUtilsInstance = new FTPUtils();
        }
        return ftpUtilsInstance;
    }

    /**
     * 设置FTP服务器
     *
     * @param FTPUrl       FTP服务器ip地址
     * @param FTPPort      FTP服务器端口号
     * @param UserName     登陆FTP服务器的账号
     * @param UserPassword 登陆FTP服务器的密码
     * @return true为成功，false为失败
     */
    public boolean initFTPSetting(String FTPUrl, int FTPPort, String UserName, String UserPassword) {
        this.FTPUrl = FTPUrl;
        this.FTPPort = FTPPort;
        this.UserName = UserName;
        this.UserPassword = UserPassword;

        int reply;

        try {
            //1.要连接的FTP服务器Url,Port
            ftpClient.connect(FTPUrl, FTPPort);

            //2.登陆FTP服务器
            ftpClient.login(UserName, UserPassword);

            //3.看返回的值是不是230，如果是，表示登陆成功
            reply = ftpClient.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                //断开
                ftpClient.disconnect();
                return false;
            }

            return true;

        } catch (SocketException e) {
//            e.printStackTrace();
            return false;
        } catch (IOException e) {
//            e.printStackTrace();
            return false;
        }
    }

    /**
     * 上传文件
     *
     * @param FilePath 要上传文件所在SDCard的路径
     * @param FileName 要上传的文件的文件名(如：Sim唯一标识码)
     * @return true为成功，false为失败
     * 示例: FTPUtils.getInstance().uploadFile("/storage/emulated/0/uitestlogs/test_APP_100@163.com_356166060460523_201608301509.zip",
     * "test_APP_100@163.com_356166060460523_201608301509.zip");
     */
    public boolean uploadFile(String FilePath, String FileName) {

        if (!ftpClient.isConnected()) {
            if (!initFTPSetting(FTPUrl, FTPPort, UserName, UserPassword)) {
                return false;
            }
        }

        try {

            //设置存储路径
            ftpClient.makeDirectory(REMOTE_SAVE_PATH);
            ftpClient.changeWorkingDirectory(REMOTE_SAVE_PATH);

            //设置上传文件需要的一些基本信息
//            ftpClient.setBufferSize(1024);
//            ftpClient.setControlEncoding("UTF-8");
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            //文件上传
            FileInputStream fileInputStream = new FileInputStream(FilePath);
            ftpClient.storeFile(FileName, fileInputStream);

            //关闭文件流
            fileInputStream.close();

            //退出登陆FTP，关闭ftpCLient的连接
            ftpClient.logout();
            ftpClient.disconnect();

        } catch (IOException e) {
//            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean uploadFile(String FilePath, String FileName, String RemotePath) {
        JLog.logd("uploadFile: start-" + System.currentTimeMillis());
        if (!ftpClient.isConnected()) {
            if (!initFTPSetting(FTPUrl, FTPPort, UserName, UserPassword)) {
                JLog.logd("uploadFile: return false");
                return false;
            }
        }
        FileInputStream fileInputStream = null;
        try {
            //设置存储路径
            ftpClient.makeDirectory(RemotePath);
            ftpClient.changeWorkingDirectory(RemotePath);
            //设置上传文件需要的一些基本信息
//            ftpClient.setBufferSize(1024);
//            ftpClient.setControlEncoding("UTF-8");
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            //文件上传
            fileInputStream = new FileInputStream(FilePath);
            ftpClient.storeFile(FileName, fileInputStream);
            JLog.logd("uploadFile: end-" + System.currentTimeMillis());
        } catch (IOException e) {
            JLog.loge("uploadFile: error-" + System.currentTimeMillis() + "  msg = " + e.getMessage());
            e.printStackTrace();
            return false;
        }finally {
            try {
                //关闭文件流
                if(null != fileInputStream){
                    fileInputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                JLog.loge("uploadFile: error2-" + System.currentTimeMillis() + "  msg = " + e.getMessage());
            }
            try {
                if(null != ftpClient){
                    //退出登陆FTP，关闭ftpCLient的连接
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
                JLog.loge("uploadFile: error2-" + System.currentTimeMillis() + "  msg = " + e.getMessage());
            }
        }
        return true;
    }


    /**
     * 下载文件
     *
     * @param FilePath 要存放的文件的路径
     * @param FileName 远程FTP服务器上的那个文件的名字
     * @return true为成功，false为失败
     */
    public boolean downLoadFile(String FilePath, String FileName) {

        if (!ftpClient.isConnected()) {
            if (!initFTPSetting(FTPUrl, FTPPort, UserName, UserPassword)) {
                return false;
            }
        }

        try {
            // 转到指定下载目录
            ftpClient.changeWorkingDirectory(REMOTE_SAVE_PATH);

            // 列出该目录下所有文件
            FTPFile[] files = ftpClient.listFiles();

            // 遍历所有文件，找到指定的文件
            for (FTPFile file : files) {
                if (file.getName().equals(FileName)) {
                    //根据绝对路径初始化文件
                    File localFile = new File(FilePath);

                    // 输出流
                    OutputStream outputStream = new FileOutputStream(localFile);

                    // 下载文件
                    ftpClient.retrieveFile(file.getName(), outputStream);

                    //关闭流
                    outputStream.close();
                }
            }

            //退出登陆FTP，关闭ftpCLient的连接
            ftpClient.logout();
            ftpClient.disconnect();


        } catch (IOException e) {
//            e.printStackTrace();
        }

        return true;
    }

}

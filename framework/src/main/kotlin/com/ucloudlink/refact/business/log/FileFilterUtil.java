package com.ucloudlink.refact.business.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by junsheng.zhang on 2018/6/21.
 */

public class FileFilterUtil {

    public static List<File> filter(File file, long startTime, long endTime) {
        List<File> files = new ArrayList<>();
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] listFiles = file.listFiles();
                if (listFiles != null) {
                    for (File f : listFiles) {
                        files.addAll(filter(f, startTime, endTime));
                    }
                }
            } else {
                FileInfo fileInfo = getFileInfo(file);
                if ((startTime < fileInfo.startTime && fileInfo.startTime < endTime)
                        || (startTime < fileInfo.endTime && fileInfo.endTime < endTime)
                        || (fileInfo.startTime < startTime && endTime < fileInfo.endTime)) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private static FileInfo getFileInfo(File file) {
        FileInfo fileInfo = new FileInfo();
        try {
            Process process = Runtime.getRuntime().exec("stat " + file.getAbsolutePath());
            InputStream is = process.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String str;
            while ((str = br.readLine()) != null) {
                if (str.startsWith("Access: 20")) {  //Access: 2018-06-21 10:14:30.209999988
                    String timeStr = str.substring(8, str.lastIndexOf("."));
                    Date date = TimeConver.converString2Time(timeStr, "yyyy-MM-dd HH:mm:ss");
                    if (date != null) {
                        fileInfo.startTime = date.getTime();
                    }
                } else if (str.startsWith("Modify: 20")) {  //Access: 2018-06-21 10:14:30.209999988
                    String timeStr = str.substring(8, str.lastIndexOf("."));
                    Date date = TimeConver.converString2Time(timeStr, "yyyy-MM-dd HH:mm:ss");
                    if (date != null) {
                        fileInfo.endTime = date.getTime();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileInfo;
    }

    private static class FileInfo {
        long startTime = 0;
        long endTime = Long.MAX_VALUE;
    }
}

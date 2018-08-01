package com.ucloudlink.refact.business.log;

import android.annotation.SuppressLint;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by chunjiao.li on 2016/9/9.
 */
public class TimeConver {
    public final static String Dataformat = "yyyy-MM-dd HH:mm:ss";
    public final static String DataformatSec = "yyyyMMddHHmmss";
    public final static String DataformatThird = "yyyyMMddHHmmssSSS";
    public final static String Dataformat2 = "yyyyMMdd_HHmmss";
    public final static int DataformatLength = 19;

    /**
     * @param time GMT时间，且时间字符串格式 "yyyy-MM-dd HH:mm:ss"
     * @return
     */
    public static Date converGMTStr2LocalDate(String time) {
        Date LocalDate;
        String LocalTime = TimeConver.converTime2LocalZone(time);
        if (LocalTime != null) {
            LocalDate = TimeConver.converString2Time(LocalTime, Dataformat);
            return LocalDate;
        }

        return null;
    }

    /* 将Server传送的UTC(GMT)时间转换为当地时区的时间 */
    //使用时注意非空判断
    @SuppressLint("SimpleDateFormat")
    public static String converTime2LocalZone(String srcTime) {
        SimpleDateFormat sdf = new SimpleDateFormat(Dataformat);
        SimpleDateFormat dspFmt = new SimpleDateFormat(Dataformat);
        String convertTime;
        Date result_date;
        long result_time;
        // 如果传入参数异常
        if (null == srcTime)
            return null;
        else {
            try { // 将输入时间字串转换为UTC时间
                sdf.setTimeZone(TimeZone.getTimeZone("GMT00:00"));
                result_date = sdf.parse(srcTime);
                result_time = result_date.getTime();
                // 设定时区
                TimeZone tz = TimeZone.getDefault();
                dspFmt.setTimeZone(tz);
                convertTime = dspFmt.format(result_time);
                return convertTime;
            } catch (Exception e) {
//                e.printStackTrace();
                return null;
            }
        }
    }

    //使用时注意非空判断
    public static Date converString2Time(String dateStr, String dateFormat) {
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        try {
            Date date = sdf.parse(dateStr);
            return date;
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * 获取日期
     *
     * @return 日期字符串
     */
    public static String getDateNow() {
        SimpleDateFormat format = new SimpleDateFormat(DataformatSec);
        String date = format.format(new Date(System.currentTimeMillis()));
        return date;// format:19700104234131
    }

    /**
     * 获取日期
     *
     * @return  日期字符串 格式"yyyyMMddHHmmssSSS"
     */
    public static String getDateTimeNow() {
        SimpleDateFormat format = new SimpleDateFormat(DataformatThird);
        String date = format.format(new Date(System.currentTimeMillis()));
        return date;//format:19700104234131666
    }

    /**
     * 获取日期的字符串
     *
     * @return 日期
     */
    public static String getStrFromDate(Date time, String dateFormat) {
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        String date = format.format(time);
        return date;
    }
}

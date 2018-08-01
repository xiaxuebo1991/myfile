package com.ucloudlink.uservice;

import android.content.Context;

import java.lang.reflect.Method;

/**
 * Created by jianguo.he on 2017/7/20.
 */

public class UServiceAppLibs {

    public static void init(Context context){
        try{
            // 使用反射调用是因为如果不使用加固，则会找不到该类，详看：gradle.properties
            Class cls = Class.forName("com.ucloudlink.safety4fw.AppLibsMap");
            Method method = cls.getDeclaredMethod("environmentInit", Context.class);
            Object methodObject = cls.newInstance();
            method.invoke(methodObject,context);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}

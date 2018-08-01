package com.ucloudlink.refact.systemapi.model

import android.content.Context
import com.ucloudlink.refact.systemapi.struct.ModelInfo

/**
 * Created by shiqianhua on 2018/1/8.
 * 
 * GP1701 版本号是某一次出版本时弄错了，以后统一都叫G1701 所以，这个model 全部继承G1701 就行
 */
class SystemApi_GP1701(context: Context, modelInfo: ModelInfo, sdkInt:Int): SystemApi_G1701(context,modelInfo,sdkInt) {
    
}
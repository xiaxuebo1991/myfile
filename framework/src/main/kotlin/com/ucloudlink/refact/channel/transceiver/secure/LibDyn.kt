package com.ucloudlink.framework.remoteuim

import android.content.Context

/**
 * Created by chentao on 2016/6/23.
 */
object LibDyn{
    external fun getClientInfo(context: Context, out_client_info:ByteArray): Int
    external fun runCmd(cmd:ByteArray,cmd_output:ByteArray): Int
    external fun getHashK(IK:ByteArray): Int
    external fun getDummy(random:Int, tmpAK:ByteArray): Int
    external fun getAppDataKey(context: Context, token:String, tmpAK:ByteArray): Int
}
package com.ucloudlink.framework.remoteuim

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.ContextThemeWrapper

/**
 * Created by chentao on 2016/6/23.
 */
public object LibUcsslSignNative : ContextThemeWrapper() {


    external fun createConnectRequest(device_id: String, shake_msg: ByteArray,
                                      server_crt_path: String, client_key_path: String,
                                      clikey_pwd: String, out_buf: ByteArray,
                                      out_ks: ByteArray): Int
    external fun encryptMsg(plain_msg: ByteArray, ks: ByteArray, out_msg: ByteArray): Int
    external fun decryptMsg(encrypt_msg: ByteArray, ks: ByteArray, out_msg: ByteArray): Int

    external fun updateLib(context: Context, token: String, commonLib: ByteArray, patchData: ByteArray): Int
    external fun getDummy(random: Int, tmpAK: ByteArray): Int
    external fun createKS(device_id: String, newks: ByteArray): Int


}
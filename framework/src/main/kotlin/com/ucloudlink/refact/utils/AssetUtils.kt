package com.ucloudlink.refact.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Assert Utils
 * Created by zhe.li on 2018/3/27.
 */
object AssetUtils {
    /**
     * Copy asset file to sd card path
     */
    fun copyAssetFileToSDCard(context: Context, assetFile: String, sdCardPath: String) {
        try {
            JLog.logd("Prepare copy file $assetFile to $sdCardPath")
            val mIs = context.assets.open(assetFile)
            val mByte = ByteArray(1024)
            var bt: Int
            val file = File(sdCardPath + File.separator + assetFile)
            if (!file.exists()) {
                file.createNewFile()
                JLog.logd("Create new file ${file.path}")
            } else {
                JLog.logd("File exist, return")
                return
            }
            val fos = FileOutputStream(file)
            bt = mIs.read(mByte)
            while (bt != -1) {
                fos.write(mByte, 0, bt)
                bt = mIs.read(mByte)
            }
            fos.flush()
            mIs.close()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Copy asset file to APP's file path
     * From /assets/FILENAME to /data/.../APP location/files/FILENAME
     *
     * @param context APP Context
     * @param assetFile file under asset
     */
    fun copyAssetFileToAppPath(context: Context, assetFile: String) {
        var mIs: InputStream? = null
        var fos: FileOutputStream? = null
        try {
            JLog.logd("Prepare copy file $assetFile")
            mIs = context.assets.open(assetFile)
            val size = mIs.available()
            val buffer = ByteArray(size)
            mIs.read(buffer)
            fos = context.openFileOutput(assetFile, Context.MODE_PRIVATE)
            fos.write(buffer)
        } catch (e: Exception) {
            throw e
        } finally {
            try {
                mIs?.close()
            } catch (e: Exception) {
            }
            try {
                fos?.close()
            } catch (e: Exception) {
            }
        }
    }
}
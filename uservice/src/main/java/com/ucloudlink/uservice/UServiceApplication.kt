package com.ucloudlink.uservice

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.ucloudlink.refact.Framework
import com.ucloudlink.refact.business.log.logcat.LogcatHelper
import com.ucloudlink.refact.utils.JLog.*
import okio.Okio
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.properties.Delegates


/**
 * Created by chentao on 2016/8/30.
 */
class UServiceApplication : Application() {

    var appDataDir: String by Delegates.notNull()
    var JG_extLibs: Array<String> = arrayOf(
            "libc++.so"
    )

    val extLibDir: String
        get() {
            return "${appDataDir}/extlib/"
        }

    var JG_ucAssets: Array<String> = arrayOf(
            "uc-asset0.bin"
    )

    val ucAssetsDir: String
        get() {
            return "${appDataDir}/files/code"
        }
    override fun onCreate() {
        super.onCreate()

        println("MainActivity base >>" + BuildConfig.REINFORCE_ENABLE)
        println("Uservice start ---->:" + SystemClock.uptimeMillis() + ", sleep:" + SystemClock.elapsedRealtime())

        appDataDir = this.applicationContext.applicationInfo.dataDir

        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.M) {
            //copyFilesNew(this.applicationContext, JG_extLibs, extLibDir)
            loadLibraries(JG_extLibs, applicationContext.applicationInfo.nativeLibraryDir)
        }else{
            System.load("/system/lib64/libc++.so")
        }
        if(BuildConfig.REINFORCE_ENABLE){
            val extLibDir = File(this.filesDir.toString()+"/code")
            if (!extLibDir.exists()) {
                extLibDir.mkdirs()
            }
            overrideUcAssetFile(ZipFile(applicationContext.packageResourcePath),"assets/uc-asset0.bin",this.filesDir.toString()+"/code/uc-asset0.bin")
            //copyFiles(this.applicationContext, JG_ucAssets, ucAssetsDir)

            UServiceAppLibs.init(this.applicationContext)
        }

        LogcatHelper.getInstance(this).start()
        logi("UService Started.")
        var versionName = "unknow"
        try {
            val manager = packageManager
            val packageInfo = manager.getPackageInfo(this.packageName, 0)
            versionName = packageInfo.versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }

        logi("UService onCreate current version: $versionName")
        val intent = Intent(this, Class.forName("com.ucloudlink.refact.access.ui.AccessEntryService"))
        logd("Start AccessEntryService at ${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val startForegroundService = ContextWrapper::class.java.getMethod("startForegroundService", Intent::class.java)
                startForegroundService.invoke(applicationContext, intent)
                logd("Start AccessEntryService startForegroundService Success!")
            } catch (e: Exception) {
                e.printStackTrace()
                loge("Start AccessEntryService failed: $e")
            }
        } else {
            startService(intent)
            logd("Start AccessEntryService startService Success!")
        }

        // 安全so库更新
        if(File(this.filesDir.toString()+"/code/uc-asset-jie0.bin").exists())
            File(this.filesDir.toString()+"/code/uc-asset-jie0.bin").delete()
        if(File(this.filesDir.toString()+"/optdir/lib0.so").exists())
            File(this.filesDir.toString()+"/optdir/lib0.so").delete()
        getRawSignature(this.applicationContext,this.applicationContext.packageName)
    }
//
//    private fun copyFilesNew(context: Context, fileList: Array<String>, dir: String){
//        for (s in fileList) {
//            val extLibDir = File(dir)
//            if (!extLibDir.exists()) {
//                extLibDir.mkdirs()
//            }
//            overrideUcAssetFile(ZipFile(context.packageResourcePath),"assets/"+s,dir+s)
//        }
//    }


    private fun overrideUcAssetFile(zipFile:ZipFile,fileInZip:String,fileDest:String){
        var zipEntry: ZipEntry ?= null

        var apkFile: ByteArray? = null
        var installFile: ByteArray? = null
        var digZip: ByteArray? = null
        var digExist: ByteArray? = null
        var fExist: File? = null
        var fis: FileInputStream? = null
        var sizeOfDest = 0

        try {
            zipEntry = zipFile.getEntry(fileInZip)
            apkFile = ByteArray(zipEntry.size!!.toInt())
            zipFile.getInputStream(zipEntry).read(apkFile)
            digZip = getMd5(apkFile)
            //logd("zipfile ${zipEntry.size!!.toInt()},${apkFile.size}")
        } catch (e: Exception) {
            e.printStackTrace()

        }
        fExist = File(fileDest)
        if(fExist.exists()) {
            try {
                installFile = fExist.readBytes()
                digExist = getMd5(installFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (memcmp(digZip, digExist, 20)) {
                println("MainActivity skip."+fileDest)
                return
            }
            fExist!!.delete()
            println("MainActivity delete."+fileDest)
        }

        val sink = Okio.sink(fExist)
        val writer = Okio.buffer(sink)
        writer.writeAll(Okio.source(zipFile.getInputStream(zipEntry)))
        writer.flush()

        println("MainActivity write done."+fileDest)
    }



    @Throws(Exception::class)
    fun getMd5(data: ByteArray): ByteArray {
        val md5 = MessageDigest.getInstance("SHA1")
        md5.update(data)
        return md5.digest()
    }

    /**
     * 比较两个byte数组数据是否相同,相同返回 true

     * @param data1
     * *
     * @param data2
     * *
     * @param len
     * *
     * @return
     */
    fun memcmp(data1: ByteArray?, data2: ByteArray?, len: Int): Boolean {
        if (data1 == null && data2 == null) {
            return true
        }
        if (data1 == null || data2 == null) {
            return false
        }
        if (data1 == data2) {
            return true
        }
        var bEquals = true
        var i: Int
        i = 0
        while (i < data1.size && i < data2.size && i < len) {
            //logi("UService memcmp test.${data1[i]} and ${data2[i]},$i")
            if (data1[i] != data2[i]) {
                bEquals = false
                break
            }
            i++
        }

        return bEquals
    }
    private fun loadLibraries(libs: Array<String>, dir: String) {
        for (lib in libs) {
            val libName = "$dir/$lib"
            //logd("loading library:$libName")
            try {
                System.load(libName)
            }catch (e :UnsatisfiedLinkError){
                e.printStackTrace()
            }
        }
    }
    override fun onTerminate() {
        logi("UService onTerminate.")
        Framework.reset()
        super.onTerminate()
    }

    private fun getRawSignature(paramContext: Context, paramString: String?): String? {
        if (paramString == null || paramString.isEmpty()) {
            loge("获取签名失败，包名为 null")
            return null
        }
        val localPackageManager = paramContext.packageManager
        val localPackageInfo: PackageInfo?
        try {
            localPackageInfo = localPackageManager.getPackageInfo(paramString, PackageManager.GET_SIGNATURES)
            if (localPackageInfo == null) {
                loge("信息为 null, 包名 = $paramString")
                return null
            }
        } catch (localNameNotFoundException: PackageManager.NameNotFoundException) {
            loge("包名没有找到...")
            return null
        }

        val sign = localPackageInfo.signatures[0].publicKey.toString()
        logd("sign::$sign")
        return sign
    }
}

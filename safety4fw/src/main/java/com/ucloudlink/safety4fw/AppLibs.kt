package com.ucloudlink.cloudsim.app

import android.content.Context
import okio.Okio
import java.io.File
import kotlin.properties.Delegates

/**
 * Created by jianguo.he on 2017/7/12.
 */
object AppLibs {
    var appDataDir: String by Delegates.notNull()

    val extLibDir: String
        get() {
            return "${appDataDir}/extlib/"
        }

    val JG_extLibs: Array<String> = arrayOf(
            "libdexload.so"
    )
    
    fun environmentInit(context: Context) {
        // 这里只加载用于安全加固的so库, copy到那个目录就加载那个目录
        appDataDir = context.applicationInfo.dataDir
        //copyFilesNew(context, JG_extLibs, extLibDir)
        loadLibraries(JG_extLibs, context.applicationInfo.nativeLibraryDir)
        // 这里的LibUc是ucapp中的类，只因so库需要指定的包名，所以跟framework中的包名一样了
        // 重要： 在未调用此方法解密之前，不能调用任何加密jar中的类
        com.ucloudlink.framework.remoteuim.LibDexload.loadUcClasses(context)
    }
/*
    private fun copyFilesNew(context: Context, fileList: Array<String>, dir: String){
        for (s in fileList) {
            val extLibDir = File(dir)
            if (!extLibDir.exists()) {
                extLibDir.mkdirs()
            }
            overrideUcAssetFile(ZipFile(context.packageResourcePath),"assets/"+s,dir+s)
        }

        for (s in fileList) {
            val destFile = File(extLibDir, s)

            if (destFile.exists()) {
                continue
            }

            val istream = context.assets.open(s)
            val source = Okio.source(istream)

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
*/
    private fun loadLibraries(libs: Array<String>, dir: String) {
        for (lib in libs) {
            val libName = "$dir/$lib"
            System.load(libName)
        }
    }

}
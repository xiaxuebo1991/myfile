package com.ucloudlink.refact.utils

import android.content.Context
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.refact.channel.transceiver.secure.SecureUtil
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.systemapi.SystemApiIf
import com.ucloudlink.refact.systemapi.struct.FileWapper
import com.ucloudlink.refact.utils.JLog.logd
import okio.Okio
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Created by shiqianhua on 2018/1/15.
 */
object LoadFiles {

    val simCommonData: Array<FileWapper> = arrayOf(
            FileWapper("server.crt","server.crt","server.crt")
            //FileWapper("libdyn-common.so","libdyn-common.so","libdyn-common.so")
    )
    val simCommonDataDb: Array<FileWapper> = arrayOf(
            FileWapper("uC_Prefab.db3","uC_Prefab.db3","uC_Prefab.db3")
    )

    val qxdmData: Array<FileWapper> = arrayOf(
            FileWapper("system_qxdm_config_all.cfg","system_qxdm_config_all.cfg","system_qxdm_config_all.cfg"),
            FileWapper("system_qxdm_config_simple.cfg", "system_qxdm_config_simple.cfg", "system_qxdm_config_simple.cfg")
    )

    val systemCFG: Array<FileWapper> = arrayOf(
            FileWapper("system_config.xml", "system_config.xml", "system_config.xml")
    )

    private val SO_VERSION_KEY = "SO_VERSION"
    /*更新so需要更新这个值*/
    private val SO_VERSION = 5

    /**
     * copy to lib dir
     */
    public fun copyFiles(context: Context, fileList: Array<FileWapper>, dir: String) {
        val extLibDir = File(dir)

        if (!extLibDir.exists()) {
            extLibDir.mkdirs()
        }


        val soVersion = SharedPreferencesUtils.getInt(context, SO_VERSION_KEY, 0)
        val isUpdate = SO_VERSION > soVersion

        for (s in fileList) {
            val destFile = File(extLibDir, s.dstPath)

            if (!isUpdate && destFile.exists()) {
                continue
            }

            val istream = context.assets.open(s.srcPath)
            val source = Okio.source(istream)

            val sink = Okio.sink(destFile)
            val writer = Okio.buffer(sink)

            writer.writeAll(source)
            writer.flush()
        }

        SharedPreferencesUtils.putInt(context, SO_VERSION_KEY, SO_VERSION)
    }

    private fun copyFilesNew(context: Context, fileList: Array<FileWapper>, dir: String){
        if(fileList.size == 0){
            logd("fileList none files!")
            return
        }

        for (s in fileList) {
            val extLibDir = File(dir)
            if (!extLibDir.exists()) {
                extLibDir.mkdirs()
            }
            logd("start to copy file ${"assets/"+s.srcPath}  -> ${dir+s.dstPath}")
            overrideUcAssetFile(ZipFile(context.packageResourcePath),"assets/"+s.srcPath,dir+s.dstPath)
        }
    }

    private fun overrideUcAssetFile(zipFile: ZipFile, fileInZip:String, fileDest:String){
        var zipEntry: ZipEntry?= null

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
            digZip = EncryptUtils.getSHA1(apkFile)
            JLog.logd("zipfile ${zipEntry.size!!.toInt()},${apkFile.size}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        fExist = File(fileDest)
        if(fExist.exists()) {
            try {
                installFile = fExist.readBytes()
                digExist = EncryptUtils.getSHA1(installFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (MemUtil.memcmp(digZip, digExist, 20)) {
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
//    fun getMd5(data: ByteArray): ByteArray {
//        val md5 = MessageDigest.getInstance("SHA1")
//        md5.update(data)
//        return md5.digest()
//    }


    private fun loadLibraries(libs: Array<FileWapper>, dir: String) {
        for (lib in libs) {
            val libName = "$dir/${lib.name}"
            JLog.logd("loading library222:$libName")
            try {
                System.load(libName)
            }catch (e :UnsatisfiedLinkError){
                e.printStackTrace()
            }
        }
    }

    private fun loadLibraries(libs: Array<String>) {
        for (lib in libs) {
            val libName = "$lib"
            JLog.logd("loading library:$libName")
            System.loadLibrary(libName)
        }
    }

    private fun checkAndDeleOld(appDir: String) {
        //需要清理的文件有，simdata/CardInfo.db  simdata/CardTable.db 以及databases文件夹,还有extLibDir文件夹

        //val ret = File(Configuration.extLibDir).deleteRecursively()
        //JLog.logv("checkAndDeleOld :${Configuration.extLibDir} ret:$ret")

        val simdataDir = File(Configuration.simDataDir)
        if (!simdataDir.exists()) {
            return
        }
        if (simdataDir.isDirectory) {
            val uC_Prefab = File(simdataDir, "uC_Prefab.db3")
            if (!uC_Prefab.exists()) {
                //不存在，旧版so升新版so，清理旧文件
                val deleFiles= arrayOf("${simdataDir}${File.separator}CardInfo.db"
                        ,"${simdataDir}${File.separator}CardTable.db"
                        ,"${appDir}${File.separator}databases"
                        , Configuration.extLibDir
                )
                deleFiles.forEach {
                    val ret = File(it).deleteRecursively()
                    JLog.logv("checkAndDeleOld dele:$it ret:$ret")
                }
            }
        }
    }

    val key1= byteArrayOf(0x16,0x37,0x2A,0x13,0x6C,0x7F,0x05,0x3B,0x46,0x7E,0x2B,0x4F,0x6A,0x3D,0x5C,0x05)
    val key2= byteArrayOf(0x26,0x7A,0x45,0x3C,0x6C,0x2E,0x5D,0x6A,0x55,0x46,0x23,0x44,0x1A,0x34,0x6A,0x1F)

    private fun ChallengeSimSo() {
        val rand_w = ByteArray(16)
        val len = IntArray(1)
        val ret = SoftSimNative.getChallenge(rand_w, len)
        if (ret== SoftSimNative.E_SOFTSIM_SUCCESS) {
//            logv("getChallenge:rand_w:${rand_w.toHex()},len:${len.get(0)}")
//            logv("getChallenge:key1:${key1.toHex()}")
//            logv("getChallenge:key2:${key2.toHex()}")
            val rand2 = rand_w.copyOfRange(0, len[0])
//            val rand_temp = EncryptionUtil3.toByte("0F8EFEC180AC87E45E8A33F961A45A13")
            val ChallengeRes:ByteArray= ByteArray(key1.size)

            for (index in 0..ChallengeRes.size-1){
                ChallengeRes[index] = ((rand2[index].toInt()) xor (key1[index].toInt())).toByte()
                ChallengeRes[index] = ((ChallengeRes[index].toInt()) xor (key2[index].toInt())).toByte()
            }

            val challengeResultRet = SoftSimNative.challengeResult(ChallengeRes, ChallengeRes.size)
            JLog.logv("getChallenge challengeResultRet:$challengeResultRet ChallengeRes:${ChallengeRes.toHex()} rand_temp:${rand2.toHex()}")

            SoftSimNative.setDataPath(Configuration.simDataDir)
//        val pwd = SecureUtil.getKeyFromAndroidKeyStore("soKey1")

//            val pwd1 = byteArrayOf(2, 3, 33, 2, 3, 33, 2, 3, 33, 2, 3, 33, 2, 3, 33, 34)
//            val pwd2 = byteArrayOf(25, 4, 5, 5, 5, 25, 4, 5, 5, 5, 25, 4, 5, 5, 5, 3)
            val pwd = SecureUtil.getToken("soKey")
            val pwd1 =pwd!!.copyOfRange(0,15)
            val pwd2 =pwd!!.copyOfRange(15,31)

            if (pwd1 != null && pwd2 != null) {
                SoftSimNative.setDbPwd(pwd1, pwd2)
            } else {
                JLog.loge("getPassword fail!!")
            }

        }else{
            JLog.loge("getChallenge fail ret :$ret")
        }

    }

    fun loadFiles(context: Context, api:SystemApiIf){
        val dataDir = Configuration.appDataDir

        logd("start load files: $dataDir")

        //复制文件之前，获取判断一下simDataDir 目录下有没有uC_Prefab ，如果没有，旧版so库，应清理旧库，并提示重新下载软卡
        checkAndDeleOld(dataDir)

        logd("copy files: ${api.getExtLibs()}")
        //复制文件
        //copyFilesNew(context, api.getExtLibs().toTypedArray(), Configuration.extLibDir)
        copyFilesNew(context, api.getSimCommFiles().toTypedArray(), Configuration.simDataDir)
        copyFiles(context, simCommonDataDb, Configuration.simDataDir)
        //copy mbn 文件到指定目录/data/misc/radio/
        copyFilesNew(context, api.getModemCfgFiles().toTypedArray(), Configuration.modemCfgDataDir)  // TODO: qcom平台才有，其他的都没有

        copyFiles(context, systemCFG, Configuration.preferencesDir)

        loadLibraries(api.getExtLibs().toTypedArray(), context.applicationInfo.nativeLibraryDir)
        //        loadLibraries(extLibs)


//        LogcatHelper.getInstance(context).start()


        //设置softsim数据库路径
//                SoftSimNative.setDataPath(Configuration.simDataDir)
        //启动QXDMlog线程
        ChallengeSimSo()
    }
}
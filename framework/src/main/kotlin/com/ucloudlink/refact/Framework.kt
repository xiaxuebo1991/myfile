package com.ucloudlink.refact

import android.annotation.SuppressLint
import android.content.Context
import android.os.HandlerThread
import com.ucloudlink.refact.access.ui.AccessEntryAdapter
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.BootOptimize
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by chentao on 2016/6/22.
 * Framework初始化，配置脚本载入
 */
object Framework {
    val lock = Any()
    private var isInited = false
    @SuppressLint("StaticFieldLeak")
    lateinit var accessEntryAdapter: AccessEntryAdapter

    fun environmentInit(context: Context) :AccessEntryAdapter {
        if (isInited) return accessEntryAdapter

        synchronized(lock) {
            try {
                if (isInited) return accessEntryAdapter
                BootOptimize.init("Framework")
                accessEntryAdapter = AccessEntryAdapter(context)
                isInited = true
                logd("Framework environmentInit >>current version:${Configuration.frameworkVersion}")

                val initThread = object : HandlerThread("initThread"){
                    override fun onLooperPrepared() {
                        ServiceManager.initDependencies(context, accessEntryAdapter)
                    }
                }
                initThread.start()
                logd("init thread start!!!")

                /*val testAlias = "abcded"
                val testEnText :ByteArray = encyption("ppppp6666666666".toByteArray(), testAlias)
                logd("testEnText ${Base64.encodeToString(testEnText,Base64.DEFAULT)}")
                val testDeText = decyption(testEnText, testAlias)
                logd("testDeText ${String(testDeText)}")*/
                logd("environment init success!")
            }catch (e:Throwable){
                e.printStackTrace()
            }
        }
        return  accessEntryAdapter
    }

    fun reset() {

    }

//    private fun getAesKey(keyAlias:String) : SecretKey {
//        var mStore: KeyStore?= null
//        if(mStore == null) {
//            mStore = KeyStore.getInstance("AndroidKeyStore")
//        }
//        mStore?.load(null)
//        var secretKey = mStore?.getKey(keyAlias,null)
//        if(secretKey==null) {
//            logd("xhandlerSecurePacket key generateKey start ")
//            val keyG: KeyGenerator = KeyGenerator.getInstance(
//                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
//            keyG.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT + KeyProperties.PURPOSE_DECRYPT)
//                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
//                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
//                    .build());
//            secretKey = keyG.generateKey()
//
//            logd("xhandlerSecurePacket key generateKey end")
//
//        }
//        return secretKey as SecretKey
//    }
//
//    private val CIPHERMODE = "AES/CBC/PKCS7Padding" //algorithm/mode/padding
//
//    @Throws(Exception::class)
//    fun encyption(needEncryptWord: ByteArray, keyAlias: String): ByteArray {
//
//        logd("dddEncyption->" + needEncryptWord[0]);
//        val secretKey = getAesKey(keyAlias)
//        if(secretKey==null){
//            throw NullPointerException("secretKey is null")
//        }
//        logd("dddEncyption secretKey->" + secretKey.algorithm+","+secretKey.format+","+secretKey.encoded);
//
//        val cipher = Cipher.getInstance(CIPHERMODE)
//        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
//
//        val encodedBytes = cipher.doFinal(needEncryptWord)
//        //保存解密需要的IV变量
//        SharedPreferencesUtils.putString(mContext,"AES-"+keyAlias, Base64.encodeToString(cipher.iv, Base64.DEFAULT))
//        return encodedBytes
//    }
//
//    @Throws(Exception::class)
//    fun decyption(needDecryptWord: ByteArray,keyAlias: String): ByteArray {
//        logd("needDecryptWord->" + needDecryptWord[0]);
//        val cipher = Cipher.getInstance(CIPHERMODE)
//        var mStore: KeyStore?= null
//        if(mStore == null) {
//            mStore = KeyStore.getInstance("AndroidKeyStore")
//        }
//        mStore?.load(null)
//        val secretKey = mStore?.getKey(keyAlias, null) as SecretKey
//        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(Base64.decode(SharedPreferencesUtils.getString(mContext,"AES-"+keyAlias), Base64.DEFAULT)))
//        val decodedBytes = cipher.doFinal(needDecryptWord)
//        return decodedBytes
//    }
}
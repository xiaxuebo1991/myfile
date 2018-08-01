package com.ucloudlink.refact.utils;


import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.jetbrains.annotations.NotNull;

import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import kotlin.jvm.internal.Intrinsics;

/**
 * Created by chunjiao.li on 2017/8/22.
 * Description:注意：对用一个数据的加解密要使用相同的privateWord。不同的数据请自行添加privateWord值。
 */

public class EncryptUtils {
    private static final String CIPHERMODE = "AES/CBC/PKCS7Padding"; //algorithm/mode/padding

    public static SecretKey getAesKey(String privateWord) {
        try {
            KeyStore mStore;
            mStore = KeyStore.getInstance("AndroidKeyStore");
            mStore.load(null);
            Key secretKey = mStore.getKey(privateWord, null);
            if (secretKey == null) {
                KeyGenerator keyG = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                keyG.init(new KeyGenParameterSpec.Builder(privateWord, KeyProperties.PURPOSE_ENCRYPT + KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());
                secretKey = keyG.generateKey();
            }
            return (SecretKey) secretKey;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 加密接口
     *
     * @param source      待加密数据
     * @param privateWord 加密数据字典值，每个加密数据需要传不同的字典值
     * @return
     */
    public static String encyption(Context context, String source, String ref, String privateWord) {
        try {
            SecretKey secretKey = getAesKey(privateWord);
            if (secretKey == null) {
                return "";
            }
            Cipher cipher = Cipher.getInstance(CIPHERMODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encodedBytes = cipher.doFinal(source.getBytes());
            //保存解密需要的IV变量
            //SysConf.getInstance().setIVvariable("AES-" + privateWord, Base64.encodeToString(cipher.getIV(), Base64.DEFAULT));
            SharedPreferencesUtils.putString(context, ref, "iv-"+privateWord,
                    Base64.encodeToString(cipher.getIV(), Base64.DEFAULT));
            return Base64.encodeToString(encodedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            JLog.logd("encodedBytes error" + e.toString());
            return "";
        }
    }

    /**
     * 解密接口
     *
     * @param source      待解密数据
     * @param privateWord 解密数据字典值，解密数据要使用加密数据使用的字典值
     * @return
     */
    public static String decyption(Context context, String source,String ref, String privateWord) {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance(CIPHERMODE);
            KeyStore mStore = null;
            mStore = KeyStore.getInstance("AndroidKeyStore");
            mStore.load(null);
            SecretKey secretKey = (SecretKey) mStore.getKey(privateWord, null);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,  new IvParameterSpec(
                    Base64.decode(SharedPreferencesUtils.getString(context, ref, "iv-"+privateWord,""), Base64.DEFAULT)));
            //JLog.d("source lenth " + source.length());
            byte[] decodedBytes = cipher.doFinal(Base64.decode(source, Base64.DEFAULT));
            return new String(decodedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            JLog.logd("decyption error" + e.toString());
            return "";
        }
    }

    public static byte[] getSHA1(byte[] data) {
        if(data == null){
            return null;
        }
        MessageDigest sha1 = null;
        try {
            sha1 = MessageDigest.getInstance("SHA1");
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
            return null;
        }
        sha1.update(data);
        byte[] var10000 = sha1.digest();
        return var10000;
    }

    public static String getMd5Digest(String str) {
        String newstr = "null";
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            newstr = HexUtil.encodeHexStr(md5.digest(str.getBytes("utf-8")));
            //JLog.logd(TAG, "getMd5Digest: " + str + " -> " + newstr);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return newstr;
        }
    }
}

package com.ucloudlink.framework.util;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
//import javax.crypto.Cipher;

/**
 * Created by jiaming.liang on 2017/5/25.
 */
public class EncryptionUtil3 {

    Cipher ecipher;
    Cipher dcipher;
    static String ke = "9238513401340235";

    public EncryptionUtil3(String password) {
        String ke = "9238513401340235";
        // 8-bytes Salt
        byte[] salt = {(byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32, (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03};
        // Iteration count
        int iterationCount = 19;
        try {
            Key keySpec = new SecretKeySpec(password.getBytes(), "AES");
            //            SecretKey key = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(keySpec);
            dcipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            // Prepare the parameters to the cipthers
            //            AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);
            AlgorithmParameterSpec iv = new IvParameterSpec(ke.getBytes());
            ecipher.init(KeyProperties.PURPOSE_ENCRYPT, keySpec, iv);
            dcipher.init(KeyProperties.PURPOSE_DECRYPT, keySpec, iv);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e1) {
            e1.printStackTrace();
        }
    }

    private static Object lock = new Object();

    /**
     * so 校验
     * @param source
     * @param key1
     * @param key2
     * @return
     */
    public static byte[] getSoAuthResult(byte[] source, byte[] key1, byte[] key2) {
        try {
            Key keySpec = new SecretKeySpec(key1, "AES");
            Cipher ecipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            AlgorithmParameterSpec iv = new IvParameterSpec(key2);
            ecipher.init(KeyProperties.PURPOSE_ENCRYPT, keySpec, iv);
            ecipher.update(source);
            byte[] bytes = ecipher.doFinal();
            Log.d("softsim", "getSoAuthResult: "+bytes.length);
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Takes a single String as an argument and returns an Encrypted version
     * of that String.
     *
     * @param str      String to be encrypted
     * @param password
     * @return <code>String</code> Encrypted version of the provided String
     */
    public static byte[] encrypt(String str, String password) {
        try {
            // Encode the string into bytes using utf-8
            Key keySpec = new SecretKeySpec(password.getBytes(), "AES");
            byte[] utf8 = str.getBytes("UTF8");
            // Encrypt
            Cipher ecipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            AlgorithmParameterSpec iv = new IvParameterSpec(ke.getBytes());
            ecipher.init(KeyProperties.PURPOSE_ENCRYPT, keySpec, iv);
            ecipher.update(utf8);
            byte[] enc = ecipher.doFinal();
            // Encode bytes to base64 to get a string
            //return new sun.misc.BASE64Encoder().encode(enc);
            return enc;
        } catch (BadPaddingException | IllegalBlockSizeException | UnsupportedEncodingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Takes a encrypted String as an argument, decrypts and returns the
     * decrypted String.
     *
     * @param dec      Encrypted String to be decrypted
     * @param password
     * @return <code>String</code> Decrypted version of the provided String
     */
    public static String decrypt(byte[] dec, String password) {
        try {
            // Decode base64 to get bytes
            //byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);
            //byte[] dec = Base64Coder.decode(str);
            // Decrypt
            Key keySpec = new SecretKeySpec(password.getBytes(), "AES");
            AlgorithmParameterSpec iv = new IvParameterSpec(ke.getBytes());
            Cipher dcipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            dcipher.init(KeyProperties.PURPOSE_DECRYPT, keySpec, iv);
            dcipher.update(dec);
            byte[] utf8 = dcipher.doFinal();
            // Decode using utf-8
            return new String(utf8, "UTF8");
        } catch (BadPaddingException | IllegalBlockSizeException | UnsupportedEncodingException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] toByte(String hexString) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).byteValue();
        return result;
    }

    public static String toHex(byte[] buf) {
        if (buf == null)
            return "";
        StringBuffer result = new StringBuffer(2 * buf.length);
        for (int i = 0; i < buf.length; i++) {
            appendHex(result, buf[i]);
        }
        return result.toString();
    }

    private final static String HEX = "0123456789ABCDEF";

    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
    }
}

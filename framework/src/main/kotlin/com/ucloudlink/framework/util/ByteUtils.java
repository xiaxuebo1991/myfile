package com.ucloudlink.framework.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.ucloudlink.refact.utils.JLog.logd;

public class ByteUtils {
    private static ByteBuffer longbuffer = ByteBuffer.allocate(8);
    private static ByteBuffer intbuffer = ByteBuffer.allocate(4);

    private static boolean bigEndian = false;

    public static void setBigEndian(boolean b) {
        bigEndian = b;
    }

    public static boolean isBigEndian() {
        return bigEndian;
    }


    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    
    public static byte[] hexStringToByte(String inputHex) {
        String hex = inputHex.toUpperCase();
        int len = (hex.length() / 2);
        byte[] result = new byte[len];
        char[] achar = hex.toCharArray();
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            result[i] = (byte) (toByte(achar[pos]) << 4 | toByte(achar[pos + 1]));
        }
        return result;
    }
    public static void printBytes(byte[] byteKi) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : byteKi) {
            stringBuilder.append(b+",");
        }
        logd("printBytes: "+stringBuilder.toString());
    }
    private static byte toByte(char c) {
        byte b = (byte) "0123456789ABCDEF".indexOf(c);
        return b;
    }
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    public static String bytesToHex(byte[] bytes, int len) {
        char[] hexChars = new char[len * 2];
        for (int j = 0; j < len; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static int readIntFromBuffer(byte[] buffer, int offset) {
        int a0, a1, a2, a3;
        if (bigEndian) {
            a0 = 0;
            a1 = 1;
            a2 = 2;
            a3 = 3;
        } else {
            a0 = 3;
            a1 = 2;
            a2 = 1;
            a3 = 0;
        }

        int ret = ((buffer[a0 + offset] & 0xff) << 24)
                | ((buffer[a1 + offset] & 0xff) << 16)
                | ((buffer[a2 + offset] & 0xff) << 8)
                | (buffer[a3 + offset] & 0xff);

        return ret;
    }

    public static void writeIntToBuffer(byte[] buffer, int offset, int value) {
        int a0, a1, a2, a3;
        if (bigEndian) {
            a0 = 0;
            a1 = 1;
            a2 = 2;
            a3 = 3;
        } else {
            a0 = 3;
            a1 = 2;
            a2 = 1;
            a3 = 0;
        }
        buffer[a0 + offset] = ((byte) ((value >> 24) & 0xff));
        buffer[a1 + offset] = ((byte) ((value >> 16) & 0xff));
        buffer[a2 + offset] = ((byte) ((value >> 8) & 0xff));
        buffer[a3 + offset] = ((byte) (value & 0xff));
    }

    public static long readLongFromBuffer(byte[] buffer, int offset) {
        int a0, a1, a2, a3, a4, a5, a6, a7;
        if (bigEndian) {
            a0 = 0;
            a1 = 1;
            a2 = 2;
            a3 = 3;
            a4 = 4;
            a5 = 5;
            a6 = 6;
            a7 = 7;
        } else {
            a0 = 7;
            a1 = 6;
            a2 = 5;
            a3 = 4;
            a4 = 3;
            a5 = 2;
            a6 = 1;
            a7 = 0;
        }

        long ret = ((long) (buffer[a0 + offset] & 0xff) << 56)
                | ((long) (buffer[a1 + offset] & 0xff) << 48)
                | ((long) (buffer[a2 + offset] & 0xff) << 40)
                | ((long) (buffer[a3 + offset] & 0xff) << 32)
                | ((long) (buffer[a4 + offset] & 0xff) << 24)
                | ((long) (buffer[a5 + offset] & 0xff) << 16)
                | ((long) (buffer[a6 + offset] & 0xff) << 8)
                | ((long) buffer[a7 + offset] & 0xff);

        return ret;
    }

    public static byte[] readByteArrayFromBuffer(byte[] buffer, int offset, int length) {
        if (length == 0) return null;
        if (buffer.length < offset + length) return null;

        byte[] ba = new byte[length];
        while (length > 0) {
            ba[length - 1] = buffer[offset + length - 1];
            length--;
        }
        return ba;
    }

    public static void writeLongToBuffer(byte[] buffer, int offset, long value) {
        int a0, a1, a2, a3, a4, a5, a6, a7;
        if (bigEndian) {
            a0 = 0;
            a1 = 1;
            a2 = 2;
            a3 = 3;
            a4 = 4;
            a5 = 5;
            a6 = 6;
            a7 = 7;
        } else {
            a0 = 7;
            a1 = 6;
            a2 = 5;
            a3 = 4;
            a4 = 3;
            a5 = 2;
            a6 = 1;
            a7 = 0;
        }

        buffer[a0 + offset] = ((byte) ((value >> 56) & 0xff));
        buffer[a1 + offset] = ((byte) ((value >> 48) & 0xff));
        buffer[a2 + offset] = ((byte) ((value >> 40) & 0xff));
        buffer[a3 + offset] = ((byte) ((value >> 32) & 0xff));
        buffer[a4 + offset] = ((byte) ((value >> 24) & 0xff));
        buffer[a5 + offset] = ((byte) ((value >> 16) & 0xff));
        buffer[a6 + offset] = ((byte) ((value >> 8) & 0xff));
        buffer[a7 + offset] = ((byte) (value & 0xff));
    }

    public static byte[] longToBytes(long x) {
        longbuffer.position(0);
        longbuffer.putLong(0, x);
        return longbuffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        int len = 8;
        if (bytes.length <= len) len = bytes.length;
        longbuffer.position(0);
        longbuffer.put(bytes, 0, len);
        longbuffer.flip();//need flip 
        return longbuffer.getLong();
    }

    public static byte[] intToBytes(int x) {
        intbuffer.position(0);
        intbuffer.putInt(x);
        return intbuffer.array();
    }

    public static int bytesToInt(byte[] bytes) {
        int len = 4;
        if (bytes.length <= len) len = bytes.length;
        intbuffer.position(0);
        intbuffer.put(bytes, 0, len);
        intbuffer.flip();//need flip 
        return intbuffer.getInt();
    }

    public static byte readByteFromBuffer(byte[] data, int pos) {
        return data[pos];
    }

    public static void writeByteToBuffer(byte[] buffer, int offset,
                                         int value) {
        buffer[offset] = ((byte) ((value >> 24) & 0xff));
    }


    public static void writeShortToBuffer(byte[] buffer, int offset,
                                          short value) {
        int a0, a1;
        if (bigEndian) {
            a0 = 0;
            a1 = 1;
        } else {
            a0 = 1;
            a1 = 0;
        }
        buffer[a0 + offset] = ((byte) ((value >> 8) & 0xff));
        buffer[a1 + offset] = ((byte) (value & 0xff));
    }

    public static short readShortFromBuffer(byte[] buffer, int offset) {
        int a0, a1;
        if (bigEndian) {
            a0 = 0;
            a1 = 1;
        } else {
            a0 = 1;
            a1 = 0;
        }
        short ret = (short) (((buffer[a0 + offset] & 0xff) << 8)
                | ((buffer[a1 + offset] & 0xff)));

        return ret;
    }

    public static double readDoubleFromBuffer(byte[] buffer, int offset) {
        ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        double ret = 0;
        byte[] array = new byte[8];
        System.arraycopy(buffer, offset, array, 0, 8);

        ret = ByteBuffer.wrap(array).order(order).getDouble();

        return ret;
    }
}

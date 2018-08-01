package com.ucloudlink.refact.utils

/**
 * Created by shiqianhua on 2018/1/15.
 */
object MemUtil {
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
            if (data1[i] != data2[i]) {
                JLog.logi("UService memcmp test.${data1[i]} and ${data2[i]},$i")
                bEquals = false
                break
            }
            i++
        }

        return bEquals
    }
}
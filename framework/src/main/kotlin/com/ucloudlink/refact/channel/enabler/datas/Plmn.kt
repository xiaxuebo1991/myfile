package com.ucloudlink.refact.channel.enabler.datas

/**
 * Created by jiaming.liang on 2017/8/8.
 */
data class Plmn(val mccmnc: String
                , val rat: Int    //制式
                , val signalQuality: Int  //信号质量（0:低质量，1：高质量）
                , val signalStrength: Int  //信号强度（正数表示，数值越小强度越高）
                , val time: Long  //plmn上报时间，这个时间使用的是 SystemClock.elapsedRealtime() 包含休眠时间
) {
    override fun toString(): String {
        return "Plmn(mccmnc=$mccmnc,rat=$rat)"
    }
    
    override fun hashCode(): Int {
        return 1
    }

    override fun equals(other: Any?): Boolean {
        other ?: return false
        if (other !is Plmn) return false
        return other.mccmnc == mccmnc && other.rat == rat
    }
}

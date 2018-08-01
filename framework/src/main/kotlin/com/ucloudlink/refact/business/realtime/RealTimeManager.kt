package com.ucloudlink.refact.business.realtime

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.loge
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by zhangxian on 2017/10/25.
 */

object RealTimeManager : Thread() {

    val REAL_TIME_SET = 1

    private val SP_ALL_TIME_COUNT = "alltime_count"//上次保存的总时间
    private val SP_UP_TIME_COUNT = "uptime_count"//上次保存的开机时间
    private val UC_SP = "uc_sp"

    private val REBOOT_KEY = "ril.radio.ucapp.reboot"
    private val VAL_REBOOT = "1"
    private val VAL_NORMAL = "2"

    var getRealTimeTimeout: Long = 120000
    private var mLooper: Looper? = null
    internal var mHandler: myHandler? = null
    private var netTimeFlag = true

    fun setRealTimeCount(){
        if(mHandler == null){
            loge("RealTimeManager is not initial!!!")
            return
        }
        val mCmd: Message

        mCmd = mHandler!!.obtainMessage(REAL_TIME_SET)
        mHandler!!.sendMessage(mCmd)
    }

    private fun setRealTime(){
        val mCmd: Message
        val webUrl = Configuration.TIME_URL
        var netMilliSeconds :Long = 0
        val realMilliSeconds: Long
        val upTime = SystemClock.elapsedRealtime()
        val reboot = false //测试过程，先默认没重启 后面再想办法区分

        /*
        去掉设置系统属性
        if(VAL_NORMAL == SystemProperties.get(REBOOT_KEY, VAL_NORMAL)) {
            SystemProperties.set(REBOOT_KEY, VAL_REBOOT)
            reboot = true
        }else{
            reboot = false
        }*/

        JLog.logd("setRealTime enter upTime:$upTime reboot:$reboot")

        if(netTimeFlag){
            try {
                val url = URL(webUrl) // 取得资源对象
                val uc = url.openConnection() // 生成连接对象
                uc.connectTimeout = 15000
                uc.readTimeout = 15000
                JLog.logd("setRealTime connect")
                uc.connect() // 发出连接
                JLog.logd("setRealTime connect2")
                netMilliSeconds = uc.date // 读取网站日期时间
            }catch (e: Exception) {
                JLog.loge("setRealTime TIME_COUNT_START", e)
            }
        }

        //判断是否获取到网络时间
        if(netMilliSeconds != 0L){
            netTimeFlag = false
            JLog.logd("setRealTime getnetwork success")
            realMilliSeconds = netMilliSeconds
        }else{
            JLog.logd("setRealTime getnetwork fail")
            //上次保存的时间 + 上次到现在的时间差 = 现在的真实时间
            if(reboot){
                //如果为开机第一次，则无上次开机时间
                realMilliSeconds = getAllTimeSP() + upTime
            }else{
                realMilliSeconds = getAllTimeSP() + (upTime - getUpTimeSP())
            }
        }

        val date = Date(realMilliSeconds) // 转换为标准时间对象
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) // 输出北京时间
        JLog.logd("setRealTime realdata:" + sdf.format(date) + " realMilliSeconds:" + realMilliSeconds)

        try {
            ServiceManager.accessEntry.softsimEntry.updateOrderOutOfDate(realMilliSeconds)
        }catch (e: Exception) {
            JLog.loge("updateOrderOutOfDate:", e)
        }

        saveAllTimeSP(realMilliSeconds)
        saveUpTimeSP(upTime)

        mHandler!!.removeMessages(REAL_TIME_SET)
        mCmd = mHandler!!.obtainMessage(REAL_TIME_SET)
        mHandler!!.sendMessageDelayed(mCmd, getRealTimeTimeout)

        JLog.logd("setRealTime exit")
    }

    private fun saveAllTimeSP(value: Long) {
        val ucSp: SharedPreferences = ServiceManager.appContext.getSharedPreferences(UC_SP, 0)
        val edit = ucSp.edit()

        JLog.logd("saveAllTimeSP:$value")
        edit.putLong(SP_ALL_TIME_COUNT, value)
        edit.commit()
    }

    private fun getAllTimeSP() : Long{
        val ucSp: SharedPreferences = ServiceManager.appContext.getSharedPreferences(UC_SP, 0)
        val allTimeSp = ucSp.getLong(SP_ALL_TIME_COUNT, 0)

        JLog.logd("getAllTimeSP:$allTimeSp")
        return allTimeSp
    }

    private fun saveUpTimeSP(value: Long) {
        val ucSp: SharedPreferences = ServiceManager.appContext.getSharedPreferences(UC_SP, 0)
        val edit = ucSp.edit()

        JLog.logd("saveUpTimeSP:$value")
        edit.putLong(SP_UP_TIME_COUNT, value)
        edit.commit()
    }

    private fun getUpTimeSP() : Long{
        val ucSp: SharedPreferences = ServiceManager.appContext.getSharedPreferences(UC_SP, 0)
        val upTimeSp = ucSp.getLong(SP_UP_TIME_COUNT, 0)

        JLog.logd("getUpTimeSP:$upTimeSp")
        return upTimeSp
    }

    //获取真实时间接口，给外部使用
    fun getRealTime() : Long{
        var realMilliSeconds :Long = 0
        var reboot = true

       /*
       去掉写系统属性
       if(VAL_NORMAL == SystemProperties.get(REBOOT_KEY, VAL_NORMAL)) {
            SystemProperties.set(REBOOT_KEY, VAL_REBOOT)
            reboot = true
        }else{
            reboot = false
        }*/

        //上次保存的时间 + 上次到现在的时间差 = 现在的真实时间
        /*
        不能准确判断是否重启，暂时去掉,直接获取当前时间
        if(reboot){
            realMilliSeconds = getAllTimeSP() + SystemClock.elapsedRealtime()
        }else{
            realMilliSeconds = getAllTimeSP() + (SystemClock.elapsedRealtime() - getUpTimeSP())
        }*/

        realMilliSeconds =System.currentTimeMillis()

        val date = Date(realMilliSeconds) // 转换为标准时间对象
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) // 输出北京时间

        JLog.logd("getRealTime realdata:" + sdf.format(date) + " realMilliSeconds:" + realMilliSeconds + " reboot:" + reboot)

        return realMilliSeconds
    }

    class myHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            JLog.logd("RealTimeManager handleMessage:" + msg.what)
            try {
                when (msg.what) {//不同状态不同处理
                    REAL_TIME_SET -> {
                        setRealTime()
                    }
                }
            } catch (e: Exception) {
                JLog.loge("RealTimeManager handleMessage", e)
            }
        }
    }
    override fun run() {
        try {
            JLog.logd("RealTimeManager thread run")
            Looper.prepare()
            mLooper = Looper.myLooper()
            mHandler = myHandler(mLooper!!)
            setRealTime()
            Looper.loop()
        } catch (e: Exception) {
            JLog.loge("RealTimeManager thread run", e)
        } finally {
            JLog.logd("RealTimeManager thread end")
        }
    }

}

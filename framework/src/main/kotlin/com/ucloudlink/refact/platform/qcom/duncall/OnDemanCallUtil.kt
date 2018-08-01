package com.ucloudlink.framework.softsim


import android.os.SystemClock
import android.util.SparseArray
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.platform.qcom.duncall.DefaultNetworkManager
import com.ucloudlink.refact.platform.qcom.duncall.DunNetworkManager
import com.ucloudlink.refact.utils.JLog.logv

/**
 * Created by jiaming.liang on 2016/7/20.
 * Modify by Tim.wang on 2016/9/20
 */
object OnDemandPsCallUtil {
    var mContext = ServiceManager.appContext

    var dunNetworkManager: DunNetworkManager? = null
    // A cache of DunNetworkManager for SIMs
    var mDunNetworkManagerCache: SparseArray<DunNetworkManager> = SparseArray<DunNetworkManager>()

    var defaultNetworkManager: DefaultNetworkManager? = null

    // A cache of DunNetworkManager for SIMs
    var mDefaultNetworkManagerCache: SparseArray<DefaultNetworkManager> = SparseArray<DefaultNetworkManager>()
    //   }

    fun getDunNetworkManager(subId: Int): DunNetworkManager {
        synchronized(mDunNetworkManagerCache) {
            var manager = mDunNetworkManagerCache.get(subId)
            if (manager == null) {
                manager = DunNetworkManager(mContext, subId)
                mDunNetworkManagerCache.put(subId, manager)
            }
            return manager
        }
    }

    /**
     * @return 返回需要延迟关卡的秒数
     * 这个秒数包含了请求与释放的间隔延迟以及释放后需要等待的秒数
     */
    fun undoOnDemandPsCall(): Long {
        //释放后延迟关闭卡时间 
        var waitTime = 0L
        if (dunNetworkManager != null) {
            val currentTimeMillis = SystemClock.elapsedRealtime()
            val manager = dunNetworkManager as DunNetworkManager
            val MINIMUM_RELEASETIME = manager.minimumReleaseTime
            val delayTime = manager.releaseNetwork()
            
            if (delayTime > 0) {
                waitTime = delayTime + MINIMUM_RELEASETIME
            } else {//<=0
                val lastReleaseTime = manager.lastReleaseTime
                if (lastReleaseTime==0L){//此时可能未赋值
                    waitTime=MINIMUM_RELEASETIME
                }else{
                    val pastedTime = currentTimeMillis - lastReleaseTime
                    waitTime = if (pastedTime > MINIMUM_RELEASETIME) 0 else MINIMUM_RELEASETIME - pastedTime
                }
            }
            logv("get undoOnDemandPsCall need delay $waitTime ")
            return waitTime
        }
        return waitTime
    }

    fun onDemandPsCall(subId: Int): Long {

//        val preferredApn = ApnUtil.getPreferredApn(mContext, subId)
//        if (preferredApn == null) {
//            logd("preferredApn==null")
//        } else {
//            logd("newRequest: current apn type:" + preferredApn.type)
//        }
//        setDunNetStatu(netStatu.onGettingNet)
        dunNetworkManager = getDunNetworkManager(subId)
        return dunNetworkManager!!.acquireNetwork()
    }

    fun getDefaultNetworkManager(subId: Int): DefaultNetworkManager {
        synchronized(mDefaultNetworkManagerCache) {
            var manager = mDefaultNetworkManagerCache.get(subId)
            if (manager == null) {
                manager = DefaultNetworkManager(mContext, subId)
                mDefaultNetworkManagerCache.put(subId, manager)
            }
            return manager
        }
    }

}
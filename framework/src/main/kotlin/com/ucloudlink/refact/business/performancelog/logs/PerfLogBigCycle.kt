package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Big_cycle_statle
import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_big_cycle
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfUntil

/**
 * Created by haiping.liu on 2018/3/31.
 * 大循环事件
 */

object PerfLogBigCycle : PerfLogEventBase() {

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        any as String
        val cycle = Ter_big_cycle.Builder()
                .head(PerfUntil.getCommnoHead())
                .occur_time((System.currentTimeMillis() / 1000).toInt())
                .persent(PerfLog.getCurrentPersent())
                .state(getState(any))
                .build()
        PerfUntil.saveFreqEventToList(cycle)
    }

    fun getState(mStringStat: String): Big_cycle_statle {
        when (mStringStat) {
            "ParentState" -> {
                return Big_cycle_statle.mParentState
            }
            "DefaultState" -> {
                return Big_cycle_statle.mDefaultState
            }
            "RunState" -> {
                return Big_cycle_statle.mRunState
            }
            "RecoveryState" -> {
                return Big_cycle_statle.mRecoveryState
            }
            "StartState" -> {
                return Big_cycle_statle.mStartState
            }
            "InitState" -> {
                return Big_cycle_statle.mInitState
            }
            "SeedChEstablishState" -> {
                return Big_cycle_statle.mSeedChEstablishState
            }
            "PreloginIpCheckState" -> {
                return Big_cycle_statle.mPreloginIpCheckState
            }
            "LoginState" -> {
                return Big_cycle_statle.mLoginState
            }
            "InServiceState" -> {
                return Big_cycle_statle.mInServiceState
            }
            "LogoutState" -> {
                return Big_cycle_statle.mLogoutState
            }
            "LogoutWaitState" -> {
                return Big_cycle_statle.mLogoutWaitState
            }
            "WaitReloginState" -> {
                return Big_cycle_statle.mWaitReloginState
            }
            "WaitResetCardState" -> {
                return Big_cycle_statle.mWaitResetCardState
            }
            "VsimBegin" -> {
                return Big_cycle_statle.mVsimBegin
            }
            "DispatchVsimState" -> {
                return Big_cycle_statle.mDispatchVsimState
            }
            "GetVsimInfoState" -> {
                return Big_cycle_statle.mGetVsimInfoState
            }
            "DownloadState" -> {
                return Big_cycle_statle.mDownloadState
            }
            "StartVsimState" -> {
                return Big_cycle_statle.mStartVsimState
            }
            "VsimRegState" -> {
                return Big_cycle_statle.mVsimRegState
            }
            "VsimDatacallState" -> {
                return Big_cycle_statle.mVsimDatacallState
            }
            "VsimEstablishedState" -> {
                return Big_cycle_statle.mVsimEstablishedState
            }
            "VsimReleaseState" -> {
                return Big_cycle_statle.mVsimReleaseState
            }
            "SwitchVsimState" -> {
                return Big_cycle_statle.mSwitchVsimState
            }
            "WaitSwitchVsimState" -> {
                return Big_cycle_statle.mWaitSwitchVsimState
            }
            "WaitResetCloudSimState" -> {
                return Big_cycle_statle.mWaitResetCloudSimState
            }
            "PlugPullCloudSimState" -> {
                return Big_cycle_statle.mPlugPullCloudSimState
            }
            "ExceptionState" -> {
                return Big_cycle_statle.mExceptionState
            }
        }
        return Big_cycle_statle.mDefaultState
    }
}
package com.ucloudlink.refact.platform.sprd.channel.enabler.simcard

import android.content.Context
import android.net.NetworkInfo
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.plmnselect.SeedPlmnSelector
import com.ucloudlink.refact.channel.enabler.plmnselect.SwitchNetHelper
import com.ucloudlink.refact.channel.enabler.simcard.SeedEnabler2
import com.ucloudlink.refact.channel.monitors.AttachReject
import com.ucloudlink.refact.config.ENV_MCC_CHANGED
import com.ucloudlink.refact.platform.sprd.api.SprdApiInst
import com.ucloudlink.refact.platform.sprd.api.SprdApiInst.*
import com.ucloudlink.refact.product.mifi.PhyCardApn.ApnNetworkErrCB
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.printContent

/**
 * Created by shiqianhua on 2018/1/11.
 */
class SprdSeedEnabler(mContext: Context, mLooper: Looper) : SeedEnabler2(mContext, mLooper) {

    private var isDefault: Boolean = true
    private var needRestartRadio = false

    override fun isDefaultNet(): Boolean {
        return isDefault
    }

    override fun onNetStateUpdated(networkState: NetworkInfo.State, type: Int) {
        super.onNetStateUpdated(networkState, type)
        logv("networkState:$networkState type:$type")
        if (!isCardOn()) {
            logv("seedsim is not on")
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        if (mCard.subId < 0) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        val imsiBySlot = getImsiBySubId(mCard.subId)
        if (mCard.imsi != imsiBySlot) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            logv("not same imsi at slot:$imsiBySlot card.imsi: ${mCard.imsi} ")
            return
        }
        if (curDDSSlotId == mCard.slot) {
            isDefault = true
            updateNetState(mDefaultNetState)
        } else {
            isDefault = false
            updateNetState(mDunNetState)
        }
    }

    override fun triggerCall() {
        super.triggerCall()
        val defaultSubId = ServiceManager.systemApi.getDefaultDataSubId()
        logd("triggerCall defaultsubid $defaultSubId cardsubid ${mCard.subId}")
        if (defaultSubId == mCard.subId) {
            val manager = TelephonyManager.from(mContext)
            val dataState = manager.dataState
            if (dataState == TelephonyManager.DATA_CONNECTED) {
                isDefault = true
                updateNetState(NetworkInfo.State.CONNECTED)
            } else {
                loge("current seed default is not connect is need requestNetwork?")
            }
        } else {
            logd("seed card subid is not default subid!")
        }
    }

    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        logd("disable seed sim reason $reason isKeepChannel $isKeepChannel")
        return super.disable(reason, isKeepChannel)
    }


    val errcodeCb = object : ApnNetworkErrCB {
        var lastDeniedReason = 0
        var sameDeniedReasonHit = 0
            set(value) {
                field = value
                JLog.logv("sameDeniedReasonHit = $field")
                if (value > 0 && lastDeniedReason > 0) {
                    handleDenied(lastDeniedReason, field >= MAX_SAME_DENIED_HIT)
                }
            }

        private fun handleDenied(reason: Int, isDisable: Boolean) {
            val exception: EnablerException
            if (isDisable) {
                sameDeniedReasonHit = 0
                exception = EnablerException.EXCEPTION_REG_DENIED
            } else {
                exception = EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE
            }

            if (reason > 0) {
                exception.reason.errorCode = reason
                notifyException(exception, "reason:$reason", isDisable)
                logd("handleDenied Registration Denied reason:$reason")
            }
        }

        override fun networkErrUpdate(phoneId: Int, errcode: Int) {
            if (phoneId != mCard.slot) {
                logd("ignore")
                return
            }
            logd("SprdNetworkErrUpdate phoneId ${mCard.subId} $phoneId, errcode $errcode")
            val deniedReason = errcode
            val shouldIgnore = (SystemClock.uptimeMillis() - regListenTime) < 5000
            if (!shouldIgnore && deniedReason != 0) {
                if (deniedReason == lastDeniedReason) {
                    sameDeniedReasonHit++
                } else {
                    sameDeniedReasonHit = 1
                }
                lastDeniedReason = deniedReason
                //处理马上换卡原因
                when (deniedReason) {
                    4, 5, 11 -> {
                        handleDenied(deniedReason, true)
                    }
                    15 -> if (mCard.rat == PreferredNetworkType.SERVER_RAT_TYPE_4G) {
                        handleDenied(deniedReason, true)
                    }
                }
            }

            if (mCard.subId <= 0) return
            if (errcode == 33
                    && mCard.imsi.length > 5
                    && mCard.imsi.startsWith("20404")
                    && mCard.cardType == CardType.PHYSICALSIM) {
                // 需求32121 沃达丰物理卡遇到33错误不切网，去切换APN
                logd("wodafeng phy card, will change APN")
            } else {
                // 支持种子卡切网
                val registeredPlmn = ServiceManager.systemApi.getNetworkOperatorForSubscription(mCard.subId)
                SwitchNetHelper.switchOnDataCallFailed(errcode, AttachReject(mCard.subId, registeredPlmn))
            }
        }
    }

    override fun onCardReady() {
        super.onCardReady()
        ServiceManager.systemApi.registerNetworkErrCB(errcodeCb)

        if (mCard.cardType == CardType.PHYSICALSIM) {
            val result = SeedPlmnSelector.checkSeedFplmnUpdate(mCard.imsi, mCard.subId, mCard.cardType)

            val PrePlmnResult = SeedPlmnSelector.checkPhySeedPerPlmnUpdate(mCard.imsi, mCard.subId)

            doReFreshPlmn(result, PrePlmnResult, mCard.imsi)
        }
    }

    private fun doReFreshPlmn(fplmnRet: Pair<Boolean, Array<String>?>, prePlmnRet: Pair<Boolean, Array<String>?>, imsi: String) {
        var isNeedRestartRadio = false
        if (fplmnRet.first) {
            isNeedRestartRadio = true
            updatePlmn(UPDATEPLMN_TYPE_FPLMN, fplmnRet.second)
            SeedPlmnSelector.markPhyLastFplmn(imsi, fplmnRet.second)
            logv("[doReFreshPlmn] prePlmnRet:${fplmnRet.second?.printContent()}")
        }

        if (prePlmnRet.first) {
            isNeedRestartRadio = true
            updatePlmn(UPDATEPLMN_TYPE_FPLMN, prePlmnRet.second)
            SeedPlmnSelector.markPhyLastPerPlmn(imsi, prePlmnRet.second)
            logv("[doReFreshPlmn] prePlmnRet:${prePlmnRet.second?.printContent()}")
        }

        if (isNeedRestartRadio) {
            needRestartRadio = true
            disable(ENV_MCC_CHANGED)
        }
    }

    override fun doReFlashPhyFplmn(imsi: String, newFplmn: Array<String>?) {
        needRestartRadio = true

        updatePlmn(UPDATEPLMN_TYPE_FPLMN, newFplmn)
        SeedPlmnSelector.markPhyLastFplmn(imsi, newFplmn)
        super.doReFlashPhyFplmn(imsi, newFplmn)
        logv("[doReFlashPhyFplmn] prePlmnRet:${newFplmn?.printContent()}")
        disable(ENV_MCC_CHANGED)
    }

    private fun updatePlmn(type: Int, newFplmn: Array<String>?) {
        val apiInst = getInstance()
        apiInst.updatePlmn(mCard.slot, type, UPDATEPLMN_ACTION_DELE_ALL, "", 1, 1, 1)
        newFplmn?.forEach {
            apiInst.updatePlmn(mCard.slot, type, UPDATEPLMN_ACTION_ADD, it, 1, 1, 1)
        }
    }

    override fun onCardAbsent(enablerClosing: Boolean, logout: Boolean, keepChannel: Boolean) {
        super.onCardAbsent(enablerClosing, logout, keepChannel)
        ServiceManager.systemApi.deregsiterNetworkErrCB(errcodeCb)
        if (needRestartRadio) {
            needRestartRadio = false
            val apiInst = SprdApiInst.getInstance()
            apiInst.restartRadio()
        }
    }
}
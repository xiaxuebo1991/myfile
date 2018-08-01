package com.ucloudlink.refact.product.mifi.cardselect

import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import java.util.*

/**
 * Created by shiqianhua on 2018/2/3.
 */
object CardSelectConfig {
    val SELECT_MODE_MANUAL = 0
    val SELECT_MODE_AUTO = 1

    val MAX_PHY_SLOT = 1

    val CONFIG_CARD_SLOT = "configCardSlot"
    val CONFIG_VSIM_SLOT = 0
    var configCardSlot: Int
        set(value) {
            logd("save configCardSlot $value")
            SharedPreferencesUtils.putInt(ServiceManager.appContext, CONFIG_CARD_SLOT, value)
        }
        get() {
            val slot = SharedPreferencesUtils.getInt(ServiceManager.appContext, CONFIG_CARD_SLOT, 0)
            logd("get configCardSlot $slot")
            return slot
        }

    val CONFIG_SELECT_MODE = "CONFIG_SELECT_MODE"
    var selectMode: Int
        set(value) {
            SharedPreferencesUtils.putInt(ServiceManager.appContext, CONFIG_SELECT_MODE, value)
        }
        get() {
            return SharedPreferencesUtils.getInt(ServiceManager.appContext, CONFIG_SELECT_MODE, SELECT_MODE_MANUAL)
        }

    val STATE_PHY_CARD = "STATE_PHY_CARD"
    var phyCardStatus:String
        set(value){
            SharedPreferencesUtils.putString(ServiceManager.appContext, STATE_PHY_CARD, value)
        }
        get(){
            return SharedPreferencesUtils.getString(ServiceManager.appContext, STATE_PHY_CARD, "")
        }


    data class CardMap(val logicSlot: Int, val realSlot: Int)

    val phyCardSlotMap = arrayListOf(
            CardMap(1, 0)   // 云卡卡槽为0
    )

    fun getRealPhySlot(loginSlot: Int): Int {
        for (itr in phyCardSlotMap) {
            if (itr.logicSlot == loginSlot) {
                return itr.realSlot
            }
        }
        loge("cannot find real phy slot! loginSlot$loginSlot")
        return 0
    }

    fun contansPhySlot(loginSlot: Int): Boolean {
        for (itr in phyCardSlotMap) {
            if (itr.logicSlot == loginSlot) {
                return true
            }
        }
        return false
    }

    fun getTotalPhyCard():Int{
        return phyCardSlotMap.size
    }

    fun getRealPhySlotList(): ArrayList<Int> {
        var list = ArrayList<Int>()
        for (itr in phyCardSlotMap) {
            list.add(itr.realSlot)
        }

        return list
    }

    fun getLogicSlotByRealSlot(realSlot: Int): Int {
        JLog.logd("realSlot == "+realSlot)
        for (itr in phyCardSlotMap) {
            if (itr.realSlot == realSlot) {
                return itr.logicSlot
            }
        }
        loge("cannot find logic slot! 2")
        return 0
    }

    fun getLogicSlotByIdx(index:Int):Int{
        var i = 0
        for (itr in phyCardSlotMap) {
            if(i == index){
                return itr.logicSlot
            }
        }
        loge("cannot find logic slot! 3")
        return 0
    }

    fun getRealSlotByIdx(index: Int):Int{
        var i = 0
        for (itr in phyCardSlotMap) {
            if(i == index){
                return itr.realSlot
            }
        }
        loge("cannot find logic slot! 3")
        return 0
    }
}
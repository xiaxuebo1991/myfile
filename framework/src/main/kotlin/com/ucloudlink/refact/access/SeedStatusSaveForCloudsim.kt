package com.ucloudlink.refact.access

import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.DeType
import rx.Subscription

/**
 * Created by shiqianhua on 2017/6/21.
 */
object SeedStatusSaveForCloudsim {
    enum class SaveType {
        NONE,
        PHYSIM,
        SOFTSIM,
    }

    var seedSaveType = SaveType.NONE
    var cloudCardStatusOb: Subscription? = null
    private var isSet: Boolean = false

    init {

    }

    fun registSubCardStatus() {
        if (isSet) {
            logd("already set registSubCardStatus")
            return
        }
        isSet = true
        logd("registSubCardStatus start !")
        cloudCardStatusOb = ServiceManager.cloudSimEnabler.cardStatusObser().subscribe(
                {
                    var newSaveType: SaveType
                    logd("cloudsim status change! " + it)
                    if (it == CardStatus.POWERON) {
                        logd("save cloudsim seed type!")

                        if (ServiceManager.seedCardEnabler.getDeType() == DeType.WIFI) {
                            newSaveType = SaveType.NONE
                        } else {
                            logd("card info: ${ServiceManager.seedCardEnabler.getCard()}")
                            if (ServiceManager.seedCardEnabler.getCard().cardType == CardType.PHYSICALSIM) {
                                newSaveType = SaveType.PHYSIM
                            } else {
                                newSaveType = SaveType.SOFTSIM
                            }
                        }

                        logd("seedSaveType change: ${seedSaveType} -> $newSaveType")
                        seedSaveType = newSaveType
                    }
                },
                {

                }
        )
    }
}
package com.ucloudlink.refact.channel.enabler.plmnselect

import com.ucloudlink.refact.channel.enabler.datas.CardType

object SeedPlmnSelector {
    val seedFplmnSelector = SeedFplmnSelector()
    val seedPrePlmnSelector = SeedPrePlmnSelector()

    @Synchronized
    fun updateEvent(event: Int, arg: Any? = null) {
        seedFplmnSelector.updateEvent(event, arg)
    }

    fun getCurrentMccFplmnByImsi(imsi: String, cardType: CardType): Array<String>? {
        return seedFplmnSelector.getCurrentMccFplmnByImsi(imsi, cardType)
    }

    fun markSoftSeedLastFplmn(imsi: String, fplmn: Array<String>?) {
        seedFplmnSelector.markSoftSeedLastFplmn(imsi, fplmn)
    }

    fun markPhyLastFplmn(imsi: String, newFplmn: Array<String>?) {
        seedFplmnSelector.markPhyLastFplmn(imsi, newFplmn)
    }

    fun checkSeedFplmnUpdate(imsi: String, subId: Int, cardType: CardType): Pair<Boolean, Array<String>?> {
        return seedFplmnSelector.checkSeedFplmnList(imsi, subId, cardType)
    }

    fun checkPhySeedPerPlmnUpdate(imsi: String, subId: Int): Pair<Boolean, Array<String>?> {
        return seedPrePlmnSelector.checkPhySeedPerPlmnUpdate(imsi,subId)
    }

    fun markPhyLastPerPlmn(imsi: String, newFplmn: Array<String>?) {
        seedPrePlmnSelector.markPhyLastPerPlmn(imsi,newFplmn)
    }

    fun getCurrentMccPreferredPlmn(imsi: String, softsim: CardType): Array<String>? {
        return seedPrePlmnSelector.getCurrentMccPreferredPlmn(imsi,softsim)
    }
}
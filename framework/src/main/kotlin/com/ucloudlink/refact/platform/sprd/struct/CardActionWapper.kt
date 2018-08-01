package com.ucloudlink.refact.platform.sprd.struct

import com.ucloudlink.refact.channel.enabler.datas.Card

/**
 * Created by shiqianhua on 2018/1/12.
 */
val CARD_ACTION_POWERUP = 1
val CARD_ACTION_POWERDOWN = 2
data class CardActionWapper (val card: Card, val action:Int)
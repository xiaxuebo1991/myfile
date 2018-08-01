package com.ucloudlink.refact.product.mifi.connect.struct

/**
 * Created by shiqianhua on 2018/2/5.
 */

/**
 * dataChannel
 * GLOCALME = 0;                   //Cloud SIM
 * SIM1 = 1;                       //实体卡SIM 1
 * SIM2 = 2;                       //实体卡SIM 2
 * SMART = 3;                      //智能选卡
 */

val DATA_CHANNEL_GLOCALME = 0
val DATA_CHANNEL_SIM1 = 1
val DATA_CHANNEL_SIM2 = 2
val DATA_CHANNEL_SMART = 3

/**
 * sim cfg
 * DATA_CHANNEL = 0;
 * FLOW_TRADE = 1;
 * DISABLE = 2;
 */

val SIM_CFG_DATA_CHANNEL = 0
val SIM_CFG_FLOW_TRADE = 1
val SIM_CFG_DISABLE = 2

data class WebSimUserConfig(var dataChannel:Int = DATA_CHANNEL_GLOCALME,
                            var sim1Cfg:Int = SIM_CFG_DATA_CHANNEL, var sim2Cfg:Int = SIM_CFG_DATA_CHANNEL,
                            var sim1Exist:Int = 0, var sim2Exist:Int = 0,
                            var imsi1:String? = "", var imsi2:String ?= "",
                            var pin1:Boolean = false, var pin2:Boolean = false,
                            var roam1:Int = 0, var roam2:Int = 0,
                            var sim1New:Boolean = false, var sim2New:Boolean = false,
                            var puk1:Boolean = false, var puk2:Boolean = false){
    override fun toString(): String {
        return "WebSimUserConfig(dataChannel=$dataChannel, sim1Cfg=$sim1Cfg, sim2Cfg=$sim2Cfg, sim1Exist=$sim1Exist, sim2Exist=$sim2Exist, imsi1=$imsi1, imsi2=$imsi2, " +
                "pin1=$pin1, pin2=$pin2, roam1=$roam1, roam2=$roam2, sim1New=$sim1New, sim2New=$sim2New, puk1=$puk1, puk2=$puk2)"
    }
}
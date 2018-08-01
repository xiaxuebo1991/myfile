package com.ucloudlink.refact.product.mifi.connect.struct

/**
 * Created by shiqianhua on 2018/1/20.
 */
data class MsgDatacallInfo(var datacall:Int, var iptype:String, var dns:String, var ipaddr:String, var gateWay:String, var netMask:String, var sleepStatus:Int)
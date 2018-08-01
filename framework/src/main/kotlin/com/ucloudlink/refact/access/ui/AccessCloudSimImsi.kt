package com.ucloudlink.refact.access.ui

/**
 * Created by jiaming.liang on 2016/6/2.
 */
class AccessCloudSimImsi private constructor() {
    //sim卡的15位卡号
    var imsi = ""

//    // 卡状态
//    var state = state_off
//        private set(state) {
//            if (this.state != state) {
//                field = state
//            }
//        }
//
//    fun turningOn() {
//        state = state_turning_on
//    }
//
//    fun close() {
//        state = state_off
//    }
//
//    fun switching() {
//        state = state_switching
//    }
//
//    fun stateOn() {//切换成功
//        state = state_on
//    }

    companion object {
//        private val state_off = 0
//        private val state_turning_on = 1
//        private val state_on = 2
//        private val state_switching = 3
//        //*****以上是各种状态*************************//

        val instance: AccessCloudSimImsi by lazy(this, { AccessCloudSimImsi() })
    }
}
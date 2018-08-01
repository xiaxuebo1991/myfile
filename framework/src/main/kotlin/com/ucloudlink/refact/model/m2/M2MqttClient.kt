package com.ucloudlink.refact.model.m2

import android.content.Context
import com.ucloudlink.refact.utils.JLog
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import java.util.concurrent.Executors

/**
 * Created by zhanlin.ma on 2018/3/28.
 */
class M2MqttClient(context: Context){
    val mqttServer = "tcp://127.0.0.1:1883"
    val myModule = "uC_Uservice_AT"
    val atModule = "uC_m2atcmd"
    val ctx = context
    val otherModuleList = listOf<String>(atModule)

    lateinit var asyncClient: MqttAsyncClient
    var mqttConnectOptions: MqttConnectOptions

    init {
        mqttConnectOptions = initOptional(10, 60)
        try {
            val mqttCallback = object : MqttCallback {
                override fun connectionLost(cause: Throwable) {
                    JLog.loge("connectionLost: cause:" + cause.message)
                    JLog.loge("connectionLost: do the reconnect!")
                    reconnect()
                }

                @Throws(Exception::class)
                override fun messageArrived(topic: String, message: MqttMessage) {
                    JLog.loge("messageArrived: msg recv:$topic, $message")

                    ATMsgDecode.decodePackage(this@M2MqttClient, topic, message.payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    JLog.logd("deliveryComplete: token" + token)
                }
            }
            asyncClient = MqttAsyncClient(mqttServer, myModule, MemoryPersistence())
            asyncClient.setCallback(mqttCallback)
            asyncClient.connect(mqttConnectOptions, context, ActionListener())
        } catch (e: MqttException) {
            e.printStackTrace()
            JLog.loge("M2MqttAndClient: mqtt error!" + e.message)
        }

    }

    private fun initOptional(timeout: Int, keepAlive: Int): MqttConnectOptions {
        val options = MqttConnectOptions()
        options.isCleanSession = true
        options.connectionTimeout = timeout
        options.keepAliveInterval = keepAlive
        //options.setAutomaticReconnect(true);
        return options
    }

    private inner class ActionListener : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            JLog.logd("onSuccess: connect succ!!!" + asyncActionToken)
            try {
                val list = getTopicListNeed(myModule, otherModuleList)
                for (topic in list) {
                    asyncClient.subscribe(topic, 0, ctx, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            JLog.logd("onSuccess: mqtt subscribe succ! ${asyncActionToken.topics}")
                            ATMsgEncode.sendTest(this@M2MqttClient)
                        }

                        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                            JLog.logd("onFailure: mqtt subscribe failed! ${asyncActionToken.topics} " + exception.message)
                            exception.printStackTrace()
                        }
                    })
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
            JLog.logd("onFailure: connect failed!" + asyncActionToken + ", " + exception.message)
            exception.printStackTrace()
            reconnect()
        }
    }

    internal var reconnectExecutor = Executors.newSingleThreadExecutor()
    fun reconnect() {
        reconnectExecutor.execute {
            try {
                Thread.sleep(100)
            } catch (e: Exception) {
                e.printStackTrace()
                JLog.loge("run: do nothing!")
            }

            var ok = false
            JLog.loge("Start Reconnect:" + Thread.currentThread().name)

            try {
                asyncClient.connect(mqttConnectOptions, ctx, ActionListener())
                ok = true
            } catch (e: MqttException) {
                e.printStackTrace()
                JLog.loge("run: reconnect failed!")
                ok = false
            }

            //Log.d(TAG, "Retry Count: " + retry + " Connected: " + connection.isConnected());
            if (!ok) {
                reconnect()
            }
        }
    }

    fun getTopicListNeed(module: String, moduleList: List<String>): ArrayList<String> {
        var list = ArrayList<String>()
        for (m in moduleList) {
            list.add(m + module)
        }
        return list
    }

    fun getTopic(src: String, dst: String): String {
        return src + dst;
    }

    fun sendMsg(dst: String, data: ByteArray): Int {
        try {
            val topic = getTopic(myModule, dst)
            val token = asyncClient.publish(topic, data, 1, false)
            JLog.logd("publish msg to $dst return token $token msg:${token.message}")
            return 0
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }
}
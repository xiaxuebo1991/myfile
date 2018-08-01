package com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack

import android.content.Context
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgDecode
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import java.util.concurrent.Executors

/**
 * Created by shiqianhua on 2018/1/16.
 */
class UcMqttClient(context: Context) {
    val mqttServer = "tcp://127.0.0.1:1883"
    val myModule = "uC_Uservice"
    val ledModule = "uC_Led"
    val webModule = "uC_WebServer"
    val gpsModule = "uC_GPS"
    val fotaModule = "uC_FOTA"
    val ctx = context

    val otherModuleList = listOf<String>(ledModule, webModule, gpsModule,fotaModule)

    lateinit var asyncClient: MqttAsyncClient
    lateinit var mqttConnectOptions: MqttConnectOptions

    init {
        mqttConnectOptions = initOptional(10, 60)
        try {
            val mqttCallback = object : MqttCallback {
                override fun connectionLost(cause: Throwable) {
                    loge("connectionLost: cause:" + cause.message)
                    loge("connectionLost: do the reconnect!")
                    reconnect()
                }

                @Throws(Exception::class)
                override fun messageArrived(topic: String, message: MqttMessage) {
                    loge("messageArrived: msg recv:$topic, $message")
//                    val modelW = getModuleWapper(topic)
//                    logd("get module wapper")
//                    if(modelW != null && modelW.dst.equals(myModule)){
//                        MsgDecode.decodePackage(this@UcMqttClient,modelW.src, message.payload)
//                    }
                    MsgDecode.decodePackage(this@UcMqttClient, topic, message.payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    logd("deliveryComplete: token" + token)
                }
            }
            asyncClient = MqttAsyncClient(mqttServer, myModule, MemoryPersistence())
            asyncClient.setCallback(mqttCallback)
            asyncClient.connect(mqttConnectOptions, context, ActionListener())
        } catch (e: MqttException) {
            e.printStackTrace()
            loge("MqttAndClient: mqtt error!" + e.message)
        }

    }

    private fun initOptional(timeout: Int, keepAlive: Int): MqttConnectOptions {
        val options = MqttConnectOptions()
        options.isCleanSession = true
        options.connectionTimeout = timeout
        options.keepAliveInterval = keepAlive
//        options.maxInflight = 100

        //options.setAutomaticReconnect(true);
        return options
    }

    private inner class ActionListener : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            logd("onSuccess: connect succ!!!" + asyncActionToken)
            try {
                val list = getTopicListNeed(myModule, otherModuleList)
                for (topic in list) {
                    asyncClient.subscribe(topic, 0, ctx, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            logd("onSuccess: mqtt subscribe succ! ${asyncActionToken.topics}")

                        }

                        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                            logd("onFailure: mqtt subscribe failed! ${asyncActionToken.topics} " + exception.message)
                            exception.printStackTrace()
                        }
                    })
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
            logd("onFailure: connect failed!" + asyncActionToken + ", " + exception.message)
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
                loge("run: do nothing!")
            }

            var ok = false
            loge("Start Reconnect:" + Thread.currentThread().name)

            try {
                asyncClient.connect(mqttConnectOptions, ctx, ActionListener())
                ok = true
            } catch (e: MqttException) {
                e.printStackTrace()
                loge("run: reconnect failed!")
                ok = false
            }

            //Log.d(TAG, "Retry Count: " + retry + " Connected: " + connection.isConnected());
            if (!ok) {
                reconnect()
            }
        }
    }

    inner class ModuleWapper(val src: String, val dst: String)

    fun getModuleWapper(topic: String): ModuleWapper? {
//        try {
//            val list = topic.split(splitChar)
//            if(list.size != 2){
//                loge("parse topic fail! $topic")
//                return null
//            }
//            return ModuleWapper(list[0], list[1])
//        }catch (e:Exception){
//            e.printStackTrace()
//            return null
//        }

        return ModuleWapper(topic, topic)
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
            logd("publish msg to $dst return token $token msg:${token.message}")
            return 0
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }
}
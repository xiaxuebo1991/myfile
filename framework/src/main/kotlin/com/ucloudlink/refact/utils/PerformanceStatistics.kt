package com.ucloudlink.refact.utils

import android.util.Log
import com.ucloudlink.refact.utils.JLog
import java.util.*

/**
 * Created by wuzhun on 2016/12/29.
 */

object PerformanceStatistics {
    val MAX_RECORDS_NUM:Int =  256
    var processRecords: ArrayList<ProcessRecords> = ArrayList()
    var autoStartIndex:Int = 0
    var process: ProcessState = ProcessState.UC_PROCESS_INIT
    set(state) {
        onStateSignalHandle(state)
    }

    @Synchronized fun onStateSignalHandle(state: ProcessState)
    {
        val curTime = Date(System.currentTimeMillis())
        logd("-->glocalme state goto "+state )

        if (state == ProcessState.UI_RESET_REQ) {
            clearAllProcessRecords()
        }

        if (state == ProcessState.AUTO_RUN_START) {
            if (checkAutoRunStarted() == false){
                clearAllProcessRecords()
            }
        }
        checkRecordExceed(MAX_RECORDS_NUM)
        var thisRecords: ProcessRecords = ProcessRecords(processState = state,happenTime = curTime)
        processRecords.add(thisRecords)

        this.autoStartIndex = findLastStateIndex(ProcessState.AUTO_RUN_START)

        when(state){
            ProcessState.CLOUD_CONNECTED ->{
                countSeedCardProcess()
                countFirstAuthPacketNum()
                onCloudConnectedRecord()
                clearAllProcessRecords()
            }
            ProcessState.AUTO_RUN_START ->{
                onCloudAutoRunStartRecord()
            }
            else ->{
            }
        }
    }

    @Synchronized fun clearAllProcessRecords()
    {
        processRecords.clear()
    }

    /*云卡Connected*/
    fun onCloudConnectedRecord()
    {
        //logd("Done: all records "+processRecords)
        var diff:Long = 0
        val startState: ProcessState = ProcessState.AUTO_RUN_START
        val endState: ProcessState = ProcessState.CLOUD_CONNECTED
        val startIndex:Int = findFirstStateIndex(startState)
        val endIndex:Int = findLastStateIndex(endState)
        val authStartIndex:Int = findFirstStateIndex(ProcessState.CLOUD_AUTH_REQ)

        if (startIndex >= 0 && endIndex >= 0 )
        {
            try {
                val startTime: Date = processRecords[startIndex].happenTime
                val endTime: Date = processRecords[endIndex].happenTime
                val authTime: Date = processRecords[authStartIndex].happenTime

                diff = calDiffTime(authTime,endTime)
                if (diff >= 0) processLogd(ProcessState.CLOUD_AUTH_REQ, ProcessState.CLOUD_CONNECTED,diff/1000)

                diff = calDiffTime(startTime,endTime)
                if (diff >= 0) processLogd(ProcessState.AUTO_RUN_START, ProcessState.CLOUD_CONNECTED,diff/1000)


            }catch (e:Exception){
                logd("catch a exception: "+e.toString())
            }
        }
    }

    /*AUTO RUN点击开始*/
    fun onCloudAutoRunStartRecord(){
    }

    /*判断记录arrayList是否溢出*/
    fun checkRecordExceed(allowNum:Int){
        if (processRecords.size > allowNum)
        {
            clearAllProcessRecords()
            logd("RecordExceed,so clear all old records")
        }
    }

    /*统计首次鉴权请求，回复，失败的个数*/
    fun countFirstAuthPacketNum():Unit
    {
        val indexStart = autoStartIndex
        val sizeList = processRecords.size
        var authReqNum:Int = 0
        var authRspNum:Int = 0
        var authFailNum:Int = 0

        if (indexStart < 0 || sizeList <= 0) return
        try {
            for (index in indexStart..sizeList-1){
                if (processRecords[index].processState == ProcessState.CLOUD_AUTH_REQ) {
                    authReqNum++
                } else if (processRecords[index].processState == ProcessState.CLOUD_AUTH_RSP){
                    authRspNum++
                }else if(processRecords[index].processState == ProcessState.CLOUD_AUTH_FAIL){
                    authFailNum++
                }
            }
        }catch (e:Exception){
            logd("countFistAuthPacketNum exception "+e)
        }

        if (authReqNum >= 0){
            logd("ucStatistics  "+"AUTH REQUEST Packet Num = "+ authReqNum)
        }
        if (authRspNum >= 0){
            logd("ucStatistics  "+"AUTH RESPONSE Packet Num = "+ authRspNum)
        }
        if (authFailNum > 0){
            logd("ucStatistics  "+"AUTH FAIL Packet Num = "+ authFailNum)
        }
    }

    /*判断是否是重复点击AUTO RUN*/
    @Synchronized private fun checkAutoRunStarted():Boolean{
        for (element in processRecords){
            if (element.processState == ProcessState.AUTO_RUN_START){
                return true
            }
        }
        return false
    }

    private fun countSeedCardProcess(){
        val indexStart = autoStartIndex
        val sizeList = processRecords.size

        if (indexStart < 0 || sizeList <= 0) return

        try {
            var diff:Long = 0
            val seedEnableIndex = findFirstStateIndex(ProcessState.SEED_ENABLE) //首次种子卡拉起
            val seedConnectIndex = findFirstStateIndex(ProcessState.SEED_CONNECTED) //首次种子卡连接
            val loginOkIndex = findFirstStateIndex(ProcessState.LOGIN_OK)  //登录成功
            val seedEnable2Index = findLastStateIndex(ProcessState.SEED_ENABLE) //DDS切换第二次种子卡拉起
            val seedConnect2Index = findLastStateIndex(ProcessState.SEED_CONNECTED) //DDS切换第二次种子卡连接
            val seedEnableTime = getHappenTimeOfIndex(seedEnableIndex)
            val seedConnectTime = getHappenTimeOfIndex(seedConnectIndex)
            val loginOkTime = getHappenTimeOfIndex(loginOkIndex)

            //logd("countSeedCardProcess= "+seedEnableIndex+","+seedConnectIndex+","+loginOkIndex)
            diff = calDiffTime(seedEnableTime,seedConnectTime)
            if ( diff >= 0){
                processLogd(ProcessState.SEED_ENABLE, ProcessState.SEED_CONNECTED,diff/1000)
            }

            diff = calDiffTime(seedConnectTime,loginOkTime)
            if (diff >= 0){
                processLogd(ProcessState.SEED_CONNECTED, ProcessState.LOGIN_OK,diff/1000)
            }

            if (seedEnable2Index != seedEnableIndex && seedConnect2Index != seedConnectIndex){
                val seedEnable2Time = getHappenTimeOfIndex(seedEnable2Index)
                val seedConnect2Time = getHappenTimeOfIndex(seedConnect2Index)
                diff = calDiffTime(seedEnable2Time,seedConnect2Time)
                if (diff >= 0){
                    processLogd(ProcessState.SEED_ENABLE_2, ProcessState.SEED_CONNNECTED_2,diff/1000)
                }
            }
        }catch (e:Exception){
            logd("countSeedCardProcess Exception: "+e.toString())
        }
    }

    private fun getHappenTimeOfIndex(index:Int): Date {
        if (index >=0 && index < processRecords.size){
            return processRecords[index].happenTime
        }else
        {
            return Date(0)
        }
    }

    /*找到最近一次的该状态的位置*/
    @Synchronized private fun findLastStateIndex(state: ProcessState):Int
    {
        val processStateList: ArrayList<ProcessState> = ArrayList()
        if (processRecords.size == 0) return -1;

        processStateList.clear()
        for( element in processRecords)
        {
            processStateList.add(element.processState)
        }

        val index = processStateList.lastIndexOf(state)
        return index
    }

    /*找到记录中的第一个该状态的位置*/
    @Synchronized private fun findFirstStateIndex(state: ProcessState):Int
    {
        val processStateList: ArrayList<ProcessState> = ArrayList()
        if (processRecords.size == 0) return -1;

        processStateList.clear()
        for( element in processRecords)
        {
            processStateList.add(element.processState)
        }

        val index = processStateList.indexOf(state)
        return index
    }

    private fun processLogd(start: ProcessState, end: ProcessState, time:Long){

        logd("ucStatistics  " + start.name + " >> " + end.name + " : " + time + " Seconds")
    }

    private fun calDiffTime(start: Date, end: Date):Long{
        val diff:Long = end.time - start.time
        if ( diff >= 0 ){
            return diff
        }else{
            return -1L
        }
    }
    private fun logd(s: String) {
        JLog.logd("ucProcessRecord: ", s)
    }
}
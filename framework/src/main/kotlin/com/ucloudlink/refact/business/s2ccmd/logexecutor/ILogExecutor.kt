package com.ucloudlink.refact.business.s2ccmd.logexecutor

import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file

/**
 * Created by junsheng.zhang on 2018/3/26.
 */
interface ILogExecutor {

    fun uploadUCLog(param: S2c_upload_log_file)

    fun uploadQXDMLog(param: S2c_upload_log_file)

    fun uploadCommonLog(param: S2c_upload_log_file)

//    fun uploadFoatLog(param: S2c_upload_log_file)
//
//    fun uploadRadioLog(param: S2c_upload_log_file)

    fun deleteUCLog()

    fun deleteQXDMLog()

    fun deleteCommonLog()

    fun deleteRadioLog()
}
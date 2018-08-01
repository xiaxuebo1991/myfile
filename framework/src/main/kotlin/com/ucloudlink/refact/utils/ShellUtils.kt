package com.ucloudlink.refact.utils

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * author: Blankj
 * linux shell 命令的工具类
 * Modified:
 * 2018/03/27: Alexander: Transfer to Kotlin
 */
object ShellUtils {
    /**
     * 返回的命令结果
     */
    class CommandResult(
            /**
             * 结果码
             */
            var result: Int,
            /**
             * 成功信息
             */
            var successMsg: String,
            /**
             * 错误信息
             */
            var errorMsg: String) {

        override fun toString(): String {
            return "CommandResult{result=$result, successMsg='$successMsg', errorMsg='$errorMsg'}"
        }
    }

    /**
     * 检测是否已经Root
     *
     * @return true: 有ROOT权限；false：没有ROOT权限
     */
    fun isRoot(): Boolean {
        val result = execCmd("echo test root", true)
        JLog.logd(result)
        return result.successMsg.isNotEmpty()
    }

    /**
     * 是否是在root下执行命令
     *
     * @param command         命令
     * @param isRoot          是否需要root权限执行
     * @param isNeedResultMsg 是否需要结果消息
     * @return CommandResult
     */
    fun execCmd(command: String, isRoot: Boolean, isNeedResultMsg: Boolean = true): CommandResult {
        return execCmd(arrayOf(command), isRoot, isNeedResultMsg)
    }

    /**
     * 是否是在root下执行命令
     *
     * @param commands 多条命令链表
     * @param isRoot   是否需要root权限执行
     * @return CommandResult
     */
    fun execCmd(commands: List<String>?, isRoot: Boolean, isNeedResultMsg: Boolean = true): CommandResult {
        return execCmd(commands?.toTypedArray(), isRoot, isNeedResultMsg)
    }

    /**
     * 是否是在root下执行命令
     *
     * @param commands        命令数组
     * @param isRoot          是否需要root权限执行
     * @param isNeedResultMsg 是否需要结果消息
     * @return CommandResult
     */
    @JvmOverloads
    fun execCmd(commands: Array<String>?, isRoot: Boolean, isNeedResultMsg: Boolean = true): CommandResult {
        var result = -1
        if (commands == null || commands.isEmpty()) {
            return CommandResult(result, "", "Command is null or empty!")
        }
        commands.forEach { JLog.logd("Command = $it") }
        var process: Process? = null
        var successResult: BufferedReader? = null
        var errorResult: BufferedReader? = null
        var successMsg: StringBuilder? = null
        var errorMsg: StringBuilder? = null
        var os: DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec(if (isRoot) "su" else "sh")
            os = DataOutputStream(process!!.outputStream)
            for (command in commands) {
                if (command == null) continue
                os.write(command.toByteArray())
                os.writeBytes("\n")
                os.flush()
            }
            os.writeBytes("exit\n")
            os.flush()
            result = process.waitFor()
            if (isNeedResultMsg) {
                successMsg = StringBuilder()
                errorMsg = StringBuilder()
                successResult = BufferedReader(InputStreamReader(process.inputStream, "UTF-8"))
                errorResult = BufferedReader(InputStreamReader(process.errorStream, "UTF-8"))
                var s = successResult.readLine()
                while (s != null) {
                    successMsg.append(s)
                    s = successResult.readLine()
                }
                s = errorResult.readLine()
                while (s != null) {
                    errorMsg.append(s)
                    s = errorResult.readLine()
                }
            }
        } catch (e: Exception) {
            JLog.loge("Error: " + e.message)
            e.printStackTrace()
        } finally {
            CloseUtils.closeIO(os, successResult, errorResult)
            if (process != null) {
                process.destroy()
            }
        }
        return CommandResult(
                result,
                if (successMsg.isNullOrEmpty()) "" else successMsg.toString(),
                if (errorMsg.isNullOrEmpty()) "" else errorMsg.toString()
        )
    }

    /**
     * Execute a simple command
     *
     * @param command Command to execute
     */
    fun execCmd(command: String) {
        JLog.logd("Execute: $command start:")
        val r = Runtime.getRuntime()
        val p: Process
        var bri: BufferedReader? = null
        var bre: BufferedReader? = null
        try {
            p = r.exec(command)
            if (p.waitFor() != 0) {
                System.out.println("exit value = " + p.exitValue())
            }
            // Read input stream
            bri = BufferedReader(InputStreamReader(p.inputStream))
            bre = BufferedReader(InputStreamReader(p.errorStream))
            val list = mutableListOf<String>()
            // Add console input stream
            list.add("InputStream List: ")
            var inline = bri.readLine()
            while (inline != null) {
                list.add(inline)
                inline = bri.readLine()
            }
            // Add console error stream
            list.add("ErrorStream List: ")
            inline = bre.readLine()
            while (inline != null) {
                list.add(inline)
                inline = bre.readLine()
            }
            // Finish
            p.destroy()
            if (list.size <= 2) {
                JLog.logd("No command result record.")
            } else {
                JLog.logd("Result list size = ${list.size}.")
                list.forEach { JLog.logd(it) }
            }
            JLog.logd("Execute: $command finished.")
        } catch (e: Exception) {
            JLog.loge("Error: ${e.message}")
            throw e
        } finally {
            try {
                bri?.close()
            } catch (e: Exception) {
            }
            try {
                bre?.close()
            } catch (e: Exception) {
            }
        }
    }
}
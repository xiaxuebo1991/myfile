package com.ucloudlink.refact.access

import android.content.Context
import android.os.Message

import com.android.internal.util.State
import com.android.internal.util.StateMachine
import com.ucloudlink.framework.IState
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2016/12/15.
 */
class SeedState(private val context: Context, private val accessState: AccessState) : StateMachine("SeedState") {
    var percent = 0
        private set

    private val mDefaultState: DefaultState by lazy { DefaultState() }
    private val mInitState: InitState by lazy { InitState() }
    private val mStartCardState: StartCardState by lazy { StartCardState() }
    private val mInsertState: InsertState by lazy { InsertState() }
    private val mReadyState: ReadyState by lazy { ReadyState() }
    private val mInserviceState: InserviceState by lazy { InserviceState() }
    private val mDataConnectState: DataConnectState by lazy { DataConnectState() }
    private val mDataOkState: DataOkState by lazy { DataOkState() }
    private val mSocketingState: SocketingState by lazy { SocketingState() }
    private val mSocketConnectState: SocketConnectState by lazy { SocketConnectState() }

    private var curState: State? = null
    private var lastState: State? = null
    private var nextState: State? = null

    init {
        addState(mDefaultState)
        addState(mInitState)
        addState(mStartCardState, mInitState)
        addState(mInsertState, mInitState)
        addState(mReadyState, mInitState)
        addState(mInserviceState, mInitState)
        addState(mDataConnectState, mInitState)
        addState(mDataOkState, mDataConnectState)
        addState(mSocketingState, mDataOkState)
        addState(mSocketConnectState, mDataOkState)

        setInitialState(mDefaultState)
        start()
        isDbg = false
    }

    private fun updatePercent(percent: Int) {
        this.percent = percent
        accessState.acessListenUpdateSeedProcess(percent)
    }

    private fun transToNextState(next: State) {
        JLog.logd("state trans: ${curState!!.name} -> ${next.name}")
        nextState = next
        transitionTo(next)
    }

    private fun getErrStrByCode(errcode: Int): String {
        return when (errcode) {
            AccessEventId.EVENT_SEEDSIM_ADD_TIMEOUT -> "EVENT_SEEDSIM_ADD_TIMEOUT"
            AccessEventId.EVENT_SEEDSIM_INSERT_TIMEOUT -> "EVENT_SEEDSIM_INSERT_TIMEOUT"
            AccessEventId.EVENT_SEEDSIM_READY_TIMEOUT -> "EVENT_SEEDSIM_READY_TIMEOUT"
            AccessEventId.EVENT_SEEDSIM_INSERVICE_TIMEOUT -> "EVENT_SEEDSIM_INSERVICE_TIMEOUT"
            AccessEventId.EVENT_SEEDSIM_CONNECT_TIMEOUT -> "EVENT_SEEDSIM_CONNECT_TIMEOUT"
            else -> "Unknown errcode $errcode"
        }
    }

    private fun updateErrMsg(errcode: Int, str: String? = null) {
        var str = str
        if (str == null) {
            str = getErrStrByCode(errcode)
        }
        accessState.accessListenUpdateSeedError(errcode, str)
    }

    private inner class DefaultState : State() {
        override fun enter() {
            curState = this
            updatePercent(0)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_ENABLE -> transToNextState(mStartCardState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }


    private inner class InitState : State() {
        override fun enter() {
            super.enter()
        }

        override fun exit() {
            super.exit()
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_ADD_TIMEOUT, AccessEventId.EVENT_SEEDSIM_INSERT_TIMEOUT,
                AccessEventId.EVENT_SEEDSIM_READY_TIMEOUT, AccessEventId.EVENT_SEEDSIM_INSERVICE_TIMEOUT,
                AccessEventId.EVENT_SEEDSIM_CONNECT_TIMEOUT -> updateErrMsg(msg.what)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }
    }

    private inner class StartCardState : State() {
        override fun enter() {
            curState = this
            updatePercent(10)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_INSERT -> transToNextState(mInsertState)
                AccessEventId.EVENT_SEEDSIM_READY -> transToNextState(mReadyState)
                AccessEventId.EVENT_SEEDSIM_IN_SERVICE -> transToNextState(mInserviceState)
                AccessEventId.EVENT_SEEDSIM_DATA_CONNECT -> transToNextState(mDataConnectState)
                AccessEventId.EVENT_NET_SOCKET_CONNECTED -> transToNextState(mSocketConnectState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    private inner class InsertState : State() {
        override fun enter() {
            curState = this
            updatePercent(20)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_READY -> transToNextState(mReadyState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    private inner class ReadyState : State() {
        override fun enter() {
            curState = this
            updatePercent(30)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_IN_SERVICE -> transToNextState(mInserviceState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    private inner class InserviceState : State() {
        override fun enter() {
            curState = this
            updatePercent(60)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_DATA_CONNECT -> transToNextState(mDataConnectState)
                AccessEventId.EVENT_SEEDSIM_START_PS_CALL -> updatePercent(70)
                AccessEventId.EVENT_SEEDSIM_PS_CALL_SUCC -> updatePercent(75)
                AccessEventId.EVENT_SEEDSIM_OUT_OF_SERVICE -> transToNextState(mReadyState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    private inner class DataConnectState : State() {
        override fun enter() {
            curState = this
            updatePercent(80)
            if (nextState !== mSocketConnectState) {
                transToNextState(mSocketingState)
            }
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_SEEDSIM_OUT_OF_SERVICE -> transToNextState(mReadyState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    private inner class DataOkState : State() {
        override fun enter() {
            curState = this
            JLog.logd("enter DataOkState")
        }

        override fun exit() {
            JLog.logd("exit DataOkState")
            lastState = this
        }

        override fun processMessage(msg: Message): Boolean {
            return super.processMessage(msg)
        }
    }

    private inner class SocketingState : State() {
        override fun enter() {
            curState = this
            updatePercent(90)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_NET_SOCKET_CONNECTED -> transToNextState(mSocketConnectState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                AccessEventId.EVENT_SEEDSIM_DATA_DISCONNECT -> transToNextState(mDataConnectState)

                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    private inner class SocketConnectState : State() {
        override fun enter() {
            curState = this
            updatePercent(100)
        }

        override fun processMessage(msg: Message): Boolean {
            when (msg.what) {
                AccessEventId.EVENT_NET_SOCKET_DISCONNECT -> transToNextState(mSocketingState)
                AccessEventId.EVENT_SEEDSIM_DISABLE -> transToNextState(mDefaultState)
                else -> return IState.NOT_HANDLED
            }
            return IState.HANDLED
        }

        override fun exit() {
            lastState = this
        }
    }

    public override fun logd(s: String) {
        JLog.logd(s)
    }
}

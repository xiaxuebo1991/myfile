package com.ucloudlink.ucapp

import android.app.Activity
import android.os.Bundle
import android.view.View

class MainActivity : Activity() {

    var NotifyMode:Int=-1
    set(value) = NoticeCtrl.getInstance().setMode(value)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun off(view: View) {
        NotifyMode=NoticeCtrl.OFF
    }

    fun on(view: View) {
        NotifyMode=NoticeCtrl.ON
    }

    fun run(view: View) {
        NotifyMode=NoticeCtrl.RUNNING
    }
}

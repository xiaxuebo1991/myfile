package com.ucloudlink.framework.remoteuim

/**
 * Created by jianguo.he on 2017/7/11.
 */
object LibUc {

    //external fun loadUcClasses(context: Context)
    external fun useLibDyn(libPath:String, patchData:ByteArray): Int
}
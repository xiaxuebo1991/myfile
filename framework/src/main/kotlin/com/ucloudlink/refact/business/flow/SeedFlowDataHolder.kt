package com.ucloudlink.refact.business.flow

/**
 * Created by jianguo.he on 2018/1/19.
 */
class SeedFlowDataHolder {

    var seedUpAdd:Long = 0
    var seedDownAdd:Long = 0
    var seedFlow: Long = 0
    var seedFlow2: Long = 0
    var seedPreUp:Long = 0
    var seedPreDown:Long = 0
    var sysPreUp :Long = 0
    var sysPreDown :Long = 0

    fun initSeedData(){
        seedFlow    = 0
        seedPreUp   = 0
        seedPreDown = 0
        seedUpAdd   = 0
        seedDownAdd = 0
        sysPreUp    = 0
        sysPreDown  = 0
        seedFlow2   = 0
    }

    fun updateData(stats: StatsData?) :Int{
        var ret = 0

        if (stats != null) {
            seedUpAdd     = stats.seedUp
            seedDownAdd   = stats.seedDown
        }else{
            ret = -1

            seedUpAdd     = 0
            seedDownAdd   = 0
        }
        return ret
    }

}
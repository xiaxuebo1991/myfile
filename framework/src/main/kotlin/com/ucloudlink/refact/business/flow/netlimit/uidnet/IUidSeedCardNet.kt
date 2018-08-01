package com.ucloudlink.refact.business.flow.netlimit.uidnet

/**
 * Created by jianguo.he on 2018/1/9.
 * 旧版种子卡网络限制，通过uid限制
 */
interface IUidSeedCardNet {

    /**
     * 限制不能上网
     * block apps of uid > 10000
     */
    fun setAllUserRestrictAppsOnData(): Int

    /**
     * 添加uid限制上网，即：限速uid上网
     */
    fun addRestrictAppOnData(uid: Int): Int

    /**
     * 移除限制上网，即：不限制上网
     */
    fun removeRestrictAppOnData(uid: Int): Int

    /**
     *  1, 放通， 0 关闭，会删除掉规则，为0时，需要配合设置10000~30000不能上网达到限制访问网络
     */
    fun setAllUserRestrictAppsOnDataByPass(enable: Int, uids: IntArray): Int

    /**
     * 恢复限制上网，即不可上网, 包括限制热点，热点限制在Framework的netd中
     * undo block apps of uid > 10000，即用户新安装的app，addRestritAppOnData(uid)
     */
    fun resetAllUserRestrictAppsOnData(): Int

}
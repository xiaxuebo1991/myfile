package com.ucloudlink.refact.product.mifi.connect.struct

import com.ucloudlink.framework.protocol.protobuf.mifi.uc_glome_account

/**
 * Created by zhifeng.gao on 2018/3/27.
 */
val PERSONAL = 0                  //个人
val AGENT_TO_PERSONAL = 1          //代理转个人
val AGENT = 2                      //代理购买套餐
val RENT = 3                       //租赁


data class UserAccountInfo(var amount:Double, var rate:Double, var country_name:String, var package_num:Int, var packge:List<uc_glome_account>, var is_show_3g:Int, var user_type:Int,
                                                                                                                                                                     var accumulated_flow:Double, var reserved:String, var display_flag:String, var cssType:String, var unit:String)
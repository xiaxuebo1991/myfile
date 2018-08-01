package com.ucloudlink.refact.systemapi.interfaces

import android.content.Context
import com.ucloudlink.refact.product.ProductForm

/**
 * Created by shiqianhua on 2018/1/13.
 */

enum class ProductTypeEnum{
    PHONE,  // 手机
    MIFI,  // MIFI
    MODULE, // 模块
}

/**
 * 不同产品形态，需要实现接口ProductForm， 然后在SysteApiBase中，跟进不通的产品形态实现对应的类
 */
interface Product {
    fun getProductObj(context:Context): ProductForm
}
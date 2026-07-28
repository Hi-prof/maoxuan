package com.xuhuangbin.xinghuozhaidu

import android.content.Context
import com.xuhuangbin.xinghuozhaidu.data.AppRepository
import com.xuhuangbin.xinghuozhaidu.data.local.XinghuoDatabase

class AppContainer(context: Context) {
    private val database = XinghuoDatabase.create(context)
    val repository = AppRepository(context.applicationContext, database)
}

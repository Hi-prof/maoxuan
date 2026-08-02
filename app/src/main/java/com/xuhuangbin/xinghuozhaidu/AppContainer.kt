package com.xuhuangbin.xinghuozhaidu

import android.content.Context
import com.xuhuangbin.xinghuozhaidu.data.AppRepository
import com.xuhuangbin.xinghuozhaidu.data.local.XinghuoDatabase
import com.xuhuangbin.xinghuozhaidu.data.update.AppUpdateManager

class AppContainer(context: Context) {
    private val database = XinghuoDatabase.create(context)
    val repository = AppRepository(context.applicationContext, database)
    val appUpdateManager = AppUpdateManager(context.applicationContext)
}

package com.xuhuangbin.xinghuozhaidu

import android.app.Application

class XinghuoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

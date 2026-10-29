package com.yara.ai

import android.app.Application

class YaraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Brain.init(this)
    }
}

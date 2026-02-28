package org.localsend.localsend_app

import android.app.Application

class LocalSendApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: LocalSendApp
            private set
    }
}

package app.scotty

import android.app.Application

class ScottyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ScottyApp
            private set
    }
}

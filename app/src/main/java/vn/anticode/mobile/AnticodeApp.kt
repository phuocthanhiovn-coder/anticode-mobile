package vn.anticode.mobile

import android.app.Application

class AnticodeApp : Application() {
    companion object {
        lateinit var instance: AnticodeApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

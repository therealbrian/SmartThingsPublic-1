package com.plexbooks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PlexBooksApp : Application() {
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val msg = e.stackTraceToString()
                base?.getSharedPreferences("crash", 0)
                    ?.edit()?.putString("last", msg)?.commit()
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }
}

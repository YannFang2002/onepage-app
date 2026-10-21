package com.onepage.companion

import android.app.Application
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OnePageApp : Application() {

    /** One shared client: keeps the connection pool warm between screens. */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    companion object {
        lateinit var instance: OnePageApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

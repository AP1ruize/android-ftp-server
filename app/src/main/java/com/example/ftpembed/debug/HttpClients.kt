package com.example.ftpembed.debug

import okhttp3.OkHttpClient

object HttpClients {
    fun create(tag: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(EventLogInterceptor(tag))
            .build()
}

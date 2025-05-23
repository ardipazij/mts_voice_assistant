package com.mtc.mtcai.data.api

import android.util.Log
import com.mtc.mtcai.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

private const val API_KEY = BuildConfig.API_KEY
class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()

        Log.d("API METHOD", request.method)
        Log.d("API URL", request.url.toString())

        val newRequest = request.newBuilder()
            .addHeader("api-key", API_KEY)
            .build()

        return chain.proceed(newRequest)
    }
}
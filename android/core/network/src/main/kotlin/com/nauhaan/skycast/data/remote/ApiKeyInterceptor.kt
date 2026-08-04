package com.nauhaan.skycast.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Appends `appid=<key>` to every outgoing request.
 *
 * Centralising this means the key appears in exactly one place in the codebase and
 * no `@Query("appid")` parameter can ever be forgotten at a call site.
 *
 * When the key is blank (see [com.nauhaan.skycast.core.common.AppConfiguration]) the
 * request goes out without it and OpenWeather returns 401, which `ErrorMapper`
 * translates to [com.nauhaan.skycast.core.common.AppError.Unauthorized], surfaced
 * to the user as setup instructions rather than a generic failure.
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        if (apiKey.isBlank()) return chain.proceed(original)

        val url =
            original.url
                .newBuilder()
                .addQueryParameter(QUERY_APP_ID, apiKey)
                .build()

        return chain.proceed(original.newBuilder().url(url).build())
    }

    private companion object {
        const val QUERY_APP_ID = "appid"
    }
}

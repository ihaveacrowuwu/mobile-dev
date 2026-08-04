package com.nauhaan.skycast.data.remote

import com.nauhaan.skycast.core.common.AppError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/**
 * Translates every framework-level failure into an [AppError].
 *
 * This is the single choke point where networking types stop and domain types
 * begin. Nothing above the data layer imports `HttpException`, `IOException` or
 * `SerializationException`, which is what lets view models be tested on the JVM
 * with no Android or OkHttp on the classpath.
 */
object ErrorMapper {
    /**
     * @throws CancellationException rethrown untouched, structured concurrency
     * requires that cancellation propagate rather than be reported as a failure.
     */
    fun map(throwable: Throwable): AppError = when (throwable) {
        is CancellationException -> throw throwable
        is AppError -> throwable
        is UnknownHostException -> AppError.Offline
        is SocketTimeoutException -> AppError.Timeout
        is HttpException -> mapHttp(throwable.code())
        is SerializationException -> AppError.Decoding(throwable.message ?: "Malformed response")
        // Must come after the specific IOException subtypes above.
        is IOException -> AppError.Offline
        else -> AppError.Unknown(throwable)
    }

    private fun mapHttp(code: Int): AppError = when (code) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> AppError.Unauthorized
        // OpenWeather returns 403 for a key that exists but lacks plan access.
        HttpURLConnection.HTTP_FORBIDDEN -> AppError.Unauthorized
        HttpURLConnection.HTTP_NOT_FOUND -> AppError.NotFound
        HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited
        in SERVER_ERROR_RANGE -> AppError.Server(code)
        else -> AppError.Server(code)
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private val SERVER_ERROR_RANGE = 500..599
}

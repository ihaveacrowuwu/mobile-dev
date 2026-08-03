package com.nauhaan.skycast.ui.common

import androidx.annotation.StringRes
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.common.AppError

/**
 * Turns an [AppError] into something a user can read and act on.
 *
 * Lives in `ui`, not `domain`, because message wording is a presentation concern, and
 * because `domain` must stay free of Android's `R` class. Keeping this mapping in one
 * place is what stops error copy drifting between screens.
 */
data class ErrorPresentation(@StringRes val titleRes: Int, @StringRes val messageRes: Int, val isRetryable: Boolean)

fun AppError.toPresentation(): ErrorPresentation = when (this) {
    AppError.Offline ->
        ErrorPresentation(
            titleRes = R.string.error_offline_title,
            messageRes = R.string.error_offline_message,
            isRetryable = true,
        )

    AppError.Timeout ->
        ErrorPresentation(
            titleRes = R.string.error_timeout_title,
            messageRes = R.string.error_timeout_message,
            isRetryable = true,
        )

    AppError.NotFound ->
        ErrorPresentation(
            titleRes = R.string.error_not_found_title,
            messageRes = R.string.error_not_found_message,
            isRetryable = false,
        )

    AppError.RateLimited ->
        ErrorPresentation(
            titleRes = R.string.error_rate_limited_title,
            messageRes = R.string.error_rate_limited_message,
            isRetryable = true,
        )

    // Not retryable: a Retry button here could never succeed, so the message tells the user how to
    // fix it instead.
    AppError.Unauthorized ->
        ErrorPresentation(
            titleRes = R.string.error_unauthorized_title,
            messageRes = R.string.error_unauthorized_message,
            isRetryable = false,
        )

    is AppError.Server ->
        ErrorPresentation(
            titleRes = R.string.error_server_title,
            messageRes = R.string.error_server_message,
            isRetryable = true,
        )

    is AppError.Decoding ->
        ErrorPresentation(
            titleRes = R.string.error_decoding_title,
            messageRes = R.string.error_decoding_message,
            isRetryable = false,
        )

    is AppError.Storage ->
        ErrorPresentation(
            titleRes = R.string.error_storage_title,
            messageRes = R.string.error_storage_message,
            isRetryable = false,
        )

    is AppError.Unknown ->
        ErrorPresentation(
            titleRes = R.string.error_unknown_title,
            messageRes = R.string.error_unknown_message,
            isRetryable = true,
        )
}

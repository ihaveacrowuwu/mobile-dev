package com.nauhaan.skycast.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error classification.
 *
 * `isRetryable` drives whether the UI offers a Retry button, so getting it wrong means
 * either hiding a working recovery path or showing a button that can never succeed.
 */
class AppErrorTest {
    @Test
    fun `transient failures are retryable`() {
        assertTrue(AppError.Offline.isRetryable)
        assertTrue(AppError.Timeout.isRetryable)
        assertTrue(AppError.RateLimited.isRetryable)
        assertTrue(AppError.Server(503).isRetryable)
        assertTrue(AppError.Unknown(RuntimeException()).isRetryable)
    }

    @Test
    fun `a missing API key is not retryable`() {
        // Retrying a bad key can never succeed; the UI shows setup instructions
        // instead of a button that always fails.
        assertFalse(AppError.Unauthorized.isRetryable)
    }

    @Test
    fun `deterministic failures are not retryable`() {
        assertFalse(AppError.NotFound.isRetryable)
        assertFalse(AppError.Decoding("bad shape").isRetryable)
        assertFalse(AppError.Storage("disk full").isRetryable)
    }

    @Test
    fun `connectivity classification separates user network problems from ours`() {
        assertTrue(AppError.Offline.isConnectivityRelated)
        assertTrue(AppError.Timeout.isConnectivityRelated)
        assertFalse(AppError.Server(500).isConnectivityRelated)
        assertFalse(AppError.Unauthorized.isConnectivityRelated)
    }
}

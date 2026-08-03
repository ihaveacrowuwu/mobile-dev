package com.nauhaan.skycast.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable coroutine dispatchers.
 *
 * Production code never references [Dispatchers] directly (detekt's `InjectDispatcher`
 * rule enforces this). Tests substitute a `StandardTestDispatcher` so coroutines run
 * deterministically instead of racing a real thread pool.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

/** The real dispatchers, bound in `di.CoroutinesModule`. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}

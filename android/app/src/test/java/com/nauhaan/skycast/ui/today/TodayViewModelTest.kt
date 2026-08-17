package com.nauhaan.skycast.ui.today

import app.cash.turbine.test
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.usecase.ObserveTodayWeatherUseCase
import com.nauhaan.skycast.testing.FakeLocationRepository
import com.nauhaan.skycast.testing.FakeSettingsRepository
import com.nauhaan.skycast.testing.FakeWeatherRepository
import com.nauhaan.skycast.testing.MainDispatcherRule
import com.nauhaan.skycast.testing.sampleLocation
import com.nauhaan.skycast.testing.sampleWeather
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [TodayViewModel] behaviour, with an emphasis on the **offline and error paths**.
 *
 * The happy path is the easy half. The cases that matter most
 * are the last two tests: a failed refresh
 * must never blank the screen, and stale cached data must still render.
 *
 * `advanceUntilIdle()` is still marked experimental in kotlinx-coroutines-test, but it is
 * the documented way to drain a `StandardTestDispatcher`, hence the opt-in below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var weatherRepository: FakeWeatherRepository
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        weatherRepository = FakeWeatherRepository()
        locationRepository = FakeLocationRepository()
        settingsRepository = FakeSettingsRepository()
        viewModel = TodayViewModel(
            observeTodayWeather = ObserveTodayWeatherUseCase(
                locationRepository = locationRepository,
                weatherRepository = weatherRepository,
                settingsRepository = settingsRepository,
            ),
            weatherRepository = weatherRepository,
        )
    }

    @Test
    fun `with no saved location the empty state shows, not an error`() = runTest {
        locationRepository.primaryLocation.value = null

        viewModel.uiState.test {
            // Skip the synthetic initial value emitted by stateIn.
            skipItems(1)
            val state = awaitItem()

            assertTrue(state.hasNoLocation)
            assertTrue(state.showsEmptyState)
            assertFalse(state.showsFullScreenError)
            assertNull(state.error)
        }
    }

    @Test
    fun `cached weather renders without a blocking loader`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        weatherRepository.currentWeather.value = DataState.success(sampleWeather())

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()

            assertTrue(state.showsContent)
            // Offline-first: a warm start shows no spinner.
            assertFalse(state.showsFullScreenLoader)
            assertEquals(22.0, state.weather?.temperatureCelsius ?: 0.0, 0.001)
        }
    }

    @Test
    fun `a failed refresh keeps the cached data and shows a banner instead of an error screen`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        weatherRepository.currentWeather.value = DataState.failure(
            error = AppError.Offline,
            cached = sampleWeather(),
            stale = true,
        )

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()

            // Data survives a network failure.
            assertTrue(state.showsContent)
            assertTrue(state.showsStaleBanner)
            assertFalse(state.showsFullScreenError)
            assertEquals(AppError.Offline, state.error)
        }
    }

    @Test
    fun `an error with no cache shows the full-screen error state`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        weatherRepository.currentWeather.value = DataState.failure(AppError.Offline)

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()

            assertFalse(state.showsContent)
            assertTrue(state.showsFullScreenError)
        }
    }

    @Test
    fun `dismissing the banner hides it without discarding the data`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        weatherRepository.currentWeather.value = DataState.failure(
            error = AppError.Offline,
            cached = sampleWeather(),
            stale = true,
        )

        viewModel.uiState.test {
            skipItems(1)
            assertTrue(awaitItem().showsStaleBanner)

            viewModel.dismissBanner()

            val dismissed = awaitItem()
            assertFalse(dismissed.showsStaleBanner)
            assertTrue(dismissed.showsContent)
        }
    }

    @Test
    fun `refresh delegates to the repository and re-arms the banner`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        weatherRepository.currentWeather.value = DataState.success(sampleWeather())

        // stateIn uses WhileSubscribed, so the flow is cold until something collects.
        // Turbine is not used here: refresh() launches into viewModelScope, and reading
        // uiState.value after advanceUntilIdle() is clearer than guessing how many
        // intermediate emissions the conflation will produce.
        val collectJob = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.dismissBanner()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBannerDismissed)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, weatherRepository.refreshCallCount)
        // A fresh attempt makes a dismissed banner relevant again, so the user is told if this
        // attempt failed too.
        assertFalse(viewModel.uiState.value.isBannerDismissed)

        collectJob.cancel()
    }

    @Test
    fun `a failed manual refresh surfaces the error so the banner appears`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        // A *successful* cached read: the stream carries no error and nothing is stale, so the
        // only thing that can put a banner on screen is the failed refresh itself.
        weatherRepository.currentWeather.value = DataState.success(sampleWeather())
        weatherRepository.refreshError = AppError.Offline

        val collectJob = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        // Nothing to complain about yet.
        assertFalse(viewModel.uiState.value.showsStaleBanner)

        viewModel.refresh()
        advanceUntilIdle()

        // The regression this guards: `refresh()` returns the error and the view model must
        // surface it, so pulling to refresh while offline is visible rather than silent.
        assertEquals(AppError.Offline, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.showsStaleBanner)
        // And the data is still there, which is the other half of the promise.
        assertTrue(viewModel.uiState.value.showsContent)

        collectJob.cancel()
    }

    @Test
    fun `a later successful refresh clears the previous failure`() = runTest {
        locationRepository.primaryLocation.value = sampleLocation()
        weatherRepository.currentWeather.value = DataState.success(sampleWeather())
        weatherRepository.refreshError = AppError.Offline

        val collectJob = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showsStaleBanner)

        // Back online.
        weatherRepository.refreshError = null
        viewModel.refresh()
        advanceUntilIdle()

        // A stale error outliving its cause is its own bug: the banner would then claim the
        // app is offline while it is happily fetching.
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.showsStaleBanner)

        collectJob.cancel()
    }

    @Test
    fun `refresh is a no-op when there is no location to refresh`() = runTest {
        locationRepository.primaryLocation.value = null

        val collectJob = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, weatherRepository.refreshCallCount)

        collectJob.cancel()
    }
}

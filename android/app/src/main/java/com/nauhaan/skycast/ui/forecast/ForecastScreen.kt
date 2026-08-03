package com.nauhaan.skycast.ui.forecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R
import com.nauhaan.skycast.ui.common.PlaceholderScreen
import com.nauhaan.skycast.ui.common.PreviewEpochDay
import com.nauhaan.skycast.ui.common.PreviewLocationId

/**
 * The Forecast tab, five days, tappable through to a day detail.
 *
 * The list itself is the next feature to build on top of
 * `WeatherRepository.observeForecast`, following the same stateful/stateless split as
 * [com.nauhaan.skycast.ui.today.TodayScreen]. The button below exercises the real
 * push route in the meantime so the navigation hierarchy is testable today.
 */
@Composable
fun ForecastScreen(onNavigateToDayDetail: (locationId: Long, epochDay: Long) -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.tab_forecast),
        plannedContent = stringResource(R.string.placeholder_forecast),
        actionLabel = stringResource(R.string.placeholder_action_open_day),
        onAction = { onNavigateToDayDetail(PreviewLocationId, PreviewEpochDay) },
        modifier = modifier,
    )
}

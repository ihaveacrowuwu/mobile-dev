package com.nauhaan.skycast.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.WeatherConditionBadge
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.model.WeatherCondition
import kotlin.math.roundToInt

/**
 * The hero reading: place, condition, and one very large temperature.
 *
 * Shared by the Home tab and the pushed location-detail screen. Extracted rather than duplicated
 * so the two cannot drift, a detail screen showing a differently-rounded temperature from the tab
 * that pushed it would read as a bug.
 *
 * `clearAndSetSemantics` merges the whole block into a single TalkBack announcement. Without it a
 * screen-reader user hears "London" … "22" … "degrees" … "feels like" as four disconnected
 * fragments. `onClickLabel` then describes the tap action, so the merge does not hide the fact
 * that the block is interactive.
 *
 * @param showsLocationName `false` where the surrounding screen already names the place.
 * @param onClick `null` makes the block inert. Today passes a lambda to push the detail screen;
 *   the detail screen itself has nowhere further to go.
 *
 * The iOS counterpart is `Features/Common/CurrentConditionsHero.swift`.
 */
@Composable
fun CurrentConditionsHeader(
    weather: Weather,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
    showsLocationName: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val displayed = unit.convertFromCelsius(weather.temperatureCelsius).roundToInt()
    val displayedFeelsLike = unit.convertFromCelsius(weather.feelsLikeCelsius).roundToInt()
    val announcement = stringResource(
        R.string.home_conditions_accessibility,
        weather.locationName,
        displayed,
        unit.symbol,
        weather.description,
        displayedFeelsLike,
    )
    val openDetailLabel = stringResource(R.string.home_open_detail_action)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick == null) {
                    base.clearAndSetSemantics { contentDescription = announcement }
                } else {
                    base
                        .clickable(onClickLabel = openDetailLabel, onClick = onClick)
                        .clearAndSetSemantics {
                            contentDescription = announcement
                            onClick(label = openDetailLabel) {
                                onClick()
                                true
                            }
                        }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Hidden where the screen already names the place above, since Home has a location switcher
        // and the detail screen an identity block, and "London" twice within a hundred pixels
        // reads as a mistake. The spoken announcement still includes it either way, since a
        // screen-reader user has no such visual context.
        if (showsLocationName) {
            Text(
                text = weather.locationName,
                // Expressive's emphasized title role: this is the screen's anchor label.
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
            )
        }
        WeatherConditionBadge(
            condition = weather.condition,
            isDaytime = weather.isDaytime,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        Row(verticalAlignment = Alignment.Top) {
            Text(text = "$displayed", style = MaterialTheme.typography.displayLargeEmphasized)
            Text(
                text = unit.symbol,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        Text(
            text = weather.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_feels_like, displayedFeelsLike, unit.symbol),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CurrentConditionsHeaderPreview() {
    SkyCastTheme {
        CurrentConditionsHeader(
            weather = previewWeather(),
            unit = TemperatureUnit.CELSIUS,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

/**
 * Sample data for the previews in this package.
 *
 * `internal`, and in the same file as its only real consumer, so it stays out of the domain model
 * and out of any test fixture that a marker might mistake for production data.
 */
internal fun previewWeather(): Weather = Weather(
    locationId = 1,
    locationName = "London",
    condition = WeatherCondition.CLEAR,
    description = "Clear sky",
    iconCode = "01d",
    temperatureCelsius = 22.0,
    feelsLikeCelsius = 21.0,
    minTemperatureCelsius = 18.0,
    maxTemperatureCelsius = 24.0,
    humidityPercent = 69,
    pressureHpa = 1009,
    windSpeedMetresPerSecond = 4.5,
    windDirectionDegrees = 210,
    cloudinessPercent = 5,
    visibilityMetres = 10_000,
    sunrise = java.time.Instant.parse("2026-06-21T04:43:00Z"),
    sunset = java.time.Instant.parse("2026-06-21T20:21:00Z"),
    observedAt = java.time.Instant.parse("2026-06-21T12:00:00Z"),
    cachedAt = java.time.Instant.parse("2026-06-21T12:01:00Z"),
    // BST: London in June, so the preview's sunrise and sunset read as London's own clock.
    zoneOffset = java.time.ZoneOffset.ofHours(1),
)

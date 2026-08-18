package com.nauhaan.skycast.ui.metar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.SkyLayer
import com.nauhaan.skycast.core.designsystem.component.SkyLayersDiagram
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WindCompass
import com.nauhaan.skycast.core.designsystem.component.coverFractionFor
import com.nauhaan.skycast.core.designsystem.component.frostRim
import com.nauhaan.skycast.core.designsystem.component.frostedCardColours
import com.nauhaan.skycast.core.designsystem.component.frostedCardElevation
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherPalette
import com.nauhaan.skycast.domain.model.FlightCategory
import com.nauhaan.skycast.domain.model.MetarReport
import com.nauhaan.skycast.ui.common.SectionHeader
import com.nauhaan.skycast.ui.common.cardinalFor
import com.nauhaan.skycast.ui.common.toPresentation
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The METAR tab: the nearest reporting airport's observation.
 *
 * The raw report is shown, in a monospaced face, above the decoded fields. Anyone who reads METARs
 * reads the line; the decoded rows are for everyone else.
 */
@Composable
fun MetarScreen(
    onNavigateToAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MetarContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onAddLocation = onNavigateToAddLocation,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetarContent(
    uiState: MetarUiState,
    onRefresh: () -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.showsFullScreenLoader ->
            LoadingView(modifier = modifier, message = stringResource(R.string.metar_loading))

        uiState.showsEmptyState ->
            EmptyStateView(
                title = stringResource(R.string.metar_empty_title),
                message = stringResource(R.string.metar_empty_message),
                icon = Icons.Filled.AddLocationAlt,
                actionLabel = stringResource(R.string.action_add_location),
                onAction = onAddLocation,
                modifier = modifier,
            )

        uiState.showsFullScreenError -> {
            val error = requireNotNull(uiState.error)
            // "No airport reporting nearby" is a fact about the place, not a network problem, so
            // it gets its own wording.
            val isNothingNearby = error is com.nauhaan.skycast.core.common.AppError.NotFound
            val presentation = error.toPresentation()
            ErrorView(
                title = stringResource(
                    if (isNothingNearby) R.string.metar_none_title else presentation.titleRes,
                ),
                message = stringResource(
                    if (isNothingNearby) R.string.metar_none_message else presentation.messageRes,
                ),
                icon = if (isNothingNearby) Icons.Filled.FlightTakeoff else Icons.Filled.CloudOff,
                onRetry = onRefresh.takeIf { presentation.isRetryable },
                modifier = modifier,
            )
        }

        else -> {
            val report = uiState.report
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (uiState.showsStaleBanner) {
                        val message = uiState.error
                            ?.let { stringResource(it.toPresentation().messageRes) }
                            ?: stringResource(R.string.banner_data_may_be_out_of_date)
                        StaleDataBanner(message = message, onRetry = onRefresh)
                    }
                    if (report != null) {
                        ReportBody(report = report, modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportBody(report: MetarReport, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StationHeader(report)
        // The category first and large, because it is the one thing a pilot looks for before anything
        // else, it decides whether the flight can be made under visual rules at all.
        FlightCategoryHero(report.flightCategory)
        SkySection(report)
        WindSection(report)
        DerivedSection(report)
        RawReport(report.raw)
        DecodedRows(report)
    }
}

@Composable
private fun StationHeader(report: MetarReport, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = report.stationId,
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        Text(
            text = report.stationName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.metar_station_distance,
                report.distanceKm.roundToInt().toString(),
                report.elevationMetres,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.metar_observed,
                OBSERVED_FORMAT.format(report.observedAt),
                report.age(Instant.now()).describe(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * The flight-rules category, as a coloured badge.
 *
 * The first thing a pilot looks for, so it is the first thing on the screen after the station. The
 * colours are the conventional ones, green for visual, blue for marginal, red for instrument,
 * magenta for low instrument, and they come from the weather palette rather than being invented
 * here, so they are the same contrast-checked colours the rest of the app uses.
 */
@Composable
private fun FlightCategoryHero(category: FlightCategory, modifier: Modifier = Modifier) {
    val colour = category.colour()
    val meaning = stringResource(category.meaningRes)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .frostRim(CardDefaults.shape)
            .clearAndSetSemantics {
                contentDescription = "Flight category ${category.label}. $meaning"
            },
        shape = CardDefaults.shape,
        colors = frostedCardColours(),
        elevation = frostedCardElevation(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // A filled disc rather than a small chip: at this size the colour does the work from across
            // the room, which is the point of the convention, green visual, blue marginal, amber
            // instrument, violet low instrument.
            Box(
                modifier = Modifier
                    .size(CategoryDiscSize)
                    .clip(CircleShape)
                    .background(colour.copy(alpha = CATEGORY_DISC_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The sky, drawn to scale, with the ceiling marked. See [SkyLayersDiagram].
 */
@Composable
private fun SkySection(report: MetarReport, modifier: Modifier = Modifier) {
    val ceiling = report.ceilingFeet
    val layers = report.clouds.mapNotNull { layer ->
        layer.baseFeet?.let { base ->
            SkyLayer(
                cover = layer.cover,
                baseFeet = base,
                coverFraction = coverFractionFor(layer.cover),
                isCeiling = base == ceiling,
            )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.metar_section_sky))
        if (layers.isEmpty()) {
            // A clear sky is a real observation, not missing data, so it is stated explicitly.
            Text(
                text = stringResource(R.string.metar_sky_clear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val shape = CardDefaults.shape
            Card(
                modifier = Modifier.fillMaxWidth().frostRim(shape),
                shape = shape,
                colors = frostedCardColours(),
                elevation = frostedCardElevation(),
            ) {
                SkyLayersDiagram(
                    layers = layers,
                    contentDescription = if (ceiling == null) {
                        stringResource(R.string.metar_sky_no_ceiling)
                    } else {
                        pluralStringResource(R.plurals.metar_sky_ceiling, ceiling, ceiling)
                    },
                )
            }
        }
    }
}

/** Wind as a compass, because a bearing is a direction and not a magnitude. */
@Composable
private fun WindSection(report: MetarReport, modifier: Modifier = Modifier) {
    val knots = report.windSpeedKnots ?: 0
    val bearing = report.windDirectionDegrees
    val shape = CardDefaults.shape

    val description = report.windDescriptionPlain()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.metar_section_wind))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .frostRim(shape)
                .clearAndSetSemantics { contentDescription = description },
            shape = shape,
            colors = frostedCardColours(),
            elevation = frostedCardElevation(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Calm and variable winds have no bearing to point at, so the needle is parked
                // north and the text carries the meaning.
                WindCompass(
                    degrees = (bearing ?: 0).toFloat(),
                    colour = weatherPalette.wind,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = stringResource(R.string.metar_wind_knots, knots),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The figures derived from a report rather than read off it.
 *
 * A METAR reports none of these. See `MetarReport` for how each is derived, and
 * `MetarDerivationsTest` for the references they are checked against.
 */
@Composable
private fun DerivedSection(report: MetarReport, modifier: Modifier = Modifier) {
    val details = buildList {
        report.relativeHumidityPercent?.let {
            add(stringResource(R.string.metar_humidity) to stringResource(R.string.metar_percent, it))
        }
        report.dewPointSpreadCelsius?.let {
            add(
                stringResource(R.string.metar_spread) to
                    stringResource(R.string.metar_spread_value, it, fogRiskLabel(it)),
            )
        }
        report.densityAltitudeFeet?.let {
            add(
                stringResource(R.string.metar_density_altitude) to
                    pluralStringResource(R.plurals.metar_feet, it, it),
            )
        }
    }
    if (details.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.metar_section_derived))
        val shape = CardDefaults.shape
        Card(
            modifier = Modifier.fillMaxWidth().frostRim(shape),
            shape = shape,
            colors = frostedCardColours(),
            elevation = frostedCardElevation(),
        ) {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                details.forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics { contentDescription = "$label, $value" },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** How close the air is to saturating, in words. */
@Composable
private fun fogRiskLabel(spreadCelsius: Double): String = stringResource(
    when {
        spreadCelsius <= 1.0 -> R.string.metar_fog_likely
        spreadCelsius <= 3.0 -> R.string.metar_fog_possible
        else -> R.string.metar_fog_unlikely
    },
)

/** The conventional colour for each category, taken from the app's palette. */
@Composable
private fun FlightCategory.colour(): Color = when (this) {
    FlightCategory.VFR -> weatherPalette.wind
    FlightCategory.MVFR -> weatherPalette.humidity
    FlightCategory.IFR -> weatherPalette.sunset
    FlightCategory.LIFR -> weatherPalette.pressure
    FlightCategory.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** What the abbreviation actually means, for everyone who is not a pilot. */
private val FlightCategory.meaningRes: Int
    get() = when (this) {
        FlightCategory.VFR -> R.string.metar_vfr_meaning
        FlightCategory.MVFR -> R.string.metar_mvfr_meaning
        FlightCategory.IFR -> R.string.metar_ifr_meaning
        FlightCategory.LIFR -> R.string.metar_lifr_meaning
        FlightCategory.UNKNOWN -> R.string.metar_category_unknown_meaning
    }

@Composable
private fun MetarReport.windDescriptionPlain(): String {
    val bearing = windDirectionDegrees
    return when {
        (windSpeedKnots ?: 0) == 0 -> stringResource(R.string.metar_wind_calm)
        bearing == null -> stringResource(R.string.metar_wind_variable_direction)
        else -> stringResource(R.string.metar_wind_from, bearing, cardinalFor(bearing))
    }
}

@Composable
private fun RawReport(raw: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.metar_raw_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = raw,
                // Monospaced: a METAR is a fixed-format line, and the groups stay aligned.
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun DecodedRows(report: MetarReport, modifier: Modifier = Modifier) {
    val rows = buildList {
        add(stringResource(R.string.metar_wind) to report.windDescription())
        add(stringResource(R.string.metar_visibility) to report.visibilityDescription())
        report.temperatureCelsius?.let { add(stringResource(R.string.metar_temperature) to "${it.roundToInt()}°C") }
        report.dewPointCelsius?.let { add(stringResource(R.string.metar_dew_point) to "${it.roundToInt()}°C") }
        report.altimeterHectopascals?.let {
            add(
                stringResource(R.string.metar_altimeter) to stringResource(
                    R.string.metar_altimeter_value,
                    it.roundToInt().toString(),
                    // The same pressure in the unit the other half of the world's charts use.
                    INCHES_FORMAT.format(it / HECTOPASCALS_PER_INCH),
                ),
            )
        }
        add(stringResource(R.string.metar_clouds) to report.cloudDescription())
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            Text(
                text = stringResource(R.string.metar_decoded_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .clearAndSetSemantics { contentDescription = "$label, $value" },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetarReport.windDescription(): String {
    // Bound to locals first: these are properties of a class in another module, so the compiler
    // cannot smart-cast them to non-null across the branches even after the null checks.
    val knots = windSpeedKnots
    val bearing = windDirectionDegrees
    return when {
        knots == null || knots == 0 -> stringResource(R.string.metar_wind_calm)
        bearing == null -> stringResource(R.string.metar_wind_variable, knots)
        else -> stringResource(R.string.metar_wind_value, "$bearing°", knots)
    }
}

@Composable
private fun MetarReport.visibilityDescription(): String {
    val miles = visibilityStatuteMiles ?: return ""
    val text = if (miles == miles.roundToInt().toDouble()) miles.roundToInt().toString() else miles.toString()
    return if (visibilityIsOrGreater) {
        stringResource(R.string.metar_visibility_or_greater, text)
    } else {
        stringResource(R.string.metar_visibility_value, text)
    }
}

@Composable
private fun MetarReport.cloudDescription(): String {
    if (clouds.isEmpty()) return stringResource(R.string.metar_clouds_clear)
    // Resolved with `map`, then joined. `map` is an inline function, so a @Composable call inside
    // its lambda keeps the composable context; `joinToString` is not inline, so doing it in one
    // pass does not compile. `LocalContext.current.getString` compiles but Android Lint rejects it,
    // because it does not track configuration changes.
    val layers = clouds.map { layer ->
        val base = layer.baseFeet
        if (base == null) {
            layer.cover
        } else {
            stringResource(R.string.metar_cloud_layer, layer.cover, base)
        }
    }
    return layers.joinToString(", ")
}

/** "12 min" or "1h 05m": how old the observation is, which is what a pilot actually checks. */
@Composable
private fun Duration.describe(): String = if (toHours() < 1) {
    stringResource(R.string.metar_minutes_ago, toMinutes().coerceAtLeast(0))
} else {
    stringResource(R.string.metar_hours_ago, toHours(), toMinutesPart())
}

private val OBSERVED_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneId.of("UTC"))

/** METARs are issued in UTC ("Z" in the report), so the observation time is shown in it. */
private const val HECTOPASCALS_PER_INCH = 33.8639
private const val INCHES_FORMAT = "%.2f"

@Preview(showBackground = true)
@Composable
private fun MetarPreview() {
    SkyCastTheme {
        MetarContent(
            uiState = MetarUiState(
                report = MetarReport(
                    stationId = "EGLC",
                    stationName = "London City Arpt, EN, GB",
                    distanceKm = 12.7,
                    latitude = 51.505,
                    longitude = 0.055,
                    elevationMetres = 10,
                    observedAt = Instant.parse("2026-08-18T14:20:00Z"),
                    temperatureCelsius = 26.0,
                    dewPointCelsius = 14.0,
                    windDirectionDegrees = 270,
                    windSpeedKnots = 10,
                    visibilityStatuteMiles = 6.0,
                    visibilityIsOrGreater = true,
                    altimeterHectopascals = 1010.0,
                    clouds = emptyList(),
                    flightCategory = FlightCategory.VFR,
                    raw = "METAR EGLC 181420Z AUTO 27010KT 240V300 9999 NCD 26/14 Q1010",
                    cachedAt = Instant.parse("2026-08-18T14:25:00Z"),
                ),
            ),
            onRefresh = {},
            onAddLocation = {},
        )
    }
}

private val CategoryDiscSize = 64.dp

/** Enough colour to read the category across a room, light enough to keep its label legible on it. */
private const val CATEGORY_DISC_ALPHA = 0.35f

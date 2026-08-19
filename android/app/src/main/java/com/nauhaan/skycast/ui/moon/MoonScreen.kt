package com.nauhaan.skycast.ui.moon

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.AuroraCard
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.LunarCycleRing
import com.nauhaan.skycast.core.designsystem.component.MetricGauge
import com.nauhaan.skycast.core.designsystem.component.MoonDisc
import com.nauhaan.skycast.core.designsystem.component.MoonRiseIcon
import com.nauhaan.skycast.core.designsystem.component.MoonSetIcon
import com.nauhaan.skycast.core.designsystem.component.NightSkyPanel
import com.nauhaan.skycast.core.designsystem.component.SkyPathCard
import com.nauhaan.skycast.core.designsystem.component.SkyPathReading
import com.nauhaan.skycast.core.designsystem.component.frostRim
import com.nauhaan.skycast.core.designsystem.component.frostedCardColours
import com.nauhaan.skycast.core.designsystem.component.frostedCardElevation
import com.nauhaan.skycast.core.designsystem.component.nightSky
import com.nauhaan.skycast.core.designsystem.theme.NightSkyTheme
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherPalette
import com.nauhaan.skycast.domain.model.MoonCalculator
import com.nauhaan.skycast.domain.model.MoonDistanceBand
import com.nauhaan.skycast.domain.model.MoonPhaseName
import com.nauhaan.skycast.domain.model.MoonSnapshot
import com.nauhaan.skycast.domain.model.PrincipalPhase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * The Moon tab: phase, illumination, rise and set, distance, and what is coming.
 *
 * Every figure here is **computed on the device**, not fetched, so this is the only screen in the
 * app with no loading, error, offline or stale state to handle.
 */
@Composable
fun MoonScreen(modifier: Modifier = Modifier, viewModel: MoonViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MoonContent(uiState = uiState, modifier = modifier)
}

@Composable
internal fun MoonContent(uiState: MoonUiState, modifier: Modifier = Modifier) {
    // The sky is the screen, not a card on it, and everything inside it is themed dark so text and
    // cards are legible against it in either app theme. See NightSkyTheme.
    //
    // The sky itself is painted by `RootScreen`, not here. This composable sits below the status bar,
    // so painting the gradient here left a strip of the shell's daytime background above it, a
    // full-page background that stopped short of the top of the page. Page backgrounds are the shell's
    // job for exactly that reason; it already owns `WeatherBackground` for every other tab.
    NightSkyTheme {
        Box(modifier = modifier.fillMaxSize()) {
            val snapshot = uiState.snapshot
            if (snapshot == null) {
                LoadingView(message = stringResource(R.string.moon_loading))
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                MoonHero(snapshot = snapshot)

                if (uiState.showsRiseAndSet) {
                    SectionHeading(stringResource(R.string.moon_tonight))
                    MoonPathCard(snapshot = snapshot, zone = uiState.zone)
                }

                SectionHeading(stringResource(R.string.moon_distance))
                MoonDistanceCard(snapshot = snapshot)

                // The aurora belongs on this page rather than on Home: it is a night-sky event, and this is
                // the night-sky screen. It is also the one thing here that is fetched, so it appears only once
                // NOAA's reading has arrived, see `MoonViewModel.space`.
                val space = uiState.spaceWeather
                val location = uiState.location
                if (space != null && location != null) {
                    auroraReading(location, space)?.let { reading ->
                        SectionHeading(stringResource(R.string.aurora_section))
                        AuroraCard(reading = reading)
                    }
                }

                SectionHeading(stringResource(R.string.moon_coming_up))
                UpcomingPhasesCard(phases = snapshot.upcomingPhases, zone = uiState.zone)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        // An emphasised role, and one of only two on this screen: the hero's phase name is the other.
        // Material's guidance is at most two per screen, or emphasis stops meaning anything.
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = modifier,
    )
}

/** The Moon, big, on the night sky, inside a ring showing where in the month it is. */
@Composable
private fun MoonHero(snapshot: MoonSnapshot, modifier: Modifier = Modifier) {
    val phaseName = stringResource(snapshot.phase.labelRes)
    val summary = stringResource(
        R.string.moon_summary,
        snapshot.illuminatedPercent,
        ageDescription(snapshot),
    )
    val countdown = fullMoonCountdown(snapshot)
    val description = listOfNotNull("$phaseName. $summary", countdown).joinToString(". ")

    // No panel of its own any more: the sky behind the whole screen is the ground this sits on, and a
    // rounded rectangle of night inside a night was a picture of the sky rather than the sky.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LunarCycleRing(
                cycleFraction = snapshot.cycleFraction.toFloat(),
                diameter = RingDiameter,
            )
            MoonDisc(elongationDegrees = snapshot.elongationDegrees, diameter = DiscDiameter)
        }

        Text(
            text = phaseName,
            style = MaterialTheme.typography.titleLargeEmphasized,
            // From the scheme, not hardcoded: NightSkyTheme has already made this subtree dark, so
            // `onSurface` is the light colour here in either app theme.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (countdown != null) {
            Text(
                text = countdown,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TertiaryAlpha),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Moonrise to moonset, on the same arc the sun card uses. */
@Composable
private fun MoonPathCard(snapshot: MoonSnapshot, zone: ZoneId, modifier: Modifier = Modifier) {
    val riseLabel = snapshot.moonrise.formatTime(zone)
    val setLabel = snapshot.moonset.formatTime(zone)
    val span = snapshot.timeAboveHorizon
    val spanLabel = if (span == null) {
        Placeholder
    } else {
        stringResource(R.string.moon_time_up, span.toHours(), span.toMinutesPart())
    }

    SkyPathCard(
        reading = SkyPathReading(
            // Outside 0…1 whenever the Moon is below the horizon, which hides the marker rather than
            // parking it at an end.
            progress = riseProgress(snapshot),
            riseLabel = riseLabel,
            setLabel = setLabel,
            centreLabel = spanLabel,
            contentDescription = stringResource(
                R.string.moon_path_description,
                riseLabel,
                setLabel,
                spanLabel,
            ),
        ),
        riseIcon = MoonRiseIcon,
        setIcon = MoonSetIcon,
        riseColour = weatherPalette.onMoonContainer,
        setColour = weatherPalette.pressure,
        modifier = modifier,
    )
}

/** How far away the Moon is, and what that means. */
@Composable
private fun MoonDistanceCard(snapshot: MoonSnapshot, modifier: Modifier = Modifier) {
    val distance = stringResource(R.string.moon_distance_km, snapshot.distanceKm.roundToLong())
    val meaning = stringResource(snapshot.distanceBand.labelRes)
    val width = stringResource(R.string.moon_apparent_width, snapshot.angularDiameterDegrees)

    val shape = CardDefaults.shape

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "$distance. $meaning. $width"
            }
            .frostRim(shape),
        shape = shape,
        colors = frostedCardColours(),
        elevation = frostedCardElevation(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // The same gauge the humidity and pressure tiles use, so "where in its range is this?"
            // looks the same wherever the app asks it.
            MetricGauge(
                fraction = snapshot.distanceFraction.toFloat(),
                colour = weatherPalette.pressure,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(text = distance, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = width,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The next four principal phases, each with the Moon drawn as it will look. */
@Composable
private fun UpcomingPhasesCard(phases: List<PrincipalPhase>, zone: ZoneId, modifier: Modifier = Modifier) {
    val shape = CardDefaults.shape

    Card(
        modifier = modifier
            .fillMaxWidth()
            .frostRim(shape),
        shape = shape,
        colors = frostedCardColours(),
        elevation = frostedCardElevation(),
    ) {
        phases.forEachIndexed { index, phase ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(start = Spacing.xxl))
            }
            UpcomingPhaseRow(phase = phase, zone = zone)
        }
    }
}

@Composable
private fun UpcomingPhaseRow(phase: PrincipalPhase, zone: ZoneId, modifier: Modifier = Modifier) {
    val name = stringResource(phase.name.labelRes)
    val date = DateFormatter.withZone(zone).format(phase.instant)
    val relative = relativeDays(phase.instant)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md)
            .clearAndSetSemantics { contentDescription = "$name, $date, $relative" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Each row draws its own phase from that phase's own elongation, so the discs cannot fall out
        // of step with their labels.
        Box(
            modifier = Modifier
                .size(RowDiscDiameter)
                // A scrap of night sky behind each small disc, for the same reason the hero has one:
                // an unlit new moon on a light surface would otherwise be an invisible row.
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            NightSkyPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues()) {
                MoonDisc(
                    elongationDegrees = phase.name.principalElongation ?: 0.0,
                    diameter = RowDiscDiameter,
                    showsDetail = false,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = relative,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Presentation helpers ──────────────────────────────────────────────────

/** The eight phase names, as string resources so they localise. */
private val MoonPhaseName.labelRes: Int
    get() = when (this) {
        MoonPhaseName.NEW -> R.string.moon_phase_new
        MoonPhaseName.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
        MoonPhaseName.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        MoonPhaseName.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
        MoonPhaseName.FULL -> R.string.moon_phase_full
        MoonPhaseName.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
        MoonPhaseName.LAST_QUARTER -> R.string.moon_phase_last_quarter
        MoonPhaseName.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
    }

/**
 * The distance band, in words.
 *
 * Only the mapping lives here. The thresholds are in the domain model, shared with iOS and unit-tested
 * on both platforms: a pure Kotlin module cannot reach `R`, but it can decide which band a distance
 * falls in, and that is the part worth testing.
 */
private val MoonDistanceBand.labelRes: Int
    get() = when (this) {
        MoonDistanceBand.VERY_CLOSE -> R.string.moon_distance_very_close
        MoonDistanceBand.CLOSER -> R.string.moon_distance_closer
        MoonDistanceBand.AVERAGE -> R.string.moon_distance_average
        MoonDistanceBand.FURTHER -> R.string.moon_distance_further
        MoonDistanceBand.VERY_FAR -> R.string.moon_distance_very_far
    }

@Composable
private fun ageDescription(snapshot: MoonSnapshot): String = if (snapshot.ageDays < 1) {
    stringResource(R.string.moon_age_under_a_day)
} else {
    pluralStringResource(
        R.plurals.moon_age_days,
        snapshot.ageDays.roundToLong().toInt(),
        snapshot.ageDays.roundToLong(),
    )
}

@Composable
private fun fullMoonCountdown(snapshot: MoonSnapshot): String? {
    val full = snapshot.nextFullMoon ?: return null
    val days = Duration.between(snapshot.instant, full.instant).toDays()
    return when (days) {
        0L -> stringResource(R.string.moon_full_tonight)
        1L -> stringResource(R.string.moon_full_tomorrow)
        else -> pluralStringResource(R.plurals.moon_full_in_days, days.toInt(), days)
    }
}

@Composable
private fun relativeDays(instant: Instant): String {
    val days = ChronoUnit.DAYS.between(Instant.now(), instant)
    return when {
        days <= 0 -> stringResource(R.string.moon_relative_today)
        days == 1L -> stringResource(R.string.moon_relative_tomorrow)
        else -> pluralStringResource(R.plurals.moon_relative_in_days, days.toInt(), days)
    }
}

/** 0 at moonrise, 1 at moonset; negative when the Moon is not up. */
private fun riseProgress(snapshot: MoonSnapshot): Float {
    val rise = snapshot.moonrise
    val span = snapshot.timeAboveHorizon?.takeIf { !it.isZero }
    return if (rise == null || span == null) {
        BelowHorizon
    } else {
        Duration.between(rise, snapshot.instant).seconds.toFloat() / span.seconds.toFloat()
    }
}

private fun Instant?.formatTime(zone: ZoneId): String =
    this?.let { TimeFormatter.withZone(zone).format(it) } ?: Placeholder

/** 24-hour, matching iOS, so a side-by-side comparison differs only where the platforms do. */
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")
private const val Placeholder = "N/A"

/** Any progress outside 0…1 hides the marker, which is the honest drawing when the Moon is down. */
private const val BelowHorizon = -1f

private val DiscDiameter = 160.dp
private val RingDiameter = 205.dp
private val RowDiscDiameter = 34.dp
private const val TertiaryAlpha = 0.65f

@Preview(showBackground = true)
@Composable
private fun MoonScreenPreview() {
    SkyCastTheme {
        // The sky comes from `RootScreen` at runtime, so the preview supplies its own. Without it
        // this renders dark-themed cards on a white page, which is not what the screen looks like.
        Box(modifier = Modifier.fillMaxSize().nightSky()) {
            MoonContent(
                uiState = MoonUiState(
                    snapshot = MoonCalculator.snapshot(
                        instant = Instant.parse("2026-08-18T20:00:00Z"),
                        latitude = 51.5074,
                        longitude = -0.1278,
                        zone = ZoneOffset.UTC,
                    ),
                    zone = ZoneOffset.UTC,
                ),
            )
        }
    }
}

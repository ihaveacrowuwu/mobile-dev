package com.nauhaan.skycast.ui.moon

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.AuroraReading
import com.nauhaan.skycast.domain.model.AuroraCalculator
import com.nauhaan.skycast.domain.model.AuroraChance
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.SpaceWeather
import java.util.Locale

/**
 * The aurora card's copy for a place, or `null` when there is nothing worth saying.
 *
 * Returns `null` for the tropics rather than a card reading "never". Somewhere within reach of the oval a
 * quiet night is still information, "you need Kp 6, tonight reaches 4.7" tells you to look again tomorrow,
 * but at a geomagnetic latitude the aurora has never reached, a permanent negative is just clutter.
 */
@Composable
fun auroraReading(location: SavedLocation, weather: SpaceWeather): AuroraReading? {
    val magneticLatitude = AuroraCalculator.geomagneticLatitude(location.latitude, location.longitude)
    val minimumKp = AuroraCalculator.minimumKpForChance(magneticLatitude) ?: return null

    val peak = weather.peakAhead()
    val bestKp = maxOf(weather.kpNow, peak?.kp ?: 0.0)
    val chance = AuroraCalculator.chance(bestKp, magneticLatitude)

    val headline = stringResource(chance.headlineRes)
    val detail = if (chance >= AuroraChance.POSSIBLE) {
        stringResource(R.string.aurora_detail_look_north, location.name, minimumKp)
    } else {
        stringResource(R.string.aurora_detail_needs_more, location.name, minimumKp, formatKp(bestKp))
    }

    val kpNow = weather.stormLevel?.let { storm ->
        stringResource(R.string.aurora_kp_now_storm, formatKp(weather.kpNow), storm)
    } ?: stringResource(R.string.aurora_kp_now, formatKp(weather.kpNow))

    return AuroraReading(
        headline = headline,
        detail = detail,
        kpNowLabel = kpNow,
        kpPeakLabel = peak
            ?.let { stringResource(R.string.aurora_kp_peak, formatKp(it.kp)) }
            .orEmpty(),
        // Both fractions are of the Kp scale, so the threshold line and the forecast marker are directly
        // comparable, which is the only reason the bar says anything.
        reachFraction = (minimumKp / KP_MAX).toFloat(),
        peakFraction = (bestKp / KP_MAX).toFloat(),
        contentDescription = "$headline $detail",
    )
}

/** One decimal place, and none when it is a whole number, so "Kp 5" rather than "Kp 5.0". */
private fun formatKp(kp: Double): String = if (kp == kp.toInt().toDouble()) {
    kp.toInt().toString()
} else {
    // Explicit locale: the default would print "4,7" where the user's locale uses a comma, and Kp is a
    // scientific index rather than a localised quantity.
    String.format(Locale.US, "%.1f", kp)
}

private val AuroraChance.headlineRes: Int
    get() = when (this) {
        AuroraChance.NONE -> R.string.aurora_none
        AuroraChance.FAINT_ON_HORIZON -> R.string.aurora_faint
        AuroraChance.POSSIBLE -> R.string.aurora_possible
        AuroraChance.LIKELY -> R.string.aurora_likely
        AuroraChance.OVERHEAD -> R.string.aurora_overhead
    }

private const val KP_MAX = 9.0

package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.local.entity.CachedSpaceWeatherEntity
import com.nauhaan.skycast.data.remote.dto.KpForecastEntryDto
import com.nauhaan.skycast.domain.model.KpPeriod
import com.nauhaan.skycast.domain.model.SpaceWeather
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * NOAA's Kp feed to the domain, and to the cache.
 */
object SpaceWeatherMapper {
    /**
     * The current reading and the forecast ahead of it, from one feed.
     *
     * The feed mixes past and future in a single array and marks each entry `observed`, `estimated`
     * or `predicted`. It is **not** trimmed to now, so the split comes from that field: the last
     * "observed" entry is the present, and everything marked estimated or predicted is the future
     * even when its timestamp is a few minutes behind.
     *
     * Returns `null` when the feed contains nothing measured at all.
     */
    fun toDomain(entries: List<KpForecastEntryDto>, cachedAt: Instant): SpaceWeather? {
        val measured = entries.filter { it.observed.equals(OBSERVED, ignoreCase = true) }
        val latest = measured.maxByOrNull { it.timeTag } ?: return null

        val upcoming = entries
            .filterNot { it.observed.equals(OBSERVED, ignoreCase = true) }
            .map { KpPeriod(time = it.timeTag.toInstant(), kp = it.kp, stormLevel = it.noaaScale) }
            .sortedBy { it.time }

        return SpaceWeather(
            kpNow = latest.kp,
            observedAt = latest.timeTag.toInstant(),
            stormLevel = latest.noaaScale,
            upcoming = upcoming,
            cachedAt = cachedAt,
        )
    }

    fun toEntity(weather: SpaceWeather): CachedSpaceWeatherEntity = CachedSpaceWeatherEntity(
        kpNow = weather.kpNow,
        observedAt = weather.observedAt.epochSecond,
        stormLevel = weather.stormLevel,
        upcoming = weather.upcoming.joinToString(PERIOD_SEPARATOR) { period ->
            listOf(
                period.time.epochSecond.toString(),
                period.kp.toString(),
                period.stormLevel.orEmpty(),
            ).joinToString(FIELD_SEPARATOR)
        },
        cachedAt = weather.cachedAt.epochSecond,
    )

    fun toDomain(entity: CachedSpaceWeatherEntity): SpaceWeather = SpaceWeather(
        kpNow = entity.kpNow,
        observedAt = Instant.ofEpochSecond(entity.observedAt),
        stormLevel = entity.stormLevel,
        upcoming = entity.upcoming
            .split(PERIOD_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { encoded ->
                val fields = encoded.split(FIELD_SEPARATOR)
                val seconds = fields.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val kp = fields.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                KpPeriod(
                    time = Instant.ofEpochSecond(seconds),
                    kp = kp,
                    stormLevel = fields.getOrNull(2)?.takeIf { it.isNotBlank() },
                )
            },
        cachedAt = Instant.ofEpochSecond(entity.cachedAt),
    )

    /**
     * NOAA's timestamps carry **no zone suffix** and are UTC.
     *
     * Parsed explicitly as UTC rather than as a local time: taking the device's zone here would shift every
     * reading by the offset, which on this feed means attributing tonight's storm to this afternoon.
     */
    private fun String.toInstant(): Instant = LocalDateTime.parse(replace(" ", "T")).toInstant(ZoneOffset.UTC)

    private const val OBSERVED = "observed"
    private const val PERIOD_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ":"
}

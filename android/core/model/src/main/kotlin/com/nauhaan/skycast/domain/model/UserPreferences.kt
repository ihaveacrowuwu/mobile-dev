package com.nauhaan.skycast.domain.model

/**
 * The user's settings. Persisted in DataStore (key–value), not Room: these are a
 * handful of scalars with no relationships, so a relational store would be overkill.
 */
data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.METRES_PER_SECOND,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColour: Boolean = true,
)

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    ;

    /** Converts a canonical Celsius value into this unit. */
    fun convertFromCelsius(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }
}

enum class WindSpeedUnit(val symbol: String) {
    METRES_PER_SECOND("m/s"),
    KILOMETRES_PER_HOUR("km/h"),
    MILES_PER_HOUR("mph"),
    ;

    fun convertFromMetresPerSecond(metresPerSecond: Double): Double = when (this) {
        METRES_PER_SECOND -> metresPerSecond
        KILOMETRES_PER_HOUR -> metresPerSecond * 3.6
        MILES_PER_HOUR -> metresPerSecond * 2.236_936
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

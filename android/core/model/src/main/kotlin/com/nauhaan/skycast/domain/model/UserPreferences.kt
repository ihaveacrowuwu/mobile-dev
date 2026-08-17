package com.nauhaan.skycast.domain.model

/**
 * The user's settings. Persisted in DataStore (key–value), not Room: these are a
 * handful of scalars with no relationships, so a relational store would be overkill.
 */
data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.METRES_PER_SECOND,
    val pressureUnit: PressureUnit = PressureUnit.HECTOPASCALS,
    val visibilityUnit: VisibilityUnit = VisibilityUnit.KILOMETRES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColour: Boolean = true,
)

/**
 * A displayable unit.
 *
 * Every unit enum converts **from the canonical value** the domain stores, Celsius, metres per
 * second, hectopascals, metres, rather than between arbitrary pairs. One direction of conversion
 * per unit means there is no lattice of conversions to get wrong, and it is what lets a settings
 * change re-render from cache with no network call.
 */
sealed interface DisplayUnit {
    /** Appended to the value, e.g. "°C" or " kt". Includes its own leading space where one reads better. */
    val symbol: String

    /** Shown in Settings, e.g. "Knots (kt)". */
    val displayName: String
}

enum class TemperatureUnit(override val symbol: String, override val displayName: String) : DisplayUnit {
    CELSIUS("°C", "Celsius (°C)"),
    FAHRENHEIT("°F", "Fahrenheit (°F)"),

    /**
     * Absolute temperature. Not a practical choice for checking whether to take a coat, and that is
     * rather the point of offering it, it costs one line and it is correct.
     */
    KELVIN(" K", "Kelvin (K)"),
    ;

    /** Converts a canonical Celsius value into this unit. */
    fun convertFromCelsius(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
        KELVIN -> celsius + ABSOLUTE_ZERO_CELSIUS
    }

    private companion object {
        /** 0 °C in kelvin. */
        const val ABSOLUTE_ZERO_CELSIUS = 273.15
    }
}

enum class WindSpeedUnit(override val symbol: String, override val displayName: String) : DisplayUnit {
    METRES_PER_SECOND("m/s", "Metres per second (m/s)"),
    KILOMETRES_PER_HOUR("km/h", "Kilometres per hour (km/h)"),
    MILES_PER_HOUR("mph", "Miles per hour (mph)"),

    /**
     * Nautical miles per hour, what aviation and sailing actually use, and what every METAR
     * reports. One knot is exactly 1852 m/h by definition of the nautical mile.
     */
    KNOTS("kt", "Knots (kt)"),

    /**
     * The Beaufort scale: a force number rather than a speed, describing observable effects at sea
     * and on land. Rendered as a plain number, because "5 Bft" is the whole reading.
     */
    BEAUFORT("Bft", "Beaufort scale"),
    ;

    fun convertFromMetresPerSecond(metresPerSecond: Double): Double = when (this) {
        METRES_PER_SECOND -> metresPerSecond
        KILOMETRES_PER_HOUR -> metresPerSecond * 3.6
        MILES_PER_HOUR -> metresPerSecond * 2.236_936
        KNOTS -> metresPerSecond * METRES_PER_SECOND_IN_KNOTS
        BEAUFORT -> beaufortForce(metresPerSecond).toDouble()
    }

    /** Beaufort is a scale of whole numbers, so a decimal place would be meaningless. */
    val isWholeNumber: Boolean get() = this == BEAUFORT

    private companion object {
        /** 3600 / 1852, from the definition of the nautical mile. */
        const val METRES_PER_SECOND_IN_KNOTS = 1.943_844

        /**
         * Upper bound of each Beaufort force in m/s, from the standard scale. Force 12 has no upper
         * bound, so anything above the last entry is a hurricane.
         */
        val BEAUFORT_UPPER_BOUNDS = doubleArrayOf(
            0.5, 1.5, 3.3, 5.5, 7.9, 10.7, 13.8, 17.1, 20.7, 24.4, 28.4, 32.6,
        )

        fun beaufortForce(metresPerSecond: Double): Int {
            val index = BEAUFORT_UPPER_BOUNDS.indexOfFirst { metresPerSecond < it }
            return if (index >= 0) index else BEAUFORT_UPPER_BOUNDS.size
        }
    }
}

/**
 * Atmospheric pressure.
 *
 * Hectopascals and millibars are numerically identical, 1 hPa = 1 mbar exactly, so only one is
 * offered, labelled with both names rather than pretending they are a choice.
 */
enum class PressureUnit(override val symbol: String, override val displayName: String) : DisplayUnit {
    HECTOPASCALS("hPa", "Hectopascals (hPa / mbar)"),

    /**
     * Inches of mercury: the altimeter setting in the United States, Canada and Japan, and the
     * reason a pilot's altimeter reads 29.92 on a standard day.
     */
    INCHES_OF_MERCURY("inHg", "Inches of mercury (inHg)"),

    /** Millimetres of mercury, still used for pressure in parts of Europe and in medicine. */
    MILLIMETRES_OF_MERCURY("mmHg", "Millimetres of mercury (mmHg)"),
    ;

    fun convertFromHectopascals(hectopascals: Double): Double = when (this) {
        HECTOPASCALS -> hectopascals
        INCHES_OF_MERCURY -> hectopascals * HECTOPASCALS_IN_INCH_OF_MERCURY
        MILLIMETRES_OF_MERCURY -> hectopascals * HECTOPASCALS_IN_MILLIMETRE_OF_MERCURY
    }

    /** inHg is conventionally quoted to two decimals (29.92); the others to none. */
    val decimalPlaces: Int get() = if (this == INCHES_OF_MERCURY) 2 else 0

    private companion object {
        const val HECTOPASCALS_IN_INCH_OF_MERCURY = 0.029_529_98
        const val HECTOPASCALS_IN_MILLIMETRE_OF_MERCURY = 0.750_062
    }
}

/**
 * Visibility.
 *
 * Aviation reports visibility in statute miles in the United States and in metres elsewhere;
 * nautical miles are included because they are the unit everything else in a cockpit uses.
 */
enum class VisibilityUnit(override val symbol: String, override val displayName: String) : DisplayUnit {
    KILOMETRES("km", "Kilometres (km)"),
    MILES("mi", "Statute miles (mi)"),
    NAUTICAL_MILES("NM", "Nautical miles (NM)"),
    ;

    fun convertFromMetres(metres: Double): Double = when (this) {
        KILOMETRES -> metres / METRES_IN_KILOMETRE
        MILES -> metres / METRES_IN_MILE
        NAUTICAL_MILES -> metres / METRES_IN_NAUTICAL_MILE
    }

    private companion object {
        const val METRES_IN_KILOMETRE = 1_000.0
        const val METRES_IN_MILE = 1_609.344

        /** Exact by definition. */
        const val METRES_IN_NAUTICAL_MILE = 1_852.0
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

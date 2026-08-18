package com.nauhaan.skycast.data.remote

import com.nauhaan.skycast.data.remote.dto.MetarDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The NOAA Aviation Weather Center endpoint SkyCast uses for METARs.
 *
 * A second data source, and the reason is that OpenWeather has no METAR: the format is issued by
 * airports and distributed by national weather services, so an aviation observation has to come
 * from an aviation source. This one needs **no API key**, which is also why it gets its own OkHttp
 * client, see `NetworkModule.provideAviationOkHttpClient`.
 *
 * There is no "nearest station" endpoint, so the query is a bounding box around the location and the
 * nearest is chosen locally in `MetarMapper`.
 */
interface AviationWeatherApi {
    /**
     * Every station reporting inside a bounding box, as `minLat,minLon,maxLat,maxLon`.
     *
     * @param bbox a comma-separated box. Kept as a single string because that is the shape the API
     *   documents, and splitting it into four parameters here would only invite them being reordered.
     */
    @GET("metar")
    suspend fun getMetars(@Query("bbox") bbox: String, @Query("format") format: String = JSON): List<MetarDto>

    private companion object {
        const val JSON = "json"
    }
}

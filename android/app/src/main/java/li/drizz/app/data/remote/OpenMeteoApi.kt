package li.drizz.app.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A thin Open-Meteo client: raw JSON in, parsed values out. */
class OpenMeteoApi(context: Context) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 20L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** GET with query params; returns the parsed body element. */
    suspend fun get(url: String, params: List<Pair<String, String?>>): JsonElement =
        withContext(Dispatchers.IO) {
            val httpUrl = url.toHttpUrl().newBuilder().apply {
                params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
            }.build()
            val request = Request.Builder().url(httpUrl).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Open-Meteo errors carry {"error":true,"reason":"..."} in the body.
                    val reason = runCatching {
                        json.parseToJsonElement(body).jsonObject["reason"]?.jsonPrimitive?.content
                    }.getOrNull()
                    throw ApiException(response.code, reason ?: "HTTP ${response.code}")
                }
                json.parseToJsonElement(body)
            }
        }

    companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val ENSEMBLE_URL = "https://ensemble-api.open-meteo.com/v1/ensemble"
        const val ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"
        const val SEASONAL_URL = "https://seasonal-api.open-meteo.com/v1/seasonal"
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val GEOCODING_GET_URL = "https://geocoding-api.open-meteo.com/v1/get"

        /** Open-Meteo returns an array for multi-location / multi-model queries. */
        fun asObjectList(element: JsonElement): List<JsonObject> = when (element) {
            is JsonArray -> element.map { it.jsonObject }
            else -> listOf(element.jsonObject)
        }

        fun longs(obj: JsonObject, key: String): List<Long> =
            (obj[key] as? JsonArray)?.map { it.jsonPrimitive.long } ?: emptyList()

        /** Numbers or nulls; JSON `null` and absent entries become `null`. */
        fun doubles(obj: JsonObject, key: String): List<Double?> =
            (obj[key] as? JsonArray)?.map { el ->
                when (el) {
                    is JsonNull -> null
                    is JsonPrimitive -> el.doubleOrNull
                    else -> null
                }
            } ?: emptyList()

        fun string(obj: JsonObject, key: String): String? =
            (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        fun int(obj: JsonObject, key: String): Long? =
            (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull?.toLong()

        fun double(obj: JsonObject, key: String): Double? =
            (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

        /**
         * Splits an hourly/daily block into time + variable arrays. Ensemble and
         * seasonal responses repeat each variable once per member with a
         * `_memberNN` suffix; callers match keys by prefix.
         */
        fun parseTimeSeries(block: JsonObject): Pair<List<Long>, Map<String, List<Double?>>> {
            val time = longs(block, "time")
            val vars = mutableMapOf<String, List<Double?>>()
            for ((key, value) in block) {
                if (key == "time") continue
                vars[key] = doubles(block, key)
                if (value is JsonNull) vars.remove(key)
            }
            return time to vars
        }
    }
}

/** Transport or API error carrying the server's human-readable reason. */
class ApiException(val code: Int, message: String) : Exception(message)


package li.drizz.app.data

import android.content.Context
import li.drizz.app.domain.model.NearbyCity
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearby-cities suggestions from the bundled GeoNames extract (pop ≥ 20k,
 * biggest first) — the same dataset the website cuts into tiles, shipped whole
 * in the APK so the panel works fully offline.
 *
 * Ranking ports findNearbyCities(): population damped by distance, widened
 * rings, a "same place" hole around the anchor, and a minimum separation
 * between picks so metropolises don't fill the list with their own suburbs.
 */
class CitiesRepository(context: Context) {

    private val cities: List<CityRow> by lazy {
        runCatching {
            context.assets.open("data/cities.json").bufferedReader().use { it.readText() }
        }.map { text ->
            val arr = org.json.JSONArray(text)
            List(arr.length()) { i ->
                val row = arr.getJSONArray(i)
                CityRow(
                    id = row.getLong(0), name = row.getString(1), country = row.getString(2),
                    lat = row.getDouble(3), lon = row.getDouble(4), popK = row.getLong(5)
                )
            }
        }.getOrElse { emptyList() }
    }

    private data class CityRow(
        val id: Long, val name: String, val country: String,
        val lat: Double, val lon: Double, val popK: Long
    )

    companion object {
        private val SEARCH_RADII_KM = listOf(200.0, 400.0, 1500.0)

        /** London's boroughs sit 20 km from its centre and share its weather. */
        fun samePlaceKm(population: Long): Double = 10.0 + 15.0 * min(1.0, population / 5_000_000.0)

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val toRad = Math.PI / 180
            val dLat = (lat2 - lat1) * toRad
            val dLon = (lon2 - lon1) * toRad
            val h = sin(dLat / 2).pow2() + cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2).pow2()
            return 2 * 6371.0 * asin(min(1.0, sqrt(h)))
        }

        private fun Double.pow2(): Double = this * this
    }

    fun findNearby(latitude: Double, longitude: Double, count: Int = 10, population: Long = 0): List<NearbyCity> {
        if (cities.isEmpty()) return emptyList()
        val ownFootprint = samePlaceKm(population)
        var best: List<NearbyCity> = emptyList()

        for (radius in SEARCH_RADII_KM) {
            val halfWeight = radius / 2
            val minSeparation = maxOf(25.0, radius / 20)
            val scored = cities.asSequence().mapNotNull { c ->
                val dist = distanceKm(latitude, longitude, c.lat, c.lon)
                if (dist < ownFootprint || dist > radius) return@mapNotNull null
                val score = c.popK / (1 + (dist / halfWeight).pow2())
                NearbyCity(c.id, c.name, c.country, c.lat, c.lon, c.popK * 1000, dist) to score
            }.sortedByDescending { it.second }.map { it.first }

            val picked = mutableListOf<NearbyCity>()
            for (city in scored) {
                if (picked.size == count) break
                val tooClose = picked.any { p -> distanceKm(p.latitude, p.longitude, city.latitude, city.longitude) < minSeparation }
                if (!tooClose) picked += city
            }
            if (picked.size >= count) return picked.sortedBy { it.distanceKm }
            if (picked.size > best.size) best = picked
        }
        return best.sortedBy { it.distanceKm }
    }
}

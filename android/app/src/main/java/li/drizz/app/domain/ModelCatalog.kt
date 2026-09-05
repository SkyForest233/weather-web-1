package li.drizz.app.domain

import li.drizz.app.R

/**
 * The full forecast-model catalog, synced from src/routes/weather/options.ts
 * (labels, grid resolutions and update cadences follow open-meteo's domains).
 */
data class WeatherModel(
    val value: String,
    val labelRes: Int? = null,
    val label: String = value,
    val resolution: String? = null,
    val update: String? = null
)

data class WeatherModelGroup(val value: String, val label: String, val models: List<WeatherModel>)

object ModelCatalog {

    private fun m(value: String, label: String, resolution: String? = null, update: String? = null) =
        WeatherModel(value = value, label = label, resolution = resolution, update = update)

    val forecastGroups: List<WeatherModelGroup> = listOf(
        WeatherModelGroup(
            "automatic_seamless", "Automatic & seamless",
            listOf(
                m("best_match", "Best match", "varies", "varies"),
                m("icon_seamless", "DWD ICON Seamless", "2-13 km", "every 3 h"),
                m("gfs_seamless", "GFS Seamless", "3-25 km", "every hour"),
                m("meteofrance_seamless", "MF Seamless", "1-25 km", "every 3 h"),
                m("ukmo_seamless", "UKMO Seamless", "2-10 km", "every 3 h"),
                m("knmi_seamless", "KNMI Seamless", "2-3 km", "every hour"),
                m("dmi_seamless", "DMI Seamless", "2 km", "every 3 h"),
                m("metno_seamless", "MET Norway Seamless", "1 km", "every 3 h"),
                m("meteoswiss_icon_seamless", "MeteoSwiss ICON Seamless", "1-2 km", "every 3 h"),
                m("chmi_aladin_seamless", "CHMI Aladin Seamless", "1-2.3 km", "every 6 h"),
                m("jma_seamless", "JMA Seamless", "5-55 km", "every 3 h"),
                m("gem_seamless", "GEM Seamless", "1-15 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "ecmwf", "ECMWF",
            listOf(
                m("ecmwf_ifs", "ECMWF IFS HRES", "9 km", "every 6 h"),
                m("ecmwf_ifs025", "ECMWF IFS 0.25°", "25 km", "every 6 h"),
                m("ecmwf_aifs025_single", "ECMWF AIFS 0.25° Single", "25 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "dwd", "DWD Germany",
            listOf(
                m("icon_global", "DWD ICON", "13 km", "every 6 h"),
                m("icon_eu", "DWD ICON EU", "7 km", "every 3 h"),
                m("icon_d2", "DWD ICON D2", "2 km", "every 3 h")
            )
        ),
        WeatherModelGroup(
            "ncep", "NOAA U.S.",
            listOf(
                m("gfs_global", "GFS Global", "13 km", "every 6 h"),
                m("gfs_hrrr", "GFS HRRR Conus", "3 km", "every hour"),
                m("gfs_graphcast025", "GFS GraphCast 0.25°", "25 km", "every 6 h"),
                m("ncep_aigfs025", "GFS AIGFS 0.25°", "25 km", "every 6 h"),
                m("ncep_hgefs025_ensemble_mean", "GFS HGEFS 0.25° Ensemble Mean", "25 km", "every 6 h"),
                m("ncep_nbm_conus", "GFS NBM Conus", "2.5 km", "every hour"),
                m("ncep_nam_conus", "GFS NAM Conus", "12 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "meteofrance", "Météo-France",
            listOf(
                m("meteofrance_arpege_world", "MF ARPEGE World", "25 km", "every 3 h"),
                m("meteofrance_arpege_europe", "MF ARPEGE Europe", "10 km", "every 3 h"),
                m("meteofrance_arome_france", "MF AROME France", "2.5 km", "every 3 h"),
                m("meteofrance_arome_france_hd", "MF AROME France HD", "1 km", "every 3 h")
            )
        ),
        WeatherModelGroup(
            "ukmo", "UK Met Office",
            listOf(
                m("ukmo_global_deterministic_10km", "UK Met Office 10km", "10 km", "every 3 h"),
                m("ukmo_uk_deterministic_2km", "UK Met Office 2km", "2 km", "every 3 h")
            )
        ),
        WeatherModelGroup(
            "knmi", "KNMI Netherlands",
            listOf(
                m("knmi_harmonie_arome_europe", "KNMI Harmonie Arome Europe", "5.5 km", "every hour"),
                m("knmi_harmonie_arome_netherlands", "KNMI Harmonie Arome Netherlands", "2 km", "every hour")
            )
        ),
        WeatherModelGroup(
            "dmi", "DMI Denmark",
            listOf(m("dmi_harmonie_arome_europe", "DMI Harmonie Arome Europe", "2 km", "every 3 h"))
        ),
        WeatherModelGroup(
            "metno", "MET Norway",
            listOf(m("metno_nordic", "MET Norway Nordic", "1 km", "every 3 h"))
        ),
        WeatherModelGroup(
            "meteoswiss", "MeteoSwiss",
            listOf(
                m("meteoswiss_icon_ch1", "MeteoSwiss ICON CH1", "1 km", "every 3 h"),
                m("meteoswiss_icon_ch2", "MeteoSwiss ICON CH2", "2 km", "every 3 h")
            )
        ),
        WeatherModelGroup(
            "jma", "JMA Japan",
            listOf(
                m("jma_msm", "JMA MSM", "5 km", "every 3 h"),
                m("jma_gsm", "JMA GSM", "55 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "cma", "CMA China",
            listOf(m("cma_grapes_global", "CMA GRAPES Global", "14 km", "every 6 h"))
        ),
        WeatherModelGroup(
            "chmi", "CHMI Czech Republic",
            listOf(
                m("chmi_aladin_central_europe_2km", "CHMI Aladin Central Europe 2km", "2.3 km", "every 6 h"),
                m("chmi_aladin_cz_1km", "CHMI Aladin CZ 1km", "1 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "cmc_gem", "GEM Canada",
            listOf(
                m("gem_global", "GEM Global", "15 km", "every 12 h"),
                m("gem_regional", "GEM Regional", "10 km", "every 6 h"),
                m("gem_hrdps_west", "GEM HRDPS West", "1 km", "every 12 h")
            )
        ),
        WeatherModelGroup(
            "italia_meteo", "ItaliaMeteo",
            listOf(m("italia_meteo_arpae_icon_2i", "IM ARPAE ICON 2i", "2.5 km", "every 3 h"))
        )
    )

    val ensembleGroups: List<WeatherModelGroup> = listOf(
        WeatherModelGroup(
            "dwd", "DWD Germany",
            listOf(
                m("icon_seamless_eps", "DWD ICON EPS Seamless", "2-25 km", "every 6 h"),
                m("icon_global_eps", "DWD ICON EPS Global", "25 km", "every 12 h"),
                m("icon_eu_eps", "DWD ICON EPS EU", "13 km", "every 6 h"),
                m("icon_d2_eps", "DWD ICON EPS D2", "2 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "ncep", "NOAA U.S.",
            listOf(
                m("ncep_gefs_seamless", "GFS Ensemble Seamless", "25-50 km", "every 6 h"),
                m("ncep_gefs025", "GFS Ensemble 0.25°", "25 km", "every 6 h"),
                m("ncep_gefs05", "GFS Ensemble 0.5°", "50 km", "every 6 h"),
                m("ncep_aigefs025", "AIGEFS 0.25°", "25 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "ecmwf", "ECMWF",
            listOf(
                m("ecmwf_ifs025_ensemble", "ECMWF IFS 0.25° Ensemble", "25 km", "every 6 h"),
                m("ecmwf_aifs025_ensemble", "ECMWF AIFS 0.25° Ensemble", "25 km", "every 6 h")
            )
        ),
        WeatherModelGroup(
            "cmc_gem", "GEM Canada",
            listOf(m("gem_global_ensemble", "GEM Global Ensemble", "50 km", "every 12 h"))
        ),
        WeatherModelGroup(
            "ukmo", "UK Met Office",
            listOf(
                m("ukmo_global_ensemble_20km", "UK MetOffice Global 20km", "20 km"),
                m("ukmo_uk_ensemble_2km", "UK MetOffice UK 2km", "2 km")
            )
        ),
        WeatherModelGroup(
            "meteoswiss", "MeteoSwiss",
            listOf(
                m("meteoswiss_icon_ch1_ensemble", "MeteoSwiss ICON CH1", "1 km", "every 12 h"),
                m("meteoswiss_icon_ch2_ensemble", "MeteoSwiss ICON CH2", "2 km", "every 12 h")
            )
        ),
        WeatherModelGroup(
            "google", "Google",
            listOf(m("google_weathernext2_ensemble", "Google WeatherNext 2 Ensemble"))
        )
    )

    val archiveGroups: List<WeatherModelGroup> = listOf(
        WeatherModelGroup(
            "auto", "Automatic",
            listOf(m("best_match", "Best match", "varies", "daily"))
        ),
        WeatherModelGroup(
            "era5", "ECMWF reanalysis",
            listOf(
                m("era5_seamless", "ERA5 seamless", "9-25 km", "daily"),
                m("era5", "ERA5", "25 km", "daily"),
                m("era5_land", "ERA5-Land", "9 km", "daily"),
                m("ecmwf_ifs", "ECMWF IFS", "9 km", "daily")
            )
        ),
        WeatherModelGroup(
            "regional", "Regional reanalysis",
            listOf(m("cerra", "CERRA (Europe)", "5 km", "daily"))
        )
    )

    val seasonalGroups: List<WeatherModelGroup> = listOf(
        WeatherModelGroup(
            "auto", "Automatic",
            listOf(m("best_match", "Best match", "varies", "monthly"))
        ),
        WeatherModelGroup(
            "ecmwf", "ECMWF",
            listOf(m("ecmwf_seasonal_seamless", "ECMWF SEAS5", "36 km", "monthly"))
        )
    )

    val allForecastModels: List<WeatherModel> = forecastGroups.flatMap { it.models }
    val allEnsembleModels: List<WeatherModel> = ensembleGroups.flatMap { it.models }

    fun findForecastModel(value: String): WeatherModel? = allForecastModels.firstOrNull { it.value == value }
    fun findEnsembleModel(value: String): WeatherModel? = allEnsembleModels.firstOrNull { it.value == value }
    fun label(value: String): String = findForecastModel(value)?.label ?: value.replace('_', ' ')
    fun ensembleLabel(value: String): String = findEnsembleModel(value)?.label ?: value.replace('_', ' ')
    fun archiveLabel(value: String): String =
        archiveGroups.flatMap { it.models }.firstOrNull { it.value == value }?.label ?: value

    fun seasonalLabel(value: String): String =
        seasonalGroups.flatMap { it.models }.firstOrNull { it.value == value }?.label ?: value

    /** A city inside each regional model's domain — the way out when the chosen model has no data here. */
    fun inDomainCity(model: String): Pair<String, String>? = when {
        model.startsWith("meteoswiss") -> "zurich" to "Zürich"
        model.startsWith("italia_meteo") -> "rome" to "Rome"
        model.startsWith("icon") || model.startsWith("dwd") -> "berlin" to "Berlin"
        model.startsWith("meteofrance") || model.startsWith("arpege") || model.startsWith("arome") -> "paris" to "Paris"
        model.startsWith("ukmo") -> "london" to "London"
        model.startsWith("knmi") -> "amsterdam" to "Amsterdam"
        model.startsWith("dmi") -> "copenhagen" to "Copenhagen"
        model.startsWith("metno") -> "oslo" to "Oslo"
        model.startsWith("ncep") || model.startsWith("gfs") || model.startsWith("hrrr") || model.startsWith("nbm") -> "new-york" to "New York"
        model.startsWith("gem") -> "toronto" to "Toronto"
        model.startsWith("jma") -> "tokyo" to "Tokyo"
        model.startsWith("cma") -> "beijing" to "Beijing"
        else -> null
    }

    /** drizz.li model id → maps.open-meteo.com domain candidates (maps-domain.ts). */
    fun mapsDomainCandidates(model: String): List<String> = when {
        model == "icon_seamless" -> listOf("dwd_icon_seamless", "dwd_icon")
        model == "icon_global" -> listOf("dwd_icon")
        model == "icon_eu" -> listOf("dwd_icon_eu")
        model == "icon_d2" -> listOf("dwd_icon_d2")
        model == "gfs_seamless" -> listOf("ncep_gfs_seamless", "ncep_gfs013")
        model == "gfs_global" -> listOf("ncep_gfs013")
        model == "gfs_hrrr" -> listOf("ncep_hrrr_conus")
        model == "gfs_graphcast025" -> listOf("ncep_gfs_graphcast025")
        model == "meteofrance_seamless" -> listOf("meteofrance_seamless", "meteofrance_arpege_world025")
        model == "meteofrance_arpege_world" -> listOf("meteofrance_arpege_world025")
        model == "meteofrance_arome_france" -> listOf("meteofrance_arome_france0025")
        model == "ukmo_seamless" -> listOf("ukmo_seamless", "ukmo_global_deterministic_10km")
        model == "knmi_seamless" -> listOf("knmi_seamless", "knmi_harmonie_arome_europe")
        model == "dmi_seamless" -> listOf("dmi_harmonie_arome_europe")
        model == "metno_seamless" || model == "metno_nordic" -> listOf("metno_nordic_pp")
        model == "meteoswiss_icon_seamless" -> listOf("meteoswiss_icon_ch2")
        model == "chmi_aladin_seamless" -> listOf("chmi_aladin_seamless", "chmi_aladin_central_europe_2km")
        model == "jma_seamless" -> listOf("jma_seamless", "jma_gsm")
        model == "gem_seamless" -> listOf("cmc_gem_seamless", "cmc_gem_gdps_15km", "cmc_gem_gdps")
        model == "gem_global" -> listOf("cmc_gem_gdps_15km", "cmc_gem_gdps")
        model == "gem_regional" -> listOf("cmc_gem_rdps_10km", "cmc_gem_rdps")
        model in listOf("icon_seamless_eps", "icon_global_eps") -> listOf("dwd_icon_eps")
        model == "icon_eu_eps" -> listOf("dwd_icon_eu_eps")
        model == "icon_d2_eps" -> listOf("dwd_icon_d2_eps")
        model == "ncep_gefs_seamless" -> listOf("ncep_gefs025")
        model == "gem_global_ensemble" -> listOf("cmc_gem_geps")
        else -> listOf(model)
    }

    /** Comparison view categorical palette (comparison.ts), color-blind safe. */
    val modelColors: List<Long> = listOf(
        0xFF0072B2, 0xFFD55E00, 0xFF009E73, 0xFFCC79A7, 0xFFE69F00, 0xFF56B4E9,
        0xFF6F4EAD, 0xFF8C564B, 0xFFE11D48, 0xFF65A30D, 0xFF0891B2, 0xFFC026D3,
        0xFF2563EB, 0xFFEA580C, 0xFF059669, 0xFF9333EA
    ).map { it.toLong() }

    fun modelColor(modelId: String, selectionOrder: List<String>): Long {
        val idx = selectionOrder.indexOf(modelId)
        if (idx >= 0) return modelColors[idx % modelColors.size]
        val canonical = allForecastModels.indexOfFirst { it.value == modelId }
        if (canonical >= 0) return modelColors[canonical % modelColors.size]
        var hash = 2166136261L
        for (c in modelId) { hash = hash xor c.code.toLong(); hash *= 16777619L }
        return modelColors[((hash and Long.MAX_VALUE) % modelColors.size).toInt()]
    }
}

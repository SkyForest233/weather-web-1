package li.drizz.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatSymbols
import java.time.format.TextStyle
import java.util.Locale

/**
 * Time helpers. Open-Meteo (timeformat=unixtime) returns UTC instants plus the
 * location's UTC offset; everything below formats through the location's IANA
 * zone, like drizz.li's formatZoned.
 */
object Dates {

    fun zone(timezone: String): ZoneId =
        runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("UTC") }

    fun zoned(epochSeconds: Long, timezone: String): ZonedDateTime =
        Instant.ofEpochSecond(epochSeconds).atZone(zone(timezone))

    fun dayKey(epochSeconds: Long, timezone: String): String =
        zoned(epochSeconds, timezone).toLocalDate().toString()

    fun localDateKey(date: LocalDate): String = date.toString()

    /** Clock "HH:mm" in the location's zone. */
    fun clock(epochSeconds: Long, timezone: String): String =
        zoned(epochSeconds, timezone).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun hourOf(epochSeconds: Long, timezone: String): Int =
        zoned(epochSeconds, timezone).hour

    /** Short weekday name, e.g. "Fri". */
    fun weekday(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun dayOfMonth(date: LocalDate): Int = date.dayOfMonth

    fun monthName(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.month.getDisplayName(TextStyle.FULL, locale)

    /** Localized month name for "August 2026" style labels (UTC calendar). */
    fun monthYearLabel(year: Int, month: Int, locale: Locale = Locale.getDefault()): String {
        val symbols = FormatSymbols(locale)
        val monthName = java.time.YearMonth.of(year, month).atDay(1).month
            .getDisplayName(TextStyle.FULL, locale)
        return "$monthName $year"
    }

    /** Relative day label bucket: yesterday / today / tomorrow / weekday. */
    enum class RelDay { YESTERDAY, TODAY, TOMORROW, OTHER }

    fun relativeDay(date: LocalDate, timezone: String): RelDay {
        val today = LocalDate.now(zone(timezone))
        return when (date) {
            today.minusDays(1) -> RelDay.YESTERDAY
            today -> RelDay.TODAY
            today.plusDays(1) -> RelDay.TOMORROW
            else -> RelDay.OTHER
        }
    }

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** "yyyy-MM-dd" for API date parameters (location-local). */
    fun apiDate(date: ZonedDateTime): String = date.toLocalDate().toString()

    /** Parses "yyyy-MM-dd" into epoch seconds at local midnight of `timezone`. */
    fun parseDate(date: String, timezone: String): Long =
        LocalDate.parse(date).atStartOfDay(zone(timezone)).toEpochSecond()

    /** Group hourly timestamps (seconds) by local calendar date, in order. */
    fun groupByDay(
        timestamps: List<Long>,
        timezone: String
    ): List<Pair<LocalDate, List<Int>>> {
        val out = LinkedHashMap<LocalDate, MutableList<Int>>()
        timestamps.forEachIndexed { i, t ->
            val key = zoned(t, timezone).toLocalDate()
            out.getOrPut(key) { mutableListOf() }.add(i)
        }
        return out.map { (d, idx) -> d to idx.toList() }
    }
}

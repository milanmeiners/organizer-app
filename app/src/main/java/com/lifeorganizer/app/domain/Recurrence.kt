package com.lifeorganizer.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A subset of the iCalendar (RFC 5545) RRULE format that covers everything the app needs:
 * FREQ (DAILY/WEEKLY/MONTHLY/YEARLY), INTERVAL, BYDAY (incl. ordinals like 2MO or -1FR),
 * BYMONTHDAY (incl. negative values), COUNT and UNTIL.
 */
enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** [ordinal] 0 means "every such weekday"; 1..5 / -1..-5 means "nth (from end) weekday of the month". */
data class WeekdayNum(val ordinal: Int, val day: DayOfWeek)

data class RRule(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val count: Int? = null,
    val until: LocalDate? = null,
) {
    fun toRRuleString(): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        if (byDay.isNotEmpty()) parts += "BYDAY=" + byDay.joinToString(",") { wn ->
            (if (wn.ordinal != 0) wn.ordinal.toString() else "") + dayCode(wn.day)
        }
        if (byMonthDay.isNotEmpty()) parts += "BYMONTHDAY=" + byMonthDay.joinToString(",")
        if (count != null) parts += "COUNT=$count"
        if (until != null) parts += "UNTIL=" + until.format(DateTimeFormatter.BASIC_ISO_DATE)
        return parts.joinToString(";")
    }

    companion object {
        private val CODES = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY,
        )

        fun dayCode(d: DayOfWeek): String = CODES.entries.first { it.value == d }.key

        /** Returns null if the string is not a valid rule. */
        fun parse(input: String?): RRule? {
            if (input.isNullOrBlank()) return null
            val s = input.trim().removePrefix("RRULE:").trim()
            var freq: Freq? = null
            var interval = 1
            var byDay = emptyList<WeekdayNum>()
            var byMonthDay = emptyList<Int>()
            var count: Int? = null
            var until: LocalDate? = null
            try {
                for (part in s.split(";")) {
                    if (part.isBlank()) continue
                    val kv = part.split("=", limit = 2)
                    if (kv.size != 2) return null
                    val key = kv[0].trim().uppercase(Locale.ROOT)
                    val value = kv[1].trim().uppercase(Locale.ROOT)
                    when (key) {
                        "FREQ" -> freq = Freq.valueOf(value)
                        "INTERVAL" -> interval = value.toInt()
                        "BYDAY" -> byDay = value.split(",").filter { it.isNotBlank() }.map { token ->
                            val code = token.takeLast(2)
                            val day = CODES[code] ?: return null
                            val numPart = token.dropLast(2)
                            val ord = if (numPart.isEmpty()) 0 else numPart.removePrefix("+").toInt()
                            WeekdayNum(ord, day)
                        }
                        "BYMONTHDAY" -> byMonthDay = value.split(",").filter { it.isNotBlank() }.map { it.toInt() }
                        "COUNT" -> count = value.toInt()
                        "UNTIL" -> until = LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE)
                        "WKST" -> Unit // ignored, weeks always start on Monday
                        else -> return null
                    }
                }
            } catch (e: Exception) {
                return null
            }
            val f = freq ?: return null
            if (interval < 1) return null
            if (count != null && count!! < 1) return null
            if (byDay.any { it.ordinal !in -5..5 }) return null
            if (byMonthDay.any { it == 0 || it !in -31..31 }) return null
            return RRule(f, interval, byDay, byMonthDay, count, until)
        }
    }
}

object Recurrence {

    private const val MAX_PERIODS = 200_000

    /**
     * All occurrence dates of [rule] starting at [start] that fall within [from]..[to] (inclusive).
     * COUNT is always counted from [start], so results are stable regardless of the queried window.
     */
    fun occurrences(rule: RRule, start: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to < from) return emptyList()
        val result = mutableListOf<LocalDate>()
        val interval = maxOf(1, rule.interval)
        val hardEnd = if (rule.until != null && rule.until < to) rule.until else to
        if (hardEnd < start) return result

        // Without COUNT we may skip directly to the queried window.
        var period = 0L
        if (rule.count == null && from > start) {
            val units = when (rule.freq) {
                Freq.DAILY -> ChronoUnit.DAYS.between(start, from)
                Freq.WEEKLY -> ChronoUnit.WEEKS.between(mondayOf(start), mondayOf(from))
                Freq.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(from))
                Freq.YEARLY -> (from.year - start.year).toLong()
            }
            period = maxOf(0L, units / interval - 1)
        }

        var produced = 0
        var guard = 0
        while (guard++ < MAX_PERIODS) {
            if (periodStart(rule.freq, start, period, interval) > hardEnd) break
            for (d in candidates(rule, start, period, interval)) {
                if (d < start) continue
                if (rule.until != null && d > rule.until) return result
                if (rule.count != null && produced >= rule.count) return result
                produced++
                if (d > to) return result
                if (d >= from) result += d
            }
            period++
        }
        return result
    }

    /** Whether [date] is an occurrence of the rule. */
    fun occursOn(rule: RRule, start: LocalDate, date: LocalDate): Boolean =
        occurrences(rule, start, date, date).isNotEmpty()

    /** First occurrence on or after [from], searching at most [horizonDays] ahead. */
    fun nextOccurrence(rule: RRule, start: LocalDate, from: LocalDate, horizonDays: Long = 3660): LocalDate? =
        occurrences(rule, start, maxOf(from, start), maxOf(from, start).plusDays(horizonDays)).firstOrNull()

    private fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun periodStart(freq: Freq, start: LocalDate, period: Long, interval: Int): LocalDate = when (freq) {
        Freq.DAILY -> start.plusDays(period * interval)
        Freq.WEEKLY -> mondayOf(start).plusWeeks(period * interval)
        Freq.MONTHLY -> YearMonth.from(start).plusMonths(period * interval).atDay(1)
        Freq.YEARLY -> LocalDate.of(start.year, 1, 1).plusYears(period * interval)
    }

    private fun candidates(rule: RRule, start: LocalDate, period: Long, interval: Int): List<LocalDate> {
        return when (rule.freq) {
            Freq.DAILY -> {
                val d = start.plusDays(period * interval)
                if (rule.byDay.isNotEmpty() && rule.byDay.none { it.day == d.dayOfWeek }) emptyList() else listOf(d)
            }
            Freq.WEEKLY -> {
                val weekStart = mondayOf(start).plusWeeks(period * interval)
                val days = if (rule.byDay.isEmpty()) listOf(start.dayOfWeek) else rule.byDay.map { it.day }.distinct()
                days.map { weekStart.plusDays((it.value - 1).toLong()) }.sorted()
            }
            Freq.MONTHLY -> {
                val ym = YearMonth.from(start).plusMonths(period * interval)
                monthCandidates(rule, ym, start.dayOfMonth)
            }
            Freq.YEARLY -> {
                val year = start.year + period * interval
                val ym = YearMonth.of(year.toInt(), start.month)
                if (start.dayOfMonth > ym.lengthOfMonth()) emptyList() else listOf(ym.atDay(start.dayOfMonth))
            }
        }
    }

    private fun monthCandidates(rule: RRule, ym: YearMonth, defaultDay: Int): List<LocalDate> {
        val len = ym.lengthOfMonth()
        val fromMonthDays = rule.byMonthDay.mapNotNull { md ->
            val day = if (md > 0) md else len + md + 1
            if (day in 1..len) ym.atDay(day) else null
        }
        val fromWeekdays = rule.byDay.flatMap { wn -> weekdaysInMonth(ym, wn) }
        val result = when {
            rule.byDay.isNotEmpty() && rule.byMonthDay.isNotEmpty() -> fromWeekdays.filter { it in fromMonthDays }
            rule.byDay.isNotEmpty() -> fromWeekdays
            rule.byMonthDay.isNotEmpty() -> fromMonthDays
            defaultDay <= len -> listOf(ym.atDay(defaultDay))
            else -> emptyList()
        }
        return result.distinct().sorted()
    }

    private fun weekdaysInMonth(ym: YearMonth, wn: WeekdayNum): List<LocalDate> {
        val all = mutableListOf<LocalDate>()
        var d = ym.atDay(1).with(TemporalAdjusters.nextOrSame(wn.day))
        while (d.month == ym.month) {
            all += d
            d = d.plusWeeks(1)
        }
        return when {
            wn.ordinal == 0 -> all
            wn.ordinal > 0 -> listOfNotNull(all.getOrNull(wn.ordinal - 1))
            else -> listOfNotNull(all.getOrNull(all.size + wn.ordinal))
        }
    }

    // ---------- Human-readable description ----------

    private val SHORT = mapOf(
        DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun",
    )
    private val LONG = mapOf(
        DayOfWeek.MONDAY to "Monday", DayOfWeek.TUESDAY to "Tuesday", DayOfWeek.WEDNESDAY to "Wednesday",
        DayOfWeek.THURSDAY to "Thursday", DayOfWeek.FRIDAY to "Friday", DayOfWeek.SATURDAY to "Saturday",
        DayOfWeek.SUNDAY to "Sunday",
    )

    fun ordinalWord(n: Int): String = when (n) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"
        -1 -> "last"; -2 -> "second-to-last"; else -> "${n}th"
    }

    private fun nth(n: Int): String = when {
        n < 0 -> if (n == -1) "last day" else "${-n}. day from end"
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    fun describe(rule: RRule, start: LocalDate? = null): String {
        val i = rule.interval
        val base = when (rule.freq) {
            Freq.DAILY -> {
                val b = if (i == 1) "Every day" else "Every $i days"
                if (rule.byDay.isNotEmpty()) b + " (" + rule.byDay.joinToString(", ") { SHORT.getValue(it.day) } + ")" else b
            }
            Freq.WEEKLY -> {
                val b = if (i == 1) "Weekly" else "Every $i weeks"
                val days = if (rule.byDay.isEmpty()) listOfNotNull(start?.dayOfWeek) else rule.byDay.map { it.day }
                val sorted = days.distinct().sortedBy { it.value }
                when {
                    sorted.isEmpty() -> b
                    sorted.size == 5 && sorted.none { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY } && i == 1 -> "Every weekday"
                    sorted.size == 7 && i == 1 -> "Every day"
                    else -> b + " on " + sorted.joinToString(", ") { SHORT.getValue(it) }
                }
            }
            Freq.MONTHLY -> {
                val b = if (i == 1) "Monthly" else "Every $i months"
                when {
                    rule.byDay.isNotEmpty() -> b + " on the " + rule.byDay.joinToString(", ") {
                        if (it.ordinal == 0) "every ${LONG.getValue(it.day)}" else ordinalWord(it.ordinal) + " " + LONG.getValue(it.day)
                    }
                    rule.byMonthDay.isNotEmpty() -> b + " on the " + rule.byMonthDay.joinToString(", ") { nth(it) }
                    start != null -> b + " on the " + nth(start.dayOfMonth)
                    else -> b
                }
            }
            Freq.YEARLY -> {
                val b = if (i == 1) "Yearly" else "Every $i years"
                if (start != null) b + " on " + start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) else b
            }
        }
        val end = when {
            rule.count != null -> ", ${rule.count} times"
            rule.until != null -> ", until " + rule.until.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            else -> ""
        }
        return base + end
    }

    fun describe(rrule: String?, start: LocalDate? = null): String =
        RRule.parse(rrule)?.let { describe(it, start) } ?: "Does not repeat"
}

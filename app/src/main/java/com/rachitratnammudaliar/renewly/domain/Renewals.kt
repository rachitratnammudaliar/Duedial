package com.rachitratnammudaliar.renewly.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/** The unit of a billing period. Stored in the database by [name]. */
enum class PeriodUnit(val singular: String, val plural: String, val chrono: ChronoUnit, val perYear: BigDecimal) {
    DAY("day", "days", ChronoUnit.DAYS, BigDecimal(365)),
    WEEK("week", "weeks", ChronoUnit.WEEKS, BigDecimal(52)),
    MONTH("month", "months", ChronoUnit.MONTHS, BigDecimal(12)),
    YEAR("year", "years", ChronoUnit.YEARS, BigDecimal.ONE);

    companion object {
        fun fromName(name: String): PeriodUnit = entries.firstOrNull { it.name == name } ?: MONTH
    }
}

/** "Every [count] [unit]s", e.g. every 3 months. */
data class BillingPeriod(val count: Int, val unit: PeriodUnit) {
    init {
        require(count in 1..999) { "count must be between 1 and 999" }
    }

    val isMonthly: Boolean get() = unit == PeriodUnit.MONTH && count == 1

    val label: String
        get() = when {
            count == 1 && unit == PeriodUnit.DAY -> "Daily"
            count == 1 && unit == PeriodUnit.WEEK -> "Weekly"
            count == 1 && unit == PeriodUnit.MONTH -> "Monthly"
            count == 1 && unit == PeriodUnit.YEAR -> "Yearly"
            else -> "Every $count ${unit.plural}"
        }

    companion object {
        val WEEKLY = BillingPeriod(1, PeriodUnit.WEEK)
        val MONTHLY = BillingPeriod(1, PeriodUnit.MONTH)
        val QUARTERLY = BillingPeriod(3, PeriodUnit.MONTH)
        val HALF_YEARLY = BillingPeriod(6, PeriodUnit.MONTH)
        val YEARLY = BillingPeriod(1, PeriodUnit.YEAR)
        val presets = listOf(WEEKLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY)

        /** Safe constructor for values read from storage or backups. */
        fun of(count: Int, unitName: String): BillingPeriod =
            BillingPeriod(count.coerceIn(1, 999), PeriodUnit.fromName(unitName))

        /** Older backups stored a single cycle name. */
        fun fromLegacyCycle(name: String): BillingPeriod = when (name) {
            "WEEKLY" -> WEEKLY
            "QUARTERLY" -> QUARTERLY
            "HALF_YEARLY" -> HALF_YEARLY
            "YEARLY" -> YEARLY
            else -> MONTHLY
        }
    }
}

/**
 * Payment-date maths. Every date is computed from the first payment (the anchor),
 * never by adding to the previous date, so month ends don't drift:
 * a subscription started on 31 Jan is charged on 28/29 Feb, then 31 Mar.
 */
object Renewals {

    /** The n-th charge date (n = 0 is the first payment). */
    fun nthDate(anchor: LocalDate, period: BillingPeriod, n: Long): LocalDate =
        anchor.plus(n * period.count, period.unit.chrono)

    /** Index of the first charge on or after [day]. */
    private fun firstIndexOnOrAfter(anchor: LocalDate, period: BillingPeriod, day: LocalDate): Long {
        if (!anchor.isBefore(day)) return 0
        var n = (period.unit.chrono.between(anchor, day) / period.count).coerceAtLeast(0)
        while (n > 0 && !nthDate(anchor, period, n - 1).isBefore(day)) n--
        while (nthDate(anchor, period, n).isBefore(day)) n++
        return n
    }

    /** The next charge on or after [today]. */
    fun nextRenewal(anchor: LocalDate, period: BillingPeriod, today: LocalDate): LocalDate =
        nthDate(anchor, period, firstIndexOnOrAfter(anchor, period, today))

    /** The last charge before the next one, or null if the first payment hasn't happened yet. */
    fun previousPayment(anchor: LocalDate, period: BillingPeriod, today: LocalDate): LocalDate? {
        val n = firstIndexOnOrAfter(anchor, period, today)
        return if (n == 0L) null else nthDate(anchor, period, n - 1)
    }

    /** How many charges have happened up to and including [today]. */
    fun paymentsMade(anchor: LocalDate, period: BillingPeriod, today: LocalDate): Long {
        if (anchor.isAfter(today)) return 0
        val n = firstIndexOnOrAfter(anchor, period, today)
        return if (nthDate(anchor, period, n) == today) n + 1 else n
    }

    /** How many charges fall between [start] and [end], both inclusive. */
    fun chargesBetween(anchor: LocalDate, period: BillingPeriod, start: LocalDate, end: LocalDate): Int {
        if (end.isBefore(start)) return 0
        var n = firstIndexOnOrAfter(anchor, period, start)
        var count = 0
        while (!nthDate(anchor, period, n).isAfter(end) && count < 400) {
            count++
            n++
        }
        return count
    }

    fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** Average cost per month, in minor units (paise, cents). */
    fun monthlyEquivalentMinor(amountMinor: Long, period: BillingPeriod): Long =
        BigDecimal.valueOf(amountMinor)
            .multiply(period.unit.perYear)
            .divide(BigDecimal(12L * period.count), 0, RoundingMode.HALF_UP)
            .toLong()

    /** Average cost per year, in minor units. */
    fun yearlyEquivalentMinor(amountMinor: Long, period: BillingPeriod): Long =
        BigDecimal.valueOf(amountMinor)
            .multiply(period.unit.perYear)
            .divide(BigDecimal(period.count), 0, RoundingMode.HALF_UP)
            .toLong()

    /** "today", "tomorrow", "in 3 days", "in 2 weeks". */
    fun relativeLabel(days: Long): String = when {
        days < 0L -> "overdue"
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days < 7L -> "in $days days"
        days < 14L -> "in 1 week"
        days < 31L -> "in ${days / 7} weeks"
        days < 60L -> "in 1 month"
        days < 365L -> "in ${days / 30} months"
        else -> "in $days days"
    }
}

/** Money is stored as whole minor units (paise, cents) to avoid rounding errors. */
object Money {

    val currencies = listOf("INR", "EUR", "USD", "GBP")

    /** Parses "199", "199.5" or "199,50" into minor units. Null if invalid, zero, negative or too large. */
    fun parseToMinor(text: String): Long? {
        val cleaned = text.trim().replace(" ", "").replace(',', '.')
        if (cleaned.isEmpty()) return null
        return try {
            val value = BigDecimal(cleaned)
            if (value.signum() <= 0 || value.scale() > 2 || value > BigDecimal(100_000_000)) return null
            value.movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Minor units back to editable text, e.g. 19950 -> "199.5", 64900 -> "649". */
    fun minorToInput(minor: Long): String =
        BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros().toPlainString()

    fun format(minor: Long, currencyCode: String): String {
        val locale = when (currencyCode) {
            "INR" -> Locale.forLanguageTag("en-IN")
            "EUR" -> Locale.forLanguageTag("en-IE")
            "GBP" -> Locale.UK
            else -> Locale.US
        }
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(BigDecimal.valueOf(minor).movePointLeft(2))
    }

    /** A card's last four digits: exactly four digits, or empty. */
    fun isValidLast4(text: String): Boolean = text.isEmpty() || (text.length == 4 && text.all { it.isDigit() })
}

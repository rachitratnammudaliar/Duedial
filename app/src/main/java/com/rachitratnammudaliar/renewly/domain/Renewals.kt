package com.rachitratnammudaliar.renewly.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/** How often a subscription is charged. Stored in the database by [name]. */
enum class BillingCycle(val label: String, val months: Int, val weeks: Int) {
    WEEKLY("Weekly", months = 0, weeks = 1),
    MONTHLY("Monthly", months = 1, weeks = 0),
    QUARTERLY("Every 3 months", months = 3, weeks = 0),
    HALF_YEARLY("Every 6 months", months = 6, weeks = 0),
    YEARLY("Yearly", months = 12, weeks = 0);

    companion object {
        fun fromName(name: String): BillingCycle =
            entries.firstOrNull { it.name == name } ?: MONTHLY
    }
}

/**
 * Renewal date maths. Dates are always computed from the first payment date
 * (the anchor), never by adding to the previous renewal, so month ends don't drift:
 * a subscription started on 31 Jan renews on 28/29 Feb, then 31 Mar.
 */
object Renewals {

    /** The n-th charge date after [anchor] (n = 0 is the anchor itself). */
    fun nthDate(anchor: LocalDate, cycle: BillingCycle, n: Long): LocalDate =
        if (cycle.weeks > 0) anchor.plusWeeks(n * cycle.weeks)
        else anchor.plusMonths(n * cycle.months)

    /** The first charge date that is on or after [today]. */
    fun nextRenewal(anchor: LocalDate, cycle: BillingCycle, today: LocalDate): LocalDate {
        if (!anchor.isBefore(today)) return anchor
        var n = if (cycle.weeks > 0) {
            ChronoUnit.WEEKS.between(anchor, today) / cycle.weeks
        } else {
            ChronoUnit.MONTHS.between(anchor, today) / cycle.months
        }
        if (n < 0) n = 0
        while (n > 0 && !nthDate(anchor, cycle, n - 1).isBefore(today)) n--
        var date = nthDate(anchor, cycle, n)
        while (date.isBefore(today)) {
            n++
            date = nthDate(anchor, cycle, n)
        }
        return date
    }

    fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** What the subscription costs per month, in minor units (paise, cents). */
    fun monthlyEquivalentMinor(amountMinor: Long, cycle: BillingCycle): Long {
        val amount = BigDecimal.valueOf(amountMinor)
        val monthly = if (cycle.weeks > 0) {
            amount.multiply(BigDecimal(52)).divide(BigDecimal(12 * cycle.weeks), 0, RoundingMode.HALF_UP)
        } else {
            amount.divide(BigDecimal(cycle.months), 0, RoundingMode.HALF_UP)
        }
        return monthly.toLong()
    }

    /** "in 3 days", "tomorrow", "today". */
    fun relativeLabel(days: Long): String = when {
        days <= 0L -> "today"
        days == 1L -> "tomorrow"
        days < 7L -> "in $days days"
        days < 14L -> "in 1 week"
        days < 31L -> "in ${days / 7} weeks"
        else -> "in $days days"
    }
}

/** Money is stored as whole minor units (paise, cents) to avoid rounding errors. */
object Money {

    val currencies = listOf("INR", "EUR", "USD", "GBP")

    /** Parses "199", "199.5" or "199,50" into minor units. Null if invalid, zero or negative. */
    fun parseToMinor(text: String): Long? {
        val cleaned = text.trim().replace(" ", "").replace(',', '.')
        if (cleaned.isEmpty()) return null
        return try {
            val value = BigDecimal(cleaned)
            if (value.signum() <= 0 || value.scale() > 2) return null
            value.movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Minor units back to editable text, e.g. 19950 -> "199.50", 19900 -> "199". */
    fun minorToInput(minor: Long): String {
        val value = BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros()
        return value.toPlainString()
    }

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

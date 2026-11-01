package com.rachitratnammudaliar.renewly.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** What kind of thing is being tracked. Stored by [name]. */
enum class PlanType(val label: String, val description: String) {
    RECURRING("Subscription", "Paid on a schedule"),
    TRIAL("Free trial", "Free now, paid when the trial ends unless you cancel"),
    FREE("Free", "Costs nothing, e.g. a student offer or free tier"),
    LIFETIME("Lifetime", "Paid once, yours to keep");

    companion object {
        fun fromName(name: String): PlanType = entries.firstOrNull { it.name == name } ?: RECURRING
    }
}

enum class Status {
    /** Paid on a schedule, with an upcoming charge. */
    UPCOMING,
    /** In a free trial that will turn into a paid subscription. */
    TRIAL,
    /** In a trial that has been cancelled; no charge will happen. */
    TRIAL_CANCELLED,
    /** Free, with or without an end date. */
    FREE,
    /** A lifetime purchase. */
    OWNED,
    /** Cancelled or fixed-term: still active until the end date. */
    ENDING,
    /** Past its end date. */
    ENDED,
    /** Paused by the user. */
    PAUSED,
}

enum class EventKind { CHARGE, TRIAL_ENDS, FREE_ENDS, SUBSCRIPTION_ENDS }

data class PlanEvent(val date: LocalDate, val kind: EventKind)

/**
 * Everything Renewly needs to reason about one subscription, independent of the database.
 *
 * @param start the first charge date. For a trial that's the day the trial ends and the first
 *   charge happens; for a lifetime purchase it's the purchase date; for free plans it's when it started.
 * @param end the last day it's active, or null if it continues. No charges happen after it.
 */
data class Plan(
    val type: PlanType,
    val amountMinor: Long,
    val period: BillingPeriod,
    val start: LocalDate,
    val end: LocalDate? = null,
    val split: Int = 1,
    val includeInMonthly: Boolean = true,
    val active: Boolean = true,
    val regularAmountMinor: Long? = null,
)

object Plans {

    /** Weekly and monthly plans count towards the monthly bill by default; longer ones don't. */
    fun defaultIncludeInMonthly(type: PlanType, period: BillingPeriod): Boolean =
        (type == PlanType.RECURRING || type == PlanType.TRIAL) &&
            (period.unit == PeriodUnit.DAY || period.unit == PeriodUnit.WEEK || period.isMonthly)

    /** Your share of each charge, after splitting. */
    fun shareMinor(p: Plan): Long =
        BigDecimal.valueOf(p.amountMinor)
            .divide(BigDecimal(p.split.coerceAtLeast(1)), 0, RoundingMode.HALF_UP)
            .toLong()

    fun inTrial(p: Plan, today: LocalDate): Boolean = p.type == PlanType.TRIAL && today.isBefore(p.start)

    private fun trialCancelled(p: Plan): Boolean = p.type == PlanType.TRIAL && p.end != null && p.end.isBefore(p.start)

    private fun allowed(p: Plan, date: LocalDate): Boolean = p.end == null || !date.isAfter(p.end)

    /** The last charge on or before [date], respecting the end date. */
    private fun lastChargeOnOrBefore(p: Plan, date: LocalDate): LocalDate? {
        val limit = if (p.end != null && p.end.isBefore(date)) p.end else date
        return when (p.type) {
            PlanType.FREE -> null
            PlanType.LIFETIME -> if (!p.start.isAfter(limit)) p.start else null
            PlanType.RECURRING, PlanType.TRIAL -> {
                val made = Renewals.paymentsMade(p.start, p.period, limit)
                if (made == 0L) null else Renewals.nthDate(p.start, p.period, made - 1)
            }
        }
    }

    /** The next charge on or after [today], or null if nothing more will be charged. */
    fun nextCharge(p: Plan, today: LocalDate): LocalDate? {
        if (!p.active) return null
        val next = when (p.type) {
            PlanType.FREE -> null
            PlanType.LIFETIME -> if (!p.start.isBefore(today)) p.start else null
            PlanType.RECURRING, PlanType.TRIAL -> Renewals.nextRenewal(p.start, p.period, today)
        }
        return next?.takeIf { allowed(p, it) }
    }

    /** The most recent charge before today. */
    fun previousCharge(p: Plan, today: LocalDate): LocalDate? = lastChargeOnOrBefore(p, today.minusDays(1))

    /** How many charges have happened up to and including [today]. */
    fun paymentsMade(p: Plan, today: LocalDate): Long {
        val limit = if (p.end != null && p.end.isBefore(today)) p.end else today
        return when (p.type) {
            PlanType.FREE -> 0
            PlanType.LIFETIME -> if (!p.start.isAfter(limit)) 1 else 0
            PlanType.RECURRING, PlanType.TRIAL -> Renewals.paymentsMade(p.start, p.period, limit)
        }
    }

    /** Your share of everything paid so far, at today's price. */
    fun paidSoFarMinor(p: Plan, today: LocalDate): Long = shareMinor(p) * paymentsMade(p, today)

    fun status(p: Plan, today: LocalDate): Status {
        if (!p.active) return Status.PAUSED
        if (p.end != null && today.isAfter(p.end)) return Status.ENDED
        return when (p.type) {
            PlanType.FREE -> Status.FREE
            PlanType.LIFETIME -> Status.OWNED
            PlanType.TRIAL, PlanType.RECURRING -> when {
                trialCancelled(p) && today.isBefore(p.start) -> Status.TRIAL_CANCELLED
                inTrial(p, today) -> Status.TRIAL
                p.end != null -> Status.ENDING
                else -> Status.UPCOMING
            }
        }
    }

    /** Counts towards the monthly bill right now? */
    fun countsInMonthlyBill(p: Plan, today: LocalDate): Boolean =
        p.includeInMonthly && status(p, today) in setOf(Status.UPCOMING, Status.ENDING) &&
            (p.type == PlanType.RECURRING || p.type == PlanType.TRIAL)

    /** This plan's part of the monthly bill: your share, spread across months for longer periods. */
    fun monthlyBillMinor(p: Plan, today: LocalDate): Long =
        if (countsInMonthlyBill(p, today)) Renewals.monthlyEquivalentMinor(shareMinor(p), p.period) else 0

    /** Average cost per year for a running subscription, your share. Zero for free and lifetime. */
    fun yearlyMinor(p: Plan, today: LocalDate): Long =
        if (status(p, today) in setOf(Status.UPCOMING, Status.ENDING)) {
            Renewals.yearlyEquivalentMinor(shareMinor(p), p.period)
        } else {
            0
        }

    /** Your share of what's actually charged between [from] and [to], both inclusive. */
    fun dueBetweenMinor(p: Plan, from: LocalDate, to: LocalDate): Long {
        if (!p.active) return 0
        val last = if (p.end != null && p.end.isBefore(to)) p.end else to
        if (last.isBefore(from)) return 0
        val count = when (p.type) {
            PlanType.FREE -> 0
            PlanType.LIFETIME -> if (!p.start.isBefore(from) && !p.start.isAfter(last)) 1 else 0
            PlanType.RECURRING, PlanType.TRIAL -> Renewals.chargesBetween(p.start, p.period, from, last)
        }
        return shareMinor(p) * count
    }

    /** Saving per charge compared with the regular price, if one was entered. */
    fun savingMinor(p: Plan): Long? {
        val regular = p.regularAmountMinor ?: return null
        val paid = if (p.type == PlanType.FREE) 0 else p.amountMinor
        return (regular - paid).takeIf { it > 0 }
    }

    /** The next thing worth a reminder, or null. */
    fun nextEvent(p: Plan, today: LocalDate): PlanEvent? {
        return when (status(p, today)) {
            Status.PAUSED, Status.ENDED, Status.TRIAL_CANCELLED, Status.OWNED ->
                if (p.type == PlanType.LIFETIME) nextCharge(p, today)?.let { PlanEvent(it, EventKind.CHARGE) } else null
            Status.TRIAL -> PlanEvent(p.start, EventKind.TRIAL_ENDS)
            Status.FREE -> p.end?.let { PlanEvent(it, EventKind.FREE_ENDS) }
            Status.UPCOMING -> nextCharge(p, today)?.let { PlanEvent(it, EventKind.CHARGE) }
            Status.ENDING -> nextCharge(p, today)?.let { PlanEvent(it, EventKind.CHARGE) }
                ?: p.end?.let { PlanEvent(it, EventKind.SUBSCRIPTION_ENDS) }
        }
    }

    /**
     * Should a reminder be shown today? On the chosen number of days before the event,
     * and on the day of a charge. [remindDaysBefore] below zero turns reminders off.
     */
    fun reminderDue(p: Plan, remindDaysBefore: Int, today: LocalDate): PlanEvent? {
        if (remindDaysBefore < 0) return null
        val event = nextEvent(p, today) ?: return null
        val days = Renewals.daysUntil(today, event.date)
        val onDay = days == 0L && (event.kind == EventKind.CHARGE || event.kind == EventKind.TRIAL_ENDS)
        return if (days == remindDaysBefore.toLong() || onDay) event else null
    }
}

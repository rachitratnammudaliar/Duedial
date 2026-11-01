package com.rachitratnammudaliar.duedial.ui

import com.rachitratnammudaliar.duedial.data.Subscription
import com.rachitratnammudaliar.duedial.data.endDate
import com.rachitratnammudaliar.duedial.data.firstPaymentDate
import com.rachitratnammudaliar.duedial.data.nextCharge
import com.rachitratnammudaliar.duedial.data.period
import com.rachitratnammudaliar.duedial.data.planTypeValue
import com.rachitratnammudaliar.duedial.data.shareMinor
import com.rachitratnammudaliar.duedial.data.status
import com.rachitratnammudaliar.duedial.domain.Money
import com.rachitratnammudaliar.duedial.domain.PlanType
import com.rachitratnammudaliar.duedial.domain.Renewals
import com.rachitratnammudaliar.duedial.domain.Status
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Words and numbers shown for a subscription, shared by the list, details and reminders. */

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

val reminderOptions = listOf(
    -1 to "Off",
    0 to "On the day",
    1 to "1 day before",
    2 to "2 days before",
    3 to "3 days before",
    7 to "1 week before",
)

fun reminderLabel(days: Int): String =
    reminderOptions.firstOrNull { it.first == days }?.second ?: "$days days before"

enum class Tone { NORMAL, WARN, MUTED }

data class Line(val text: String, val tone: Tone = Tone.NORMAL)

/** One line under the name in the list: what happens next. */
fun statusLine(s: Subscription, today: LocalDate): Line {
    val end = s.endDate()
    return when (s.status(today)) {
        Status.UPCOMING -> {
            val next = s.nextCharge(today) ?: return Line("No upcoming payment", Tone.MUTED)
            val days = Renewals.daysUntil(today, next)
            Line("${next.format(dateFormat)} · ${Renewals.relativeLabel(days)}", if (days <= 3) Tone.WARN else Tone.NORMAL)
        }
        Status.ENDING -> {
            val next = s.nextCharge(today)
            if (next != null) {
                Line("${next.format(shortDate)} · last payment, ends ${end?.format(shortDate)}")
            } else {
                Line("Cancelled · active until ${end?.format(dateFormat)}", Tone.MUTED)
            }
        }
        Status.TRIAL -> {
            val trialEnd = s.firstPaymentDate()
            val days = Renewals.daysUntil(today, trialEnd)
            Line(
                "Trial ends ${Renewals.relativeLabel(days)} · cancel by ${trialEnd.minusDays(1).format(shortDate)}",
                if (days <= 3) Tone.WARN else Tone.NORMAL,
            )
        }
        Status.TRIAL_CANCELLED -> Line("Trial cancelled · free until ${end?.format(shortDate)}", Tone.MUTED)
        Status.FREE -> if (end != null) {
            val days = Renewals.daysUntil(today, end)
            Line("Free until ${end.format(dateFormat)}", if (days <= 7) Tone.WARN else Tone.NORMAL)
        } else {
            Line("Free · no end date")
        }
        Status.OWNED -> {
            val date = s.firstPaymentDate()
            if (date.isAfter(today)) Line("Purchase on ${date.format(dateFormat)}") else Line("Bought ${date.format(dateFormat)}", Tone.MUTED)
        }
        Status.ENDED -> Line("Ended ${end?.format(dateFormat) ?: ""}".trim(), Tone.MUTED)
        Status.PAUSED -> Line("Paused", Tone.MUTED)
    }
}

/** The big number on the right of a list row, and the small line under it. */
fun priceLabels(s: Subscription, today: LocalDate): Pair<String, String> {
    val share = Money.format(s.shareMinor(), s.currency)
    val split = if (s.splitCount > 1) " · your share" else ""
    return when (s.planTypeValue()) {
        PlanType.FREE -> "Free" to (s.regularAmountMinor?.let { "normally ${Money.format(it, s.currency)}" } ?: "")
        PlanType.LIFETIME -> share to "once"
        PlanType.TRIAL -> if (s.status(today) == Status.TRIAL || s.status(today) == Status.TRIAL_CANCELLED) {
            "Free" to "then $share"
        } else {
            share to s.period().label + split
        }
        PlanType.RECURRING -> share to s.period().label + split
    }
}

/** Which group a subscription is listed in on the home screen. */
fun listGroup(s: Subscription, today: LocalDate): Int = when (s.status(today)) {
    Status.UPCOMING, Status.ENDING, Status.TRIAL -> 0
    Status.FREE, Status.OWNED, Status.TRIAL_CANCELLED -> 1
    Status.ENDED, Status.PAUSED -> 2
}

val groupTitles = mapOf(1 to "Free & lifetime", 2 to "Ended & paused")

package com.rachitratnammudaliar.renewly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlansTest {

    private val today = LocalDate.of(2026, 11, 1)
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    private fun plan(
        type: PlanType = PlanType.RECURRING,
        amount: Long = 60000,
        period: BillingPeriod = BillingPeriod.MONTHLY,
        start: LocalDate = d(1, 15),
        end: LocalDate? = null,
        split: Int = 1,
        include: Boolean = Plans.defaultIncludeInMonthly(type, period),
        active: Boolean = true,
        regular: Long? = null,
    ) = Plan(type, amount, period, start, end, split, include, active, regular)

    @Test
    fun monthlyBillDefaults() {
        assertTrue(Plans.defaultIncludeInMonthly(PlanType.RECURRING, BillingPeriod.MONTHLY))
        assertTrue(Plans.defaultIncludeInMonthly(PlanType.RECURRING, BillingPeriod.WEEKLY))
        assertFalse(Plans.defaultIncludeInMonthly(PlanType.RECURRING, BillingPeriod.QUARTERLY))
        assertFalse(Plans.defaultIncludeInMonthly(PlanType.RECURRING, BillingPeriod.YEARLY))
        assertFalse(Plans.defaultIncludeInMonthly(PlanType.FREE, BillingPeriod.MONTHLY))
        assertFalse(Plans.defaultIncludeInMonthly(PlanType.LIFETIME, BillingPeriod.MONTHLY))
    }

    @Test
    fun longerPlansAreSpreadAcrossMonthsWhenIncluded() {
        val quarterly = plan(amount = 90000, period = BillingPeriod.QUARTERLY, include = true)
        assertEquals(30000, Plans.monthlyBillMinor(quarterly, today))
        val yearly = plan(amount = 120000, period = BillingPeriod.YEARLY, include = true)
        assertEquals(10000, Plans.monthlyBillMinor(yearly, today))
        assertEquals(0, Plans.monthlyBillMinor(yearly.copy(includeInMonthly = false), today))
    }

    @Test
    fun splitUsesYourShare() {
        val p = plan(amount = 23900, split = 2)
        assertEquals(11950, Plans.shareMinor(p))
        assertEquals(11950, Plans.monthlyBillMinor(p, today))
        assertEquals(11950 * 12, Plans.yearlyMinor(p, today))
        assertEquals(10000, Plans.shareMinor(plan(amount = 30000, split = 3)))
    }

    @Test
    fun trialIsFreeUntilItEnds() {
        val t = plan(type = PlanType.TRIAL, amount = 29900, start = d(11, 3))
        assertEquals(Status.TRIAL, Plans.status(t, today))
        assertEquals(0, Plans.monthlyBillMinor(t, today))
        assertEquals(0, Plans.paymentsMade(t, today))
        assertEquals(d(11, 3), Plans.nextCharge(t, today))
        assertEquals(PlanEvent(d(11, 3), EventKind.TRIAL_ENDS), Plans.nextEvent(t, today))
        assertEquals(PlanEvent(d(11, 3), EventKind.TRIAL_ENDS), Plans.reminderDue(t, 2, today))
        assertNull(Plans.reminderDue(t, 1, today))
        // after the trial it is a normal subscription
        assertEquals(Status.UPCOMING, Plans.status(t, d(11, 10)))
        assertEquals(29900, Plans.monthlyBillMinor(t, d(11, 10)))
        assertEquals(1, Plans.paymentsMade(t, d(11, 10)))
    }

    @Test
    fun cancelledTrialNeverCharges() {
        val t = plan(type = PlanType.TRIAL, start = d(11, 3), end = d(11, 2))
        assertEquals(Status.TRIAL_CANCELLED, Plans.status(t, today))
        assertNull(Plans.nextCharge(t, today))
        assertNull(Plans.nextEvent(t, today))
        assertEquals(Status.ENDED, Plans.status(t, d(11, 5)))
        assertEquals(0, Plans.paymentsMade(t, d(12, 31)))
        assertEquals(0, Plans.dueBetweenMinor(t, d(11, 1), d(11, 30)))
    }

    @Test
    fun freePlans() {
        val forever = plan(type = PlanType.FREE, amount = 0, regular = 1000)
        assertEquals(Status.FREE, Plans.status(forever, today))
        assertNull(Plans.nextCharge(forever, today))
        assertNull(Plans.nextEvent(forever, today))
        assertEquals(0, Plans.monthlyBillMinor(forever.copy(includeInMonthly = true), today))
        assertEquals(1000L, Plans.savingMinor(forever))

        val student = forever.copy(end = d(11, 8))
        assertEquals(PlanEvent(d(11, 8), EventKind.FREE_ENDS), Plans.reminderDue(student, 7, today))
        assertEquals(Status.ENDED, Plans.status(student, d(11, 9)))
    }

    @Test
    fun lifetimeIsPaidOnce() {
        val p = plan(type = PlanType.LIFETIME, amount = 129900, start = d(3, 1))
        assertEquals(Status.OWNED, Plans.status(p, today))
        assertEquals(1, Plans.paymentsMade(p, today))
        assertEquals(129900, Plans.paidSoFarMinor(p, today))
        assertNull(Plans.nextCharge(p, today))
        assertEquals(0, Plans.monthlyBillMinor(p.copy(includeInMonthly = true), today))
        assertEquals(0, Plans.yearlyMinor(p, today))
        assertEquals(129900, Plans.dueBetweenMinor(p, d(3, 1), d(3, 31)))
        assertEquals(0, Plans.dueBetweenMinor(p, d(4, 1), d(4, 30)))
    }

    @Test
    fun cancelledSubscriptionRunsUntilItsEndDate() {
        val p = plan(start = d(1, 15), end = d(11, 14))
        assertEquals(Status.ENDING, Plans.status(p, today))
        assertNull(Plans.nextCharge(p, today))
        assertEquals(PlanEvent(d(11, 14), EventKind.SUBSCRIPTION_ENDS), Plans.nextEvent(p, today))
        assertEquals(60000, Plans.monthlyBillMinor(p, today))
        assertEquals(Status.ENDED, Plans.status(p, d(11, 15)))
        assertEquals(0, Plans.monthlyBillMinor(p, d(11, 15)))
        assertEquals(10, Plans.paymentsMade(p, d(12, 31)))

        val lastPayment = plan(start = d(1, 15), end = d(12, 1))
        assertEquals(d(11, 15), Plans.nextCharge(lastPayment, today))
        assertEquals(60000, Plans.dueBetweenMinor(lastPayment, d(11, 1), d(11, 30)))
        assertEquals(0, Plans.dueBetweenMinor(lastPayment, d(12, 1), d(12, 31)))
    }

    @Test
    fun dueThisMonthIgnoresTheToggle() {
        val yearly = plan(amount = 149900, period = BillingPeriod.YEARLY, start = d(11, 20, 2025), include = false)
        assertEquals(0, Plans.monthlyBillMinor(yearly, today))
        assertEquals(149900, Plans.dueBetweenMinor(yearly, d(11, 1), d(11, 30)))
    }

    @Test
    fun pausedCountsNothing() {
        val p = plan(active = false)
        assertEquals(Status.PAUSED, Plans.status(p, today))
        assertNull(Plans.nextCharge(p, today))
        assertNull(Plans.reminderDue(p, 0, d(11, 15)))
        assertEquals(0, Plans.monthlyBillMinor(p, today))
        assertEquals(0, Plans.dueBetweenMinor(p, d(11, 1), d(11, 30)))
    }

    @Test
    fun chargeReminders() {
        val p = plan(start = d(1, 15))
        assertEquals(PlanEvent(d(11, 15), EventKind.CHARGE), Plans.reminderDue(p, 3, d(11, 12)))
        assertEquals(PlanEvent(d(11, 15), EventKind.CHARGE), Plans.reminderDue(p, 3, d(11, 15)))
        assertNull(Plans.reminderDue(p, 3, d(11, 13)))
        assertNull(Plans.reminderDue(p, -1, d(11, 15)))
    }
}

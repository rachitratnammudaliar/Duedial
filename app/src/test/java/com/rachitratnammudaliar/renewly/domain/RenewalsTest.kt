package com.rachitratnammudaliar.renewly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RenewalsTest {

    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun firstPaymentInTheFutureIsTheNextRenewal() {
        assertEquals(d("2026-12-01"), Renewals.nextRenewal(d("2026-12-01"), BillingCycle.MONTHLY, d("2026-11-01")))
    }

    @Test
    fun renewalTodayCountsAsNext() {
        assertEquals(d("2026-11-15"), Renewals.nextRenewal(d("2026-01-15"), BillingCycle.MONTHLY, d("2026-11-15")))
    }

    @Test
    fun monthlyRollsForward() {
        assertEquals(d("2026-11-15"), Renewals.nextRenewal(d("2026-01-15"), BillingCycle.MONTHLY, d("2026-11-02")))
        assertEquals(d("2026-12-15"), Renewals.nextRenewal(d("2026-01-15"), BillingCycle.MONTHLY, d("2026-11-16")))
    }

    @Test
    fun monthEndDoesNotDrift() {
        // Started 31 Jan: Feb is clamped to the 28th, but March goes back to the 31st.
        val anchor = d("2026-01-31")
        assertEquals(d("2026-02-28"), Renewals.nextRenewal(anchor, BillingCycle.MONTHLY, d("2026-02-01")))
        assertEquals(d("2026-03-31"), Renewals.nextRenewal(anchor, BillingCycle.MONTHLY, d("2026-03-01")))
        assertEquals(d("2026-04-30"), Renewals.nextRenewal(anchor, BillingCycle.MONTHLY, d("2026-04-01")))
    }

    @Test
    fun yearlyFromLeapDay() {
        val anchor = d("2024-02-29")
        assertEquals(d("2026-02-28"), Renewals.nextRenewal(anchor, BillingCycle.YEARLY, d("2026-01-10")))
        assertEquals(d("2028-02-29"), Renewals.nextRenewal(anchor, BillingCycle.YEARLY, d("2027-03-01")))
    }

    @Test
    fun weekly() {
        // 2026-11-02 is a Monday.
        assertEquals(d("2026-11-09"), Renewals.nextRenewal(d("2026-11-02"), BillingCycle.WEEKLY, d("2026-11-03")))
        assertEquals(d("2026-11-09"), Renewals.nextRenewal(d("2026-11-02"), BillingCycle.WEEKLY, d("2026-11-09")))
    }

    @Test
    fun quarterlyAndHalfYearly() {
        assertEquals(d("2026-12-10"), Renewals.nextRenewal(d("2026-03-10"), BillingCycle.QUARTERLY, d("2026-09-11")))
        assertEquals(d("2027-03-10"), Renewals.nextRenewal(d("2026-03-10"), BillingCycle.HALF_YEARLY, d("2026-09-11")))
    }

    @Test
    fun longHistoryStaysCorrect() {
        val anchor = d("2010-05-31")
        val today = d("2026-11-01")
        val next = Renewals.nextRenewal(anchor, BillingCycle.MONTHLY, today)
        assertEquals(d("2026-11-30"), next)
    }

    @Test
    fun monthlyEquivalent() {
        assertEquals(64900L, Renewals.monthlyEquivalentMinor(64900L, BillingCycle.MONTHLY))
        assertEquals(12500L, Renewals.monthlyEquivalentMinor(150000L, BillingCycle.YEARLY))
        assertEquals(43333L, Renewals.monthlyEquivalentMinor(10000L, BillingCycle.WEEKLY))
    }

    @Test
    fun relativeLabels() {
        assertEquals("today", Renewals.relativeLabel(0))
        assertEquals("tomorrow", Renewals.relativeLabel(1))
        assertEquals("in 3 days", Renewals.relativeLabel(3))
    }

    @Test
    fun moneyParsing() {
        assertEquals(64900L, Money.parseToMinor("649"))
        assertEquals(19950L, Money.parseToMinor("199.5"))
        assertEquals(19950L, Money.parseToMinor("199,50"))
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("0"))
        assertNull(Money.parseToMinor("1.234"))
        assertNull(Money.parseToMinor("1.2.3"))
        assertEquals("199.5", Money.minorToInput(19950L))
        assertEquals("649", Money.minorToInput(64900L))
    }

    @Test
    fun lastFourDigits() {
        assertTrue(Money.isValidLast4(""))
        assertTrue(Money.isValidLast4("1234"))
        assertFalse(Money.isValidLast4("123"))
        assertFalse(Money.isValidLast4("12a4"))
    }
}

package com.rachitratnammudaliar.renewly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RenewalsTest {

    private fun d(text: String) = LocalDate.parse(text)
    private val monthly = BillingPeriod.MONTHLY

    @Test
    fun firstPaymentInTheFutureIsTheNextRenewal() {
        assertEquals(d("2026-12-01"), Renewals.nextRenewal(d("2026-12-01"), monthly, d("2026-11-01")))
        assertNull(Renewals.previousPayment(d("2026-12-01"), monthly, d("2026-11-01")))
        assertEquals(0L, Renewals.paymentsMade(d("2026-12-01"), monthly, d("2026-11-01")))
    }

    @Test
    fun renewalTodayCountsAsNextAndAsPaid() {
        val anchor = d("2026-01-15")
        val today = d("2026-11-15")
        assertEquals(today, Renewals.nextRenewal(anchor, monthly, today))
        assertEquals(d("2026-10-15"), Renewals.previousPayment(anchor, monthly, today))
        assertEquals(11L, Renewals.paymentsMade(anchor, monthly, today))
    }

    @Test
    fun monthlyRollsForward() {
        val anchor = d("2026-01-15")
        assertEquals(d("2026-11-15"), Renewals.nextRenewal(anchor, monthly, d("2026-11-02")))
        assertEquals(d("2026-12-15"), Renewals.nextRenewal(anchor, monthly, d("2026-11-16")))
        assertEquals(d("2026-11-15"), Renewals.previousPayment(anchor, monthly, d("2026-11-16")))
        assertEquals(11L, Renewals.paymentsMade(anchor, monthly, d("2026-11-16")))
    }

    @Test
    fun monthEndDoesNotDrift() {
        // Started 31 Jan: Feb is clamped to the 28th, but March goes back to the 31st.
        val anchor = d("2026-01-31")
        assertEquals(d("2026-02-28"), Renewals.nextRenewal(anchor, monthly, d("2026-02-01")))
        assertEquals(d("2026-03-31"), Renewals.nextRenewal(anchor, monthly, d("2026-03-01")))
        assertEquals(d("2026-04-30"), Renewals.nextRenewal(anchor, monthly, d("2026-04-01")))
    }

    @Test
    fun yearlyFromLeapDay() {
        val anchor = d("2024-02-29")
        assertEquals(d("2026-02-28"), Renewals.nextRenewal(anchor, BillingPeriod.YEARLY, d("2026-01-10")))
        assertEquals(d("2028-02-29"), Renewals.nextRenewal(anchor, BillingPeriod.YEARLY, d("2027-03-01")))
    }

    @Test
    fun weekly() {
        assertEquals(d("2026-11-09"), Renewals.nextRenewal(d("2026-11-02"), BillingPeriod.WEEKLY, d("2026-11-03")))
        assertEquals(d("2026-11-09"), Renewals.nextRenewal(d("2026-11-02"), BillingPeriod.WEEKLY, d("2026-11-09")))
    }

    @Test
    fun threeAndSixMonths() {
        val anchor = d("2026-03-10")
        assertEquals(d("2026-12-10"), Renewals.nextRenewal(anchor, BillingPeriod.QUARTERLY, d("2026-09-11")))
        assertEquals(d("2026-09-10"), Renewals.previousPayment(anchor, BillingPeriod.QUARTERLY, d("2026-09-11")))
        assertEquals(3L, Renewals.paymentsMade(anchor, BillingPeriod.QUARTERLY, d("2026-09-11")))
        assertEquals(d("2027-03-10"), Renewals.nextRenewal(anchor, BillingPeriod.HALF_YEARLY, d("2026-09-11")))
    }

    @Test
    fun customPeriods() {
        assertEquals(d("2026-11-11"), Renewals.nextRenewal(d("2026-11-01"), BillingPeriod(10, PeriodUnit.DAY), d("2026-11-05")))
        assertEquals(d("2027-01-20"), Renewals.nextRenewal(d("2025-01-20"), BillingPeriod(2, PeriodUnit.YEAR), d("2026-06-01")))
        assertEquals(d("2026-11-16"), Renewals.nextRenewal(d("2026-11-02"), BillingPeriod(2, PeriodUnit.WEEK), d("2026-11-03")))
        assertEquals("Every 10 days", BillingPeriod(10, PeriodUnit.DAY).label)
        assertEquals("Monthly", BillingPeriod.MONTHLY.label)
        assertEquals("Every 3 months", BillingPeriod.QUARTERLY.label)
    }

    @Test
    fun longHistoryStaysCorrect() {
        assertEquals(d("2026-11-30"), Renewals.nextRenewal(d("2010-05-31"), monthly, d("2026-11-01")))
    }

    @Test
    fun chargesInAMonth() {
        val start = d("2026-11-01")
        val end = d("2026-11-30")
        assertEquals(1, Renewals.chargesBetween(d("2026-01-15"), monthly, start, end))
        // Mondays in November 2026: 2, 9, 16, 23, 30.
        assertEquals(5, Renewals.chargesBetween(d("2026-10-26"), BillingPeriod.WEEKLY, start, end))
        assertEquals(0, Renewals.chargesBetween(d("2026-03-10"), BillingPeriod.QUARTERLY, start, end))
        assertEquals(1, Renewals.chargesBetween(d("2026-02-01"), BillingPeriod.QUARTERLY, start, end))
        assertEquals(0, Renewals.chargesBetween(d("2027-01-01"), monthly, start, end))
    }

    @Test
    fun monthlyAndYearlyEquivalents() {
        assertEquals(64900L, Renewals.monthlyEquivalentMinor(64900L, monthly))
        assertEquals(12500L, Renewals.monthlyEquivalentMinor(150000L, BillingPeriod.YEARLY))
        assertEquals(43333L, Renewals.monthlyEquivalentMinor(10000L, BillingPeriod.WEEKLY))
        assertEquals(3000L, Renewals.monthlyEquivalentMinor(9000L, BillingPeriod.QUARTERLY))
        assertEquals(778800L, Renewals.yearlyEquivalentMinor(64900L, monthly))
        assertTrue(BillingPeriod.MONTHLY.isMonthly)
        assertFalse(BillingPeriod.QUARTERLY.isMonthly)
    }

    @Test
    fun relativeLabels() {
        assertEquals("today", Renewals.relativeLabel(0))
        assertEquals("tomorrow", Renewals.relativeLabel(1))
        assertEquals("in 3 days", Renewals.relativeLabel(3))
        assertEquals("in 2 weeks", Renewals.relativeLabel(15))
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
        assertNull(Money.parseToMinor("999999999"))
        assertEquals("199.5", Money.minorToInput(19950L))
        assertEquals("649", Money.minorToInput(64900L))
        assertEquals("1000", Money.minorToInput(100000L))
    }

    @Test
    fun lastFourDigits() {
        assertTrue(Money.isValidLast4(""))
        assertTrue(Money.isValidLast4("1234"))
        assertFalse(Money.isValidLast4("123"))
        assertFalse(Money.isValidLast4("12a4"))
    }

    @Test
    fun brandsAreRecognised() {
        assertEquals(0xFFE50914, Brands.match("Netflix Premium")?.color)
        assertEquals(Category.MUSIC, Brands.match("spotify")?.category)
        assertEquals(Category.ENTERTAINMENT, Brands.match("JioCinema")?.category)
        assertNull(Brands.match("My landlord"))
        assertEquals(Brands.colorFor("Something"), Brands.colorFor("something "))
        assertEquals("N", Brands.initial(" netflix"))
        assertEquals("?", Brands.initial("  "))
    }
}

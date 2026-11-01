package com.rachitratnammudaliar.duedial.backup

import com.rachitratnammudaliar.duedial.data.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {

    @Test
    fun roundTripKeepsEverythingButTheIcon() {
        val original = Subscription(
            id = 7,
            name = "Netflix",
            nickname = "Family",
            amountMinor = 64900,
            currency = "INR",
            periodCount = 3,
            periodUnit = "MONTH",
            firstPaymentEpochDay = 20_400,
            category = "ENTERTAINMENT",
            cardNickname = "HDFC",
            cardLast4 = "4821",
            remindDaysBefore = 3,
            notes = "Shared with family",
            iconFile = "abc.png",
            active = false,
            createdAt = 1234,
        )
        val restored = Backup.decode(Backup.encode(listOf(original))).single()
        assertEquals(original.copy(id = 0, iconFile = ""), restored)
    }

    @Test
    fun readsOlderBackupsWithACycleName() {
        val json = """
            {"format":"duedial-backup","version":1,"subscriptions":[
              {"name":"Gym","amountMinor":9000,"currency":"EUR","cycle":"QUARTERLY","firstPaymentEpochDay":20000}
            ]}
        """.trimIndent()
        val s = Backup.decode(json).single()
        assertEquals(3, s.periodCount)
        assertEquals("MONTH", s.periodUnit)
        assertEquals("OTHER", s.category)
    }

    @Test
    fun skipsBrokenEntriesAndCleansValues() {
        val json = """
            {"format":"duedial-backup","subscriptions":[
              {"name":"","amountMinor":100,"firstPaymentEpochDay":1},
              {"name":"Bad amount","amountMinor":0,"firstPaymentEpochDay":1},
              {"name":"No date","amountMinor":100},
              {"name":"Ok","amountMinor":100,"firstPaymentEpochDay":1,"cardLast4":"12-34","periodCount":0,"periodUnit":"NOPE","remindDaysBefore":99}
            ]}
        """.trimIndent()
        val s = Backup.decode(json).single()
        assertEquals("Ok", s.name)
        assertEquals("1234", s.cardLast4)
        assertEquals(1, s.periodCount)
        assertEquals("MONTH", s.periodUnit)
        assertEquals(30, s.remindDaysBefore)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"hello":"world"}""") }
    }

    @Test
    fun keepsPlanDetails() {
        val free = Subscription(
            name = "GitHub Copilot", nickname = "Student", amountMinor = 0, currency = "USD",
            periodCount = 1, periodUnit = "MONTH", firstPaymentEpochDay = 20_000, category = "SOFTWARE",
            cardNickname = "", cardLast4 = "", remindDaysBefore = 7, notes = "", iconFile = "",
            active = true, createdAt = 5, planType = "FREE", endEpochDay = 20_300,
            includeInMonthly = false, splitCount = 1, regularAmountMinor = 1000,
        )
        val shared = free.copy(
            name = "Spotify Duo", amountMinor = 23900, currency = "INR", planType = "RECURRING",
            endEpochDay = null, includeInMonthly = true, splitCount = 2, regularAmountMinor = null,
        )
        val trial = shared.copy(name = "Hotstar", planType = "TRIAL", endEpochDay = 19_999, splitCount = 1)
        val yearly = shared.copy(name = "Prime", periodUnit = "YEAR", includeInMonthly = true, splitCount = 1)
        val list = listOf(free, shared, trial, yearly)
        assertEquals(list, Backup.decode(Backup.encode(list)))
    }

    @Test
    fun olderBackupsGetSensibleDefaults() {
        val json = """
            {"format":"duedial-backup","version":2,"subscriptions":[
              {"name":"Netflix","amountMinor":64900,"periodCount":1,"periodUnit":"MONTH","firstPaymentEpochDay":20000},
              {"name":"Prime","amountMinor":149900,"periodCount":1,"periodUnit":"YEAR","firstPaymentEpochDay":20000}
            ]}
        """.trimIndent()
        val (netflix, prime) = Backup.decode(json)
        assertEquals("RECURRING", netflix.planType)
        assertEquals(true, netflix.includeInMonthly)
        assertEquals(false, prime.includeInMonthly)
        assertEquals(null, prime.endEpochDay)
        assertEquals(1, prime.splitCount)
    }

    @Test
    fun freePlansMayHaveNoPriceButPaidOnesMust() {
        val json = """
            {"format":"duedial-backup","subscriptions":[
              {"name":"Free","amountMinor":0,"planType":"FREE","firstPaymentEpochDay":1},
              {"name":"Paid","amountMinor":0,"planType":"LIFETIME","firstPaymentEpochDay":1}
            ]}
        """.trimIndent()
        assertEquals("Free", Backup.decode(json).single().name)
    }

    @Test
    fun readsBackupsFromBeforeTheRename() {
        val json = """{"format":"renewly-backup","version":3,"subscriptions":[{"name":"Netflix","amountMinor":64900,"firstPaymentEpochDay":20000}]}"""
        assertEquals("Netflix", Backup.decode(json).single().name)
    }
}

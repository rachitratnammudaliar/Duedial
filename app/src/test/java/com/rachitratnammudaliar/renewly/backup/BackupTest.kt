package com.rachitratnammudaliar.renewly.backup

import com.rachitratnammudaliar.renewly.data.Subscription
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
            {"format":"renewly-backup","version":1,"subscriptions":[
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
            {"format":"renewly-backup","subscriptions":[
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
}

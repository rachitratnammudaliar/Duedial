package com.rachitratnammudaliar.renewly.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Data saved by version 1.0 must survive the update to 1.1 unchanged. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @Test
    fun version1DataIsKept() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL(
                "CREATE TABLE subscriptions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "nickname TEXT NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, " +
                    "periodCount INTEGER NOT NULL, periodUnit TEXT NOT NULL, firstPaymentEpochDay INTEGER NOT NULL, " +
                    "category TEXT NOT NULL, cardNickname TEXT NOT NULL, cardLast4 TEXT NOT NULL, " +
                    "remindDaysBefore INTEGER NOT NULL, notes TEXT NOT NULL, iconFile TEXT NOT NULL, " +
                    "active INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
            )
            fun insert(name: String, count: Int, unit: String) = db.execSQL(
                "INSERT INTO subscriptions (name, nickname, amountMinor, currency, periodCount, periodUnit, " +
                    "firstPaymentEpochDay, category, cardNickname, cardLast4, remindDaysBefore, notes, iconFile, active, createdAt) " +
                    "VALUES ('$name', '', 64900, 'INR', $count, '$unit', 20000, 'OTHER', 'HDFC', '4821', 1, '', '', 1, 1)",
            )
            insert("Netflix", 1, "MONTH")
            insert("Gym", 3, "MONTH")
            insert("Prime", 1, "YEAR")
            insert("Milk", 1, "WEEK")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val all = runBlocking { room.subscriptions().getAll() }.associateBy { it.name }
            assertEquals(4, all.size)
            val netflix = all.getValue("Netflix")
            assertEquals(64900L, netflix.amountMinor)
            assertEquals("4821", netflix.cardLast4)
            assertEquals("RECURRING", netflix.planType)
            assertNull(netflix.endEpochDay)
            assertEquals(1, netflix.splitCount)
            assertNull(netflix.regularAmountMinor)
            assertEquals(true, netflix.includeInMonthly)
            assertEquals(true, all.getValue("Milk").includeInMonthly)
            assertEquals(false, all.getValue("Gym").includeInMonthly)
            assertEquals(false, all.getValue("Prime").includeInMonthly)
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }
}

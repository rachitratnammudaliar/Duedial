package com.rachitratnammudaliar.renewly.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.rachitratnammudaliar.renewly.domain.BillingCycle
import com.rachitratnammudaliar.renewly.domain.Renewals
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * One subscription. Only a card nickname and its last four digits are stored,
 * never a full card number.
 */
@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String = "",
    val amountMinor: Long,
    val currency: String = "INR",
    val cycle: String = BillingCycle.MONTHLY.name,
    val firstPaymentEpochDay: Long,
    val cardNickname: String = "",
    val cardLast4: String = "",
    val remindDaysBefore: Int = 1,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

fun Subscription.billingCycle(): BillingCycle = BillingCycle.fromName(cycle)

fun Subscription.firstPaymentDate(): LocalDate = LocalDate.ofEpochDay(firstPaymentEpochDay)

fun Subscription.nextRenewal(today: LocalDate = LocalDate.now()): LocalDate =
    Renewals.nextRenewal(firstPaymentDate(), billingCycle(), today)

/** "HDFC •••• 1234", "HDFC", "•••• 1234" or "". */
fun Subscription.cardLabel(): String = when {
    cardNickname.isNotBlank() && cardLast4.isNotBlank() -> "$cardNickname •••• $cardLast4"
    cardNickname.isNotBlank() -> cardNickname
    cardLast4.isNotBlank() -> "•••• $cardLast4"
    else -> ""
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: Long): Subscription?

    @Insert
    suspend fun insert(subscription: Subscription): Long

    @Insert
    suspend fun insertAll(subscriptions: List<Subscription>)

    @Update
    suspend fun update(subscription: Subscription)

    @Delete
    suspend fun delete(subscription: Subscription)
}

@Database(entities = [Subscription::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptions(): SubscriptionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "renewly.db",
                ).build().also { instance = it }
            }
    }
}

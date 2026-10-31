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
import com.rachitratnammudaliar.renewly.domain.BillingPeriod
import com.rachitratnammudaliar.renewly.domain.Category
import com.rachitratnammudaliar.renewly.domain.PeriodUnit
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
    val periodCount: Int = 1,
    val periodUnit: String = PeriodUnit.MONTH.name,
    val firstPaymentEpochDay: Long,
    val category: String = Category.OTHER.name,
    val cardNickname: String = "",
    val cardLast4: String = "",
    val remindDaysBefore: Int = 1,
    val notes: String = "",
    /** File name of a custom icon in the app's private storage, or empty. */
    val iconFile: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

fun Subscription.period(): BillingPeriod = BillingPeriod.of(periodCount, periodUnit)

fun Subscription.categoryValue(): Category = Category.fromName(category)

fun Subscription.firstPaymentDate(): LocalDate = LocalDate.ofEpochDay(firstPaymentEpochDay)

fun Subscription.nextRenewal(today: LocalDate): LocalDate =
    Renewals.nextRenewal(firstPaymentDate(), period(), today)

fun Subscription.previousPayment(today: LocalDate): LocalDate? =
    Renewals.previousPayment(firstPaymentDate(), period(), today)

fun Subscription.paymentsMade(today: LocalDate): Long =
    Renewals.paymentsMade(firstPaymentDate(), period(), today)

fun Subscription.monthlyMinor(): Long = Renewals.monthlyEquivalentMinor(amountMinor, period())

fun Subscription.yearlyMinor(): Long = Renewals.yearlyEquivalentMinor(amountMinor, period())

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

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observe(id: Long): Flow<Subscription?>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun count(): Int

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

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rachitratnammudaliar.renewly.domain.BillingPeriod
import com.rachitratnammudaliar.renewly.domain.Category
import com.rachitratnammudaliar.renewly.domain.PeriodUnit
import com.rachitratnammudaliar.renewly.domain.Plan
import com.rachitratnammudaliar.renewly.domain.PlanType
import com.rachitratnammudaliar.renewly.domain.Plans
import com.rachitratnammudaliar.renewly.domain.Status
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
    // Added in database version 2 (app 1.1.0).
    val planType: String = PlanType.RECURRING.name,
    /** Last day it's active (cancelled, fixed-term, or free until). Null if it continues. */
    val endEpochDay: Long? = null,
    val includeInMonthly: Boolean = true,
    /** Number of people sharing the cost; 1 means not shared. */
    val splitCount: Int = 1,
    /** The normal price, to show savings from a discount or free offer. */
    val regularAmountMinor: Long? = null,
)

fun Subscription.planTypeValue(): PlanType = PlanType.fromName(planType)

fun Subscription.endDate(): LocalDate? = endEpochDay?.let { LocalDate.ofEpochDay(it) }

fun Subscription.toPlan(): Plan = Plan(
    type = planTypeValue(),
    amountMinor = amountMinor,
    period = period(),
    start = firstPaymentDate(),
    end = endDate(),
    split = splitCount.coerceAtLeast(1),
    includeInMonthly = includeInMonthly,
    active = active,
    regularAmountMinor = regularAmountMinor,
)

fun Subscription.status(today: LocalDate): Status = Plans.status(toPlan(), today)

fun Subscription.nextCharge(today: LocalDate): LocalDate? = Plans.nextCharge(toPlan(), today)

fun Subscription.previousCharge(today: LocalDate): LocalDate? = Plans.previousCharge(toPlan(), today)

fun Subscription.paymentsMade(today: LocalDate): Long = Plans.paymentsMade(toPlan(), today)

fun Subscription.shareMinor(): Long = Plans.shareMinor(toPlan())

fun Subscription.monthlyBillMinor(today: LocalDate): Long = Plans.monthlyBillMinor(toPlan(), today)

fun Subscription.yearlyMinor(today: LocalDate): Long = Plans.yearlyMinor(toPlan(), today)

fun Subscription.period(): BillingPeriod = BillingPeriod.of(periodCount, periodUnit)

fun Subscription.categoryValue(): Category = Category.fromName(category)

fun Subscription.firstPaymentDate(): LocalDate = LocalDate.ofEpochDay(firstPaymentEpochDay)

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

/**
 * Version 1 -> 2 (app 1.0.0 -> 1.1.0): plan types, end dates, monthly-bill toggle, cost splitting
 * and regular price. Existing subscriptions stay paid subscriptions; weekly and monthly ones keep
 * counting in the monthly bill, longer ones don't (the same as 1.0.0's default view).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN planType TEXT NOT NULL DEFAULT 'RECURRING'")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN endEpochDay INTEGER")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN includeInMonthly INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN splitCount INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN regularAmountMinor INTEGER")
        db.execSQL(
            "UPDATE subscriptions SET includeInMonthly = CASE " +
                "WHEN periodUnit IN ('DAY', 'WEEK') OR (periodUnit = 'MONTH' AND periodCount = 1) THEN 1 ELSE 0 END",
        )
    }
}

@Database(entities = [Subscription::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptions(): SubscriptionDao

    companion object {
        const val DATABASE_NAME = "renewly.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

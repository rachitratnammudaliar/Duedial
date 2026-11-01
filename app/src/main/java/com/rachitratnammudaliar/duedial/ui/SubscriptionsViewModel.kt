package com.rachitratnammudaliar.duedial.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rachitratnammudaliar.duedial.backup.Backup
import com.rachitratnammudaliar.duedial.data.AppDatabase
import com.rachitratnammudaliar.duedial.data.IconStore
import com.rachitratnammudaliar.duedial.data.Prefs
import com.rachitratnammudaliar.duedial.data.SortMode
import com.rachitratnammudaliar.duedial.data.Subscription
import com.rachitratnammudaliar.duedial.data.TotalMode
import com.rachitratnammudaliar.duedial.domain.BillingPeriod
import com.rachitratnammudaliar.duedial.domain.Category
import com.rachitratnammudaliar.duedial.domain.PeriodUnit
import com.rachitratnammudaliar.duedial.domain.PlanType
import com.rachitratnammudaliar.duedial.domain.Plans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.subscriptions()
    private val prefs = Prefs(app)

    /** Null until the database has loaded, so the screen doesn't flash "empty". */
    val subscriptions: StateFlow<List<Subscription>?> = dao.observeAll()
        .map<List<Subscription>, List<Subscription>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _totalMode = MutableStateFlow(prefs.totalMode)
    val totalMode: StateFlow<TotalMode> = _totalMode.asStateFlow()

    private val _sortMode = MutableStateFlow(prefs.sortMode)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setTotalMode(mode: TotalMode) {
        prefs.totalMode = mode
        _totalMode.value = mode
    }

    fun setSortMode(mode: SortMode) {
        prefs.sortMode = mode
        _sortMode.value = mode
    }

    fun observe(id: Long): Flow<Subscription?> = dao.observe(id)

    suspend fun get(id: Long): Subscription? = dao.get(id)

    /** Saves and removes an icon file that's no longer used. */
    fun save(subscription: Subscription, replacedIcon: String = "") {
        viewModelScope.launch {
            if (subscription.id == 0L) dao.insert(subscription) else dao.update(subscription)
            if (replacedIcon.isNotBlank() && replacedIcon != subscription.iconFile) {
                withContext(Dispatchers.IO) { IconStore.delete(getApplication(), replacedIcon) }
            }
        }
    }

    fun delete(subscription: Subscription) {
        viewModelScope.launch {
            dao.delete(subscription)
            withContext(Dispatchers.IO) { IconStore.delete(getApplication(), subscription.iconFile) }
        }
    }

    suspend fun saveIcon(uri: Uri): String = withContext(Dispatchers.IO) {
        IconStore.save(getApplication(), uri)
    }

    /** Writes a JSON backup to [uri]. Returns how many subscriptions were saved. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        val resolver = getApplication<Application>().contentResolver
        val stream = resolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file for writing.")
        stream.bufferedWriter().use { it.write(Backup.encode(all)) }
        all.size
    }

    /** Adds the subscriptions from a backup at [uri]. Returns how many were added. */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val stream = resolver.openInputStream(uri) ?: error("Couldn't open the file.")
        val text = stream.bufferedReader().use { it.readText() }
        val items = Backup.decode(text)
        db.withTransaction { dao.insertAll(items) }
        items.size
    }

    /** Sample data for screenshots. Only used by debug builds, and only when the app is empty. */
    fun seedDemo() {
        viewModelScope.launch {
            if (dao.count() > 0) return@launch
            val t = LocalDate.now()
            fun sub(
                name: String, amount: Long, currency: String, count: Int, unit: PeriodUnit, first: LocalDate,
                category: Category, card: String = "", last4: String = "", nickname: String = "", remind: Int = 1,
                type: PlanType = PlanType.RECURRING, end: LocalDate? = null, include: Boolean? = null,
                split: Int = 1, regular: Long? = null,
            ) = Subscription(
                name = name, nickname = nickname, amountMinor = amount, currency = currency,
                periodCount = count, periodUnit = unit.name, firstPaymentEpochDay = first.toEpochDay(),
                category = category.name, cardNickname = card, cardLast4 = last4, remindDaysBefore = remind,
                planType = type.name, endEpochDay = end?.toEpochDay(),
                includeInMonthly = include ?: Plans.defaultIncludeInMonthly(type, BillingPeriod(count, unit)),
                splitCount = split, regularAmountMinor = regular,
            )
            dao.insertAll(
                listOf(
                    sub("Netflix", 64900, "INR", 1, PeriodUnit.MONTH, t.minusMonths(7).plusDays(2), Category.ENTERTAINMENT, "HDFC Visa", "4821", "Premium, family"),
                    sub("Spotify", 23900, "INR", 1, PeriodUnit.MONTH, t.minusMonths(14).plusDays(9), Category.MUSIC, "HDFC Visa", "4821", "Duo", split = 2),
                    sub("YouTube Premium", 14900, "INR", 1, PeriodUnit.MONTH, t.minusMonths(3).plusDays(1), Category.ENTERTAINMENT, "UPI"),
                    sub("Amazon Prime", 149900, "INR", 1, PeriodUnit.YEAR, t.minusMonths(10), Category.ENTERTAINMENT, "ICICI Amazon Pay", "1106", remind = 7, include = true),
                    sub("Disney+ Hotstar", 29900, "INR", 1, PeriodUnit.MONTH, t.plusDays(2), Category.ENTERTAINMENT, "HDFC Visa", "4821", type = PlanType.TRIAL, remind = 2),
                    sub("GitHub Copilot", 0, "USD", 1, PeriodUnit.MONTH, t.minusMonths(5), Category.SOFTWARE, nickname = "Student pack", type = PlanType.FREE, regular = 1000, end = t.plusMonths(7)),
                    sub("Notion", 0, "USD", 1, PeriodUnit.MONTH, t.minusMonths(9), Category.SOFTWARE, nickname = "Education plan", type = PlanType.FREE, regular = 1000),
                    sub("Procreate", 129900, "INR", 1, PeriodUnit.MONTH, t.minusMonths(18), Category.SOFTWARE, "UPI", type = PlanType.LIFETIME, remind = -1),
                    sub("ChatGPT Plus", 2000, "USD", 1, PeriodUnit.MONTH, t.minusMonths(2).plusDays(18), Category.SOFTWARE, "Revolut", "7310"),
                    sub("iCloud+", 7500, "INR", 1, PeriodUnit.MONTH, t.minusMonths(20).plusDays(25), Category.CLOUD, "Apple balance"),
                    sub("Gym membership", 9000, "EUR", 3, PeriodUnit.MONTH, t.minusMonths(2).plusDays(12), Category.FITNESS, "Swedbank", "2290", "Kaunas", remind = 3),
                    sub("Telia mobile", 1299, "EUR", 1, PeriodUnit.MONTH, t.minusMonths(1).plusDays(5), Category.PHONE, "Swedbank", "2290"),
                ),
            )
        }
    }
}

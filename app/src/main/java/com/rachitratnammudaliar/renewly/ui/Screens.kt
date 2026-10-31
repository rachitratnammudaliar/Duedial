@file:OptIn(ExperimentalMaterial3Api::class)

package com.rachitratnammudaliar.renewly.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rachitratnammudaliar.renewly.data.SortMode
import com.rachitratnammudaliar.renewly.data.Subscription
import com.rachitratnammudaliar.renewly.data.TotalMode
import com.rachitratnammudaliar.renewly.data.cardLabel
import com.rachitratnammudaliar.renewly.data.categoryValue
import com.rachitratnammudaliar.renewly.data.firstPaymentDate
import com.rachitratnammudaliar.renewly.data.monthlyMinor
import com.rachitratnammudaliar.renewly.data.nextRenewal
import com.rachitratnammudaliar.renewly.data.paymentsMade
import com.rachitratnammudaliar.renewly.data.period
import com.rachitratnammudaliar.renewly.data.previousPayment
import com.rachitratnammudaliar.renewly.data.yearlyMinor
import com.rachitratnammudaliar.renewly.domain.BillingPeriod
import com.rachitratnammudaliar.renewly.domain.Brands
import com.rachitratnammudaliar.renewly.domain.Category
import com.rachitratnammudaliar.renewly.domain.Money
import com.rachitratnammudaliar.renewly.domain.PeriodUnit
import com.rachitratnammudaliar.renewly.domain.Renewals
import com.rachitratnammudaliar.renewly.reminders.Reminders
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private val reminderOptions = listOf(
    -1 to "Off",
    0 to "On the day",
    1 to "1 day before",
    3 to "3 days before",
    7 to "1 week before",
)

private fun reminderLabel(days: Int): String =
    reminderOptions.firstOrNull { it.first == days }?.second ?: "$days days before"

/* =============================================================
   Navigation: four screens, no extra library
   ============================================================= */

/**
 * @param startScreen "list", "settings", "add", "detail:ID" or "edit:ID". Only debug builds pass
 * anything other than "list" (used to take screenshots).
 */
@Composable
fun RenewlyRoot(startScreen: String = "list", demo: Boolean = false, vm: SubscriptionsViewModel = viewModel()) {
    val start = remember(startScreen) {
        val parts = startScreen.split(":")
        parts[0] to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }
    var route by rememberSaveable { mutableStateOf(if (start.first == "add") "edit" else start.first) }
    var currentId by rememberSaveable { mutableLongStateOf(start.second) }

    // "Today" refreshes when the app comes back to the screen and at midnight.
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(today) {
        val now = LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, midnight).toMillis() + 1_000)
        today = LocalDate.now()
    }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (demo) vm.seedDemo()
        if (!demo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = route != "list") {
        route = if (route == "edit" && currentId != 0L) "detail" else "list"
    }

    when (route) {
        "detail" -> DetailScreen(
            vm = vm,
            id = currentId,
            today = today,
            onBack = { route = "list" },
            onEdit = { route = "edit" },
        )
        "edit" -> EditScreen(
            vm = vm,
            id = currentId,
            today = today,
            onDone = { savedExisting -> route = if (savedExisting) "detail" else "list" },
            onDeleted = { route = "list" },
        )
        "settings" -> SettingsScreen(vm = vm, onBack = { route = "list" })
        else -> ListScreen(
            vm = vm,
            today = today,
            onAdd = { currentId = 0L; route = "edit" },
            onOpen = { currentId = it; route = "detail" },
            onSettings = { route = "settings" },
        )
    }
}

/* =============================================================
   Home: summary + list
   ============================================================= */

@Composable
private fun ListScreen(
    vm: SubscriptionsViewModel,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val subs by vm.subscriptions.collectAsStateWithLifecycle()
    val totalMode by vm.totalMode.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Renewly", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add subscription")
            }
        },
    ) { padding ->
        val list = subs
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Modifier.padding(padding), onAdd)
            else -> {
                val categories = list.map { it.categoryValue() }.distinct().sortedBy { it.ordinal }
                val activeFilter = categories.firstOrNull { it.name == filter }
                val shown = list
                    .filter { activeFilter == null || it.categoryValue() == activeFilter }
                    .sortedWith(
                        when (sortMode) {
                            SortMode.NEXT -> compareBy<Subscription>({ !it.active }, { it.nextRenewal(today) }, { it.name.lowercase() })
                            SortMode.PRICE -> compareBy<Subscription>({ !it.active }, { -it.monthlyMinor() }, { it.name.lowercase() })
                            SortMode.NAME -> compareBy<Subscription>({ !it.active }, { it.name.lowercase() })
                        },
                    )

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "summary") {
                        SummaryCard(list, today, totalMode, onModeChange = vm::setTotalMode)
                    }
                    item(key = "controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ChipRow(
                                options = SortMode.entries,
                                selected = sortMode,
                                label = { "Sort: ${it.label}" },
                                onSelect = vm::setSortMode,
                            )
                            if (categories.size > 1) {
                                ChipRow(
                                    options = listOf<Category?>(null) + categories,
                                    selected = activeFilter,
                                    label = { it?.label ?: "All" },
                                    onSelect = { filter = it?.name ?: "" },
                                )
                            }
                        }
                    }
                    items(shown, key = { it.id }) { s ->
                        SubscriptionRow(s, today, onClick = { onOpen(s.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No subscriptions yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add Netflix, Spotify or anything you pay for regularly. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAdd) { Text("Add your first subscription") }
    }
}

/** Totals per currency for the chosen mode. Currencies are never added together. */
private fun totals(all: List<Subscription>, today: LocalDate, mode: TotalMode): Map<String, Long> {
    val active = all.filter { it.active }
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    return when (mode) {
        TotalMode.MONTHLY_ONLY -> active.filter { it.period().isMonthly }
            .groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        TotalMode.ALL_AVERAGED -> active
            .groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.monthlyMinor() } }
        TotalMode.THIS_MONTH -> active
            .groupBy { it.currency }
            .mapValues { (_, l) ->
                l.sumOf { s -> s.amountMinor * Renewals.chargesBetween(s.firstPaymentDate(), s.period(), monthStart, monthEnd) }
            }
    }.filterValues { it > 0 }
}

@Composable
private fun SummaryCard(all: List<Subscription>, today: LocalDate, mode: TotalMode, onModeChange: (TotalMode) -> Unit) {
    val result = totals(all, today, mode)
    val active = all.filter { it.active }
    val counted = when (mode) {
        TotalMode.MONTHLY_ONLY -> active.count { it.period().isMonthly }
        else -> active.size
    }
    val next = active.minByOrNull { it.nextRenewal(today) }
    val onCard = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                if (mode == TotalMode.THIS_MONTH) "Due in ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }}" else "Per month",
                style = MaterialTheme.typography.labelLarge,
                color = onCard,
            )
            if (result.isEmpty()) {
                Text("Nothing here yet", style = MaterialTheme.typography.headlineSmall, color = onCard)
            } else {
                result.entries.sortedByDescending { it.key == "INR" }.forEach { (currency, minor) ->
                    Text(
                        Money.format(minor, currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onCard,
                    )
                }
            }
            Text(
                mode.description + " · $counted counted",
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
            )
            if (next != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Next: ${next.name}, ${Renewals.relativeLabel(Renewals.daysUntil(today, next.nextRenewal(today)))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = onCard,
                )
            }
            Spacer(Modifier.height(8.dp))
            ChipRow(
                options = TotalMode.entries,
                selected = mode,
                label = { it.label },
                onSelect = onModeChange,
            )
        }
    }
}

@Composable
private fun SubscriptionRow(s: Subscription, today: LocalDate, onClick: () -> Unit) {
    val next = s.nextRenewal(today)
    val days = Renewals.daysUntil(today, next)
    val soon = s.active && days <= 3

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SubscriptionBadge(s.name, s.iconFile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (s.active) "${next.format(dateFormat)} · ${Renewals.relativeLabel(days)}" else "Paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (soon) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                val card = s.cardLabel()
                if (card.isNotEmpty()) {
                    Text(
                        card,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(s.amountMinor, s.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    s.period().label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/* =============================================================
   Detail
   ============================================================= */

@Composable
private fun DetailScreen(vm: SubscriptionsViewModel, id: Long, today: LocalDate, onBack: () -> Unit, onEdit: () -> Unit) {
    val flow = remember(id) { vm.observe(id) }
    val sub by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sub?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = sub != null) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = onEdit, enabled = sub != null) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        val s = sub ?: return@Scaffold
        val period = s.period()
        val next = s.nextRenewal(today)
        val previous = s.previousPayment(today)
        val paid = s.paymentsMade(today)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscriptionBadge(s.name, s.iconFile, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Money.format(s.amountMinor, s.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(period.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val sub2 = listOf(s.nickname, s.categoryValue().label).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(sub2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    if (s.active) {
                        InfoRow("Next payment", next.format(dateFormat), Renewals.relativeLabel(Renewals.daysUntil(today, next)))
                    } else {
                        InfoRow("Status", "Paused", "No reminders while paused")
                    }
                    HorizontalDivider()
                    InfoRow("Last payment", previous?.format(dateFormat) ?: "Not yet")
                    HorizontalDivider()
                    InfoRow("First payment", s.firstPaymentDate().format(dateFormat))
                    HorizontalDivider()
                    InfoRow(
                        "Paid so far",
                        if (paid == 0L) "Nothing yet" else Money.format(s.amountMinor * paid, s.currency),
                        if (paid == 0L) "" else "$paid payment${if (paid == 1L) "" else "s"}, at today's price",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    InfoRow("Per month", Money.format(s.monthlyMinor(), s.currency), if (period.isMonthly) "" else "average")
                    HorizontalDivider()
                    InfoRow("Per year", Money.format(s.yearlyMinor(), s.currency))
                    HorizontalDivider()
                    InfoRow("Card", s.cardLabel().ifEmpty { "Not set" })
                    HorizontalDivider()
                    InfoRow("Reminder", reminderLabel(s.remindDaysBefore))
                }
            }
            if (s.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(s.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${s.name}?") },
                text = { Text("This removes it from Renewly. It can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.delete(s)
                        onBack()
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

/* =============================================================
   Add / edit
   ============================================================= */

/** A supporting-text slot that only exists when there's an error to show. */
private fun errorText(show: Boolean, message: String): (@Composable () -> Unit)? =
    if (show) {
        { Text(message) }
    } else {
        null
    }

@Composable
private fun EditScreen(
    vm: SubscriptionsViewModel,
    id: Long,
    today: LocalDate,
    onDone: (savedExisting: Boolean) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isNew = id == 0L

    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    var periodCount by rememberSaveable { mutableIntStateOf(1) }
    var periodUnit by rememberSaveable { mutableStateOf(PeriodUnit.MONTH.name) }
    var customPeriod by rememberSaveable { mutableStateOf(false) }
    var customCountText by rememberSaveable { mutableStateOf("2") }
    var firstDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var category by rememberSaveable { mutableStateOf(Category.OTHER.name) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var cardNickname by rememberSaveable { mutableStateOf("") }
    var cardLast4 by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableIntStateOf(1) }
    var notes by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var iconFile by rememberSaveable { mutableStateOf("") }
    var originalIcon by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var existing by remember { mutableStateOf<Subscription?>(null) }

    LaunchedEffect(id) {
        if (!isNew) {
            val s = vm.get(id)
            existing = s
            if (s != null && !loaded) {
                name = s.name
                nickname = s.nickname
                amount = Money.minorToInput(s.amountMinor)
                currency = s.currency
                periodCount = s.periodCount
                periodUnit = s.periodUnit
                customPeriod = s.period() !in BillingPeriod.presets
                customCountText = s.periodCount.toString()
                firstDay = s.firstPaymentEpochDay
                category = s.category
                categoryTouched = true
                cardNickname = s.cardNickname
                cardLast4 = s.cardLast4
                remind = s.remindDaysBefore
                notes = s.notes
                active = s.active
                iconFile = s.iconFile
                originalIcon = s.iconFile
                loaded = true
            }
        }
    }

    // Leaving without saving: throw away an icon that was picked but never saved.
    fun leave(saved: Boolean) {
        if (!saved && iconFile.isNotBlank() && iconFile != originalIcon) {
            com.rachitratnammudaliar.renewly.data.IconStore.delete(context, iconFile)
        }
        onDone(saved && !isNew)
    }
    BackHandler { leave(saved = false) }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val newFile = vm.saveIcon(uri)
                    if (iconFile.isNotBlank() && iconFile != originalIcon) {
                        com.rachitratnammudaliar.renewly.data.IconStore.delete(context, iconFile)
                    }
                    iconFile = newFile
                } catch (e: Exception) {
                    snackbar.showSnackbar("That image couldn't be used. Try another one.")
                }
            }
        }
    }

    val countFromText = customCountText.toIntOrNull()
    val effectiveCount = if (customPeriod) countFromText ?: 0 else periodCount
    val periodError = customPeriod && (countFromText == null || countFromText !in 1..999)
    val amountMinor = Money.parseToMinor(amount)
    val nameError = name.isBlank()
    val amountError = amountMinor == null
    val last4Error = !Money.isValidLast4(cardLast4)

    fun save() {
        if (nameError || amountError || last4Error || periodError || amountMinor == null) {
            showErrors = true
            scope.launch { snackbar.showSnackbar("Check the highlighted fields") }
            return
        }
        vm.save(
            Subscription(
                id = id,
                name = name.trim(),
                nickname = nickname.trim(),
                amountMinor = amountMinor,
                currency = currency,
                periodCount = effectiveCount,
                periodUnit = periodUnit,
                firstPaymentEpochDay = firstDay,
                category = category,
                cardNickname = cardNickname.trim(),
                cardLast4 = cardLast4,
                remindDaysBefore = remind,
                notes = notes.trim(),
                iconFile = iconFile,
                active = active,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
            replacedIcon = originalIcon,
        )
        leave(saved = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New subscription" else "Edit") },
                navigationIcon = {
                    IconButton(onClick = { leave(saved = false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscriptionBadge(name, iconFile, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                OutlinedButton(onClick = {
                    iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (iconFile.isBlank()) "Choose icon" else "Change icon") }
                if (iconFile.isNotBlank()) {
                    TextButton(onClick = {
                        if (iconFile != originalIcon) com.rachitratnammudaliar.renewly.data.IconStore.delete(context, iconFile)
                        iconFile = ""
                    }) { Text("Remove") }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { input ->
                    name = input.take(60)
                    if (isNew && !categoryTouched) {
                        category = (Brands.match(name)?.category ?: Category.OTHER).name
                    }
                },
                label = { Text("Name") },
                placeholder = { Text("Netflix") },
                singleLine = true,
                isError = showErrors && nameError,
                supportingText = errorText(showErrors && nameError, "Enter a name"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(60) },
                label = { Text("Nickname (optional)") },
                placeholder = { Text("Family plan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Fee")
            OutlinedTextField(
                value = amount,
                onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' || it == ',' }.take(12) },
                label = { Text("Amount") },
                placeholder = { Text("649") },
                singleLine = true,
                isError = showErrors && amountError,
                supportingText = errorText(showErrors && amountError, "Enter an amount above zero, up to 2 decimals"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            ChipRow(Money.currencies, selected = currency, label = { it }, onSelect = { currency = it })

            SectionLabel("Billing period")
            val presetSelected = if (customPeriod) null else BillingPeriod.presets.firstOrNull {
                it.count == periodCount && it.unit.name == periodUnit
            }
            ChipRow(
                options = BillingPeriod.presets.map { it as BillingPeriod? } + listOf<BillingPeriod?>(null),
                selected = if (customPeriod) null else presetSelected,
                label = { it?.label ?: "Custom" },
                onSelect = { p ->
                    if (p == null) {
                        customPeriod = true
                    } else {
                        customPeriod = false
                        periodCount = p.count
                        periodUnit = p.unit.name
                    }
                },
            )
            if (customPeriod) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Every", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = customCountText,
                        onValueChange = { t -> customCountText = t.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        isError = showErrors && periodError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(90.dp),
                    )
                }
                ChipRow(
                    options = PeriodUnit.entries,
                    selected = PeriodUnit.fromName(periodUnit),
                    label = { it.plural.replaceFirstChar { c -> c.uppercase() } },
                    onSelect = { periodUnit = it.name },
                )
            }

            OutlinedButton(
                onClick = {
                    val d = LocalDate.ofEpochDay(firstDay)
                    DatePickerDialog(
                        context,
                        { _, y, m, day -> firstDay = LocalDate.of(y, m + 1, day).toEpochDay() },
                        d.year,
                        d.monthValue - 1,
                        d.dayOfMonth,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("First payment: ${LocalDate.ofEpochDay(firstDay).format(dateFormat)}")
            }
            val previewPeriod = if (effectiveCount in 1..999) BillingPeriod(effectiveCount, PeriodUnit.fromName(periodUnit)) else null
            Text(
                if (previewPeriod != null) {
                    val next = Renewals.nextRenewal(LocalDate.ofEpochDay(firstDay), previewPeriod, today)
                    "${previewPeriod.label}. Next payment: ${next.format(dateFormat)}, ${Renewals.relativeLabel(Renewals.daysUntil(today, next))}."
                } else {
                    "Enter how often it repeats (1 to 999)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (previewPeriod == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "On a free trial? Set the first payment to the day the trial ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Category")
            ChipRow(
                options = Category.entries,
                selected = Category.fromName(category),
                label = { it.label },
                onSelect = { category = it.name; categoryTouched = true },
            )

            SectionLabel("Payment method")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cardNickname,
                    onValueChange = { cardNickname = it.take(30) },
                    label = { Text("Card or method") },
                    placeholder = { Text("HDFC Visa, UPI…") },
                    singleLine = true,
                    modifier = Modifier.weight(1.6f),
                )
                OutlinedTextField(
                    value = cardLast4,
                    onValueChange = { input -> cardLast4 = input.filter { it.isDigit() }.take(4) },
                    label = { Text("Last 4") },
                    placeholder = { Text("1234") },
                    singleLine = true,
                    isError = showErrors && last4Error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (showErrors && last4Error) "Enter exactly 4 digits, or leave it empty." else "Only the last 4 digits are stored, never the full card number.",
                style = MaterialTheme.typography.bodySmall,
                color = if (showErrors && last4Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Reminder")
            ChipRow(reminderOptions.map { it.first }, selected = remind, label = { reminderLabel(it) }, onSelect = { remind = it })

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(500) },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Turn off to pause it: no reminders, not counted in totals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = active, onCheckedChange = { active = it })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${name.ifBlank { "subscription" }}?") },
            text = { Text("This removes it from Renewly. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let { vm.delete(it) }
                    if (iconFile.isNotBlank() && iconFile != originalIcon) {
                        com.rachitratnammudaliar.renewly.data.IconStore.delete(context, iconFile)
                    }
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/* =============================================================
   Settings: backup, reminders, Samsung tips, privacy
   ============================================================= */

@Composable
private fun SettingsScreen(vm: SubscriptionsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val count = vm.exportTo(uri)
                    "Backup saved: $count subscriptions"
                } catch (e: Exception) {
                    "Couldn't save the backup: ${e.message ?: "unknown error"}"
                }
                snackbar.showSnackbar(message)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val count = vm.importFrom(uri)
                    "Added $count subscriptions from the backup"
                } catch (e: Exception) {
                    e.message ?: "Couldn't read the backup"
                }
                snackbar.showSnackbar(message)
            }
        }
    }

    fun open(intent: android.content.Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Open your phone's Settings > Apps > Renewly instead") }
        }
    }

    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    var notifyOn by remember { mutableStateOf(Reminders.canNotify(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notifyOn = Reminders.canNotify(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Reminders")
            Text(
                if (notifyOn) {
                    "On. Renewly checks every morning and reminds you before each renewal."
                } else {
                    "Notifications are off, so reminders can't be shown."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!notifyOn) {
                    FilledTonalButton(onClick = { open(Reminders.notificationSettingsIntent(context)) }) {
                        Text("Turn on notifications")
                    }
                }
                OutlinedButton(onClick = {
                    Reminders.showTest(context)
                    scope.launch {
                        snackbar.showSnackbar(if (Reminders.canNotify(context)) "Test reminder sent" else "Notifications are off for Renewly")
                    }
                }) { Text("Send a test") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samsung and other phones", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Battery saving can put apps to sleep and delay reminders. Open Renewly's app settings, " +
                            "tap Battery, and choose Unrestricted. Also make sure Renewly isn't in Sleeping apps.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { open(Reminders.appSettingsIntent(context)) }) { Text("Open app settings") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Backup")
            Text(
                "Save your subscriptions to a file you control, and restore them on a new phone. Custom icons aren't included.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("renewly-backup-${LocalDate.now()}.json") }) {
                    Text("Export")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Import")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Privacy")
            Text(
                "Renewly works completely offline. It has no internet permission, no accounts, no ads and no tracking. " +
                    "Your data is stored only on this phone and is excluded from cloud backups.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Renewly $version · github.com/rachitratnammudaliar/renewly",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

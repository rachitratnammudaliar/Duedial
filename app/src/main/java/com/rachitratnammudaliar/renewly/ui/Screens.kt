@file:OptIn(ExperimentalMaterial3Api::class)

package com.rachitratnammudaliar.renewly.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rachitratnammudaliar.renewly.data.Subscription
import com.rachitratnammudaliar.renewly.data.billingCycle
import com.rachitratnammudaliar.renewly.data.cardLabel
import com.rachitratnammudaliar.renewly.data.nextRenewal
import com.rachitratnammudaliar.renewly.domain.BillingCycle
import com.rachitratnammudaliar.renewly.domain.Money
import com.rachitratnammudaliar.renewly.domain.Renewals
import com.rachitratnammudaliar.renewly.reminders.Reminders
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/* =============================================================
   Navigation: three screens, no extra library
   ============================================================= */

@Composable
fun RenewlyRoot(vm: SubscriptionsViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf("list") }
    var editId by rememberSaveable { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = route != "list") { route = "list" }

    when (route) {
        "edit" -> EditScreen(vm = vm, id = editId, onDone = { route = "list" })
        "settings" -> SettingsScreen(vm = vm, onBack = { route = "list" })
        else -> ListScreen(
            vm = vm,
            onAdd = { editId = 0L; route = "edit" },
            onOpen = { editId = it; route = "edit" },
            onSettings = { route = "settings" },
        )
    }
}

/* =============================================================
   List
   ============================================================= */

@Composable
private fun ListScreen(
    vm: SubscriptionsViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val subs by vm.subscriptions.collectAsStateWithLifecycle()
    val today = LocalDate.now()

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
                val sorted = list.sortedWith(
                    compareBy<Subscription>({ !it.active }, { it.nextRenewal(today) }, { it.name.lowercase() }),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SummaryCard(list, today) }
                    items(sorted, key = { it.id }) { s ->
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

@Composable
private fun SummaryCard(all: List<Subscription>, today: LocalDate) {
    val active = all.filter { it.active }
    val totals = active.groupBy { it.currency }
        .mapValues { (_, subs) -> subs.sumOf { Renewals.monthlyEquivalentMinor(it.amountMinor, it.billingCycle()) } }
    val next = active.minByOrNull { it.nextRenewal(today) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Per month",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (totals.isEmpty()) {
                Text("Nothing active", style = MaterialTheme.typography.headlineSmall)
            } else {
                totals.entries.sortedByDescending { it.key == "INR" }.forEach { (currency, minor) ->
                    Text(
                        Money.format(minor, currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${active.size} active of ${all.size}" + if (next != null) {
                    " · next: ${next.name} ${Renewals.relativeLabel(Renewals.daysUntil(today, next.nextRenewal(today)))}"
                } else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (s.nickname.isNotBlank()) {
                    Text(
                        s.nickname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (s.active) "Renews ${next.format(dateFormat)} · ${Renewals.relativeLabel(days)}" else "Paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (soon) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal,
                )
                val card = s.cardLabel()
                if (card.isNotEmpty()) {
                    Text(card, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(s.amountMinor, s.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    s.billingCycle().label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* =============================================================
   Add / edit
   ============================================================= */

private val reminderOptions = listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before", 7 to "1 week before")

@Composable
private fun EditScreen(vm: SubscriptionsViewModel, id: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val isNew = id == 0L

    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    var cycle by rememberSaveable { mutableStateOf(BillingCycle.MONTHLY.name) }
    var firstDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var cardNickname by rememberSaveable { mutableStateOf("") }
    var cardLast4 by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableIntStateOf(1) }
    var notes by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
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
                cycle = s.cycle
                firstDay = s.firstPaymentEpochDay
                cardNickname = s.cardNickname
                cardLast4 = s.cardLast4
                remind = s.remindDaysBefore
                notes = s.notes
                active = s.active
                loaded = true
            }
        }
    }

    val amountMinor = Money.parseToMinor(amount)
    val nameError = name.isBlank()
    val amountError = amountMinor == null
    val last4Error = !Money.isValidLast4(cardLast4)

    fun save() {
        if (nameError || amountError || last4Error || amountMinor == null) {
            showErrors = true
            return
        }
        vm.save(
            Subscription(
                id = id,
                name = name.trim(),
                nickname = nickname.trim(),
                amountMinor = amountMinor,
                currency = currency,
                cycle = cycle,
                firstPaymentEpochDay = firstDay,
                cardNickname = cardNickname.trim(),
                cardLast4 = cardLast4,
                remindDaysBefore = remind,
                notes = notes.trim(),
                active = active,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New subscription" else "Edit subscription") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
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
            ChipRow(Money.currencies.map { it to it }, selected = currency, onSelect = { currency = it })

            SectionLabel("Renews")
            ChipRow(BillingCycle.entries.map { it.name to it.label }, selected = cycle, onSelect = { cycle = it })
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
            val previewNext = Renewals.nextRenewal(LocalDate.ofEpochDay(firstDay), BillingCycle.fromName(cycle), LocalDate.now())
            Text(
                "Next renewal: ${previewNext.format(dateFormat)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Payment card")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cardNickname,
                    onValueChange = { cardNickname = it.take(30) },
                    label = { Text("Card name") },
                    placeholder = { Text("HDFC Visa") },
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
                "Only the last 4 digits are stored, never the full card number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Reminder")
            ChipRow(reminderOptions.map { it.first.toString() to it.second }, selected = remind.toString(), onSelect = { remind = it.toInt() })

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
                        "Turn off to pause reminders without deleting",
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
                    existing?.let { vm.delete(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A supporting-text slot that only exists when there's an error to show. */
private fun errorText(show: Boolean, message: String): (@Composable () -> Unit)? =
    if (show) {
        { Text(message) }
    } else {
        null
    }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

/* =============================================================
   Settings: backup, reminders, privacy
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

    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
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
            SectionLabel("Backup")
            Text(
                "Save your subscriptions to a file you control, and restore them on a new phone.",
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

            SectionLabel("Reminders")
            Text(
                if (Reminders.canNotify(context)) {
                    "On. Renewly checks every morning and reminds you before each renewal."
                } else {
                    "Notifications are off. Turn them on for Renewly in your phone's settings to get reminders."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                Reminders.showTest(context)
                scope.launch {
                    snackbar.showSnackbar(
                        if (Reminders.canNotify(context)) "Test reminder sent" else "Notifications are off for Renewly",
                    )
                }
            }) { Text("Send a test reminder") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Privacy")
            Text(
                "Renewly works completely offline. It has no internet permission, no accounts, no ads and no tracking. " +
                    "Your data is stored only on this phone and is excluded from cloud backups.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Renewly $version · by Rachit Ratnam Mudaliar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

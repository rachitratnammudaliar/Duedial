@file:OptIn(ExperimentalMaterial3Api::class)

package com.rachitratnammudaliar.duedial.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rachitratnammudaliar.duedial.data.SortMode
import com.rachitratnammudaliar.duedial.data.Subscription
import com.rachitratnammudaliar.duedial.data.TotalMode
import com.rachitratnammudaliar.duedial.data.cardLabel
import com.rachitratnammudaliar.duedial.data.categoryValue
import com.rachitratnammudaliar.duedial.data.firstPaymentDate
import com.rachitratnammudaliar.duedial.data.monthlyBillMinor
import com.rachitratnammudaliar.duedial.data.nextCharge
import com.rachitratnammudaliar.duedial.data.period
import com.rachitratnammudaliar.duedial.data.shareMinor
import com.rachitratnammudaliar.duedial.data.status
import com.rachitratnammudaliar.duedial.data.toPlan
import com.rachitratnammudaliar.duedial.data.yearlyMinor
import com.rachitratnammudaliar.duedial.domain.Category
import com.rachitratnammudaliar.duedial.domain.Money
import com.rachitratnammudaliar.duedial.domain.Plans
import com.rachitratnammudaliar.duedial.domain.Renewals
import com.rachitratnammudaliar.duedial.domain.Status
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/* =============================================================
   Navigation: four screens, no extra library
   ============================================================= */

/**
 * @param startScreen "list", "settings", "add", "detail:ID" or "edit:ID". Only debug builds pass
 * anything other than "list" (used to take screenshots).
 */
@Composable
fun DuedialRoot(startScreen: String = "list", demo: Boolean = false, vm: SubscriptionsViewModel = viewModel()) {
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
                title = { Text("Duedial", fontWeight = FontWeight.SemiBold) },
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
                val far = LocalDate.MAX
                val shown = list
                    .filter { activeFilter == null || it.categoryValue() == activeFilter }
                    .sortedWith(
                        when (sortMode) {
                            SortMode.NEXT -> compareBy<Subscription>(
                                { listGroup(it, today) },
                                { Plans.nextEvent(it.toPlan(), today)?.date ?: far },
                                { it.name.lowercase() },
                            )
                            SortMode.PRICE -> compareBy<Subscription>(
                                { listGroup(it, today) },
                                { -(it.yearlyMinor(today).takeIf { y -> y > 0 } ?: it.shareMinor()) },
                                { it.name.lowercase() },
                            )
                            SortMode.NAME -> compareBy<Subscription>({ listGroup(it, today) }, { it.name.lowercase() })
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
                    var lastGroup = 0
                    shown.forEach { s ->
                        val group = listGroup(s, today)
                        if (group != lastGroup && group in groupTitles) {
                            val title = groupTitles.getValue(group)
                            item(key = "group-$group") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                                )
                            }
                        }
                        lastGroup = group
                        item(key = s.id) { SubscriptionRow(s, today, onClick = { onOpen(s.id) }) }
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
            "Add Netflix, a free trial, a student offer or a lifetime app. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAdd) { Text("Add your first subscription") }
    }
}

@Composable
private fun SummaryCard(all: List<Subscription>, today: LocalDate, mode: TotalMode, onModeChange: (TotalMode) -> Unit) {
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val totals: Map<String, Long> = when (mode) {
        TotalMode.MONTHLY_BILL -> all.groupBy { it.currency }
            .mapValues { (_, l) -> l.sumOf { it.monthlyBillMinor(today) } }
        TotalMode.THIS_MONTH -> all.groupBy { it.currency }
            .mapValues { (_, l) -> l.sumOf { Plans.dueInTotalsMinor(it.toPlan(), monthStart, monthEnd) } }
    }.filterValues { it > 0 }

    val running = all.filter { it.status(today) == Status.UPCOMING || it.status(today) == Status.ENDING }
    val counted = all.count { Plans.countsInMonthlyBill(it.toPlan(), today) }
    val notCounted = running.size - counted
    val yearly = all.filter { Plans.countsInMonthlyBill(it.toPlan(), today) }
        .groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.yearlyMinor(today) } }.filterValues { it > 0 }
    val trials = all.filter { it.status(today) == Status.TRIAL }.sortedBy { it.firstPaymentDate() }
    val next = all.mapNotNull { s -> s.nextCharge(today)?.let { s to it } }.minByOrNull { it.second }
    val onCard = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                if (mode == TotalMode.THIS_MONTH) {
                    "Due in ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
                } else {
                    "Monthly bill"
                },
                style = MaterialTheme.typography.labelLarge,
                color = onCard,
            )
            if (totals.isEmpty()) {
                Text("Nothing to pay", style = MaterialTheme.typography.headlineSmall, color = onCard)
            } else {
                totals.entries.sortedByDescending { it.key == "INR" }.forEach { (currency, minor) ->
                    Text(
                        Money.format(minor, currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onCard,
                    )
                }
            }
            Text(
                if (mode == TotalMode.MONTHLY_BILL) {
                    buildString {
                        append("$counted counted")
                        if (notCounted > 0) append(" · $notCounted not counted")
                        if (yearly.isNotEmpty()) {
                            append(" · ")
                            append(yearly.entries.sortedByDescending { it.key == "INR" }.joinToString(" + ") { Money.format(it.value, it.key) })
                            append(" a year")
                        }
                    }
                } else {
                    mode.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
            )
            if (next != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Next: ${next.first.name}, ${Renewals.relativeLabel(Renewals.daysUntil(today, next.second))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = onCard,
                )
            }
            trials.firstOrNull()?.let { t ->
                val end = t.firstPaymentDate()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Trial: ${t.name} ends ${Renewals.relativeLabel(Renewals.daysUntil(today, end))}. " +
                        "Cancel by ${end.minusDays(1).format(shortDate)}" +
                        if (trials.size > 1) " (+${trials.size - 1} more)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
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
    val line = statusLine(s, today)
    val (price, priceSub) = priceLabels(s, today)
    val muted = s.status(today) == Status.ENDED || s.status(today) == Status.PAUSED

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (muted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
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
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = toneColor(line.tone),
                    fontWeight = if (line.tone == Tone.WARN) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                Text(price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (priceSub.isNotEmpty()) {
                    Text(
                        priceSub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.WARN -> MaterialTheme.colorScheme.error
    Tone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Tone.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

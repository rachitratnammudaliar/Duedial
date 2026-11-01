@file:OptIn(ExperimentalMaterial3Api::class)

package com.rachitratnammudaliar.duedial.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rachitratnammudaliar.duedial.data.Subscription
import com.rachitratnammudaliar.duedial.data.cardLabel
import com.rachitratnammudaliar.duedial.data.categoryValue
import com.rachitratnammudaliar.duedial.data.endDate
import com.rachitratnammudaliar.duedial.data.firstPaymentDate
import com.rachitratnammudaliar.duedial.data.monthlyBillMinor
import com.rachitratnammudaliar.duedial.data.nextCharge
import com.rachitratnammudaliar.duedial.data.paymentsMade
import com.rachitratnammudaliar.duedial.data.period
import com.rachitratnammudaliar.duedial.data.planTypeValue
import com.rachitratnammudaliar.duedial.data.previousCharge
import com.rachitratnammudaliar.duedial.data.shareMinor
import com.rachitratnammudaliar.duedial.data.status
import com.rachitratnammudaliar.duedial.data.toPlan
import com.rachitratnammudaliar.duedial.domain.Money
import com.rachitratnammudaliar.duedial.domain.PlanType
import com.rachitratnammudaliar.duedial.domain.Plans
import com.rachitratnammudaliar.duedial.domain.Renewals
import com.rachitratnammudaliar.duedial.domain.Status
import java.time.LocalDate

@Composable
fun DetailScreen(vm: SubscriptionsViewModel, id: Long, today: LocalDate, onBack: () -> Unit, onEdit: () -> Unit) {
    val flow = remember(id) { vm.observe(id) }
    val sub by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var cancelDialog by rememberSaveable { mutableStateOf(false) }

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
        val type = s.planTypeValue()
        val status = s.status(today)
        val plan = s.toPlan()
        val end = s.endDate()
        val share = Money.format(s.shareMinor(), s.currency)
        val full = Money.format(s.amountMinor, s.currency)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscriptionBadge(s.name, s.iconFile, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    val (price, priceSub) = priceLabels(s, today)
                    Text(price, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (priceSub.isNotEmpty()) {
                        Text(priceSub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val sub2 = listOf(type.label, s.nickname, s.categoryValue().label).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(sub2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val line = statusLine(s, today)
            Spacer(Modifier.height(10.dp))
            Text(line.text, style = MaterialTheme.typography.bodyLarge, color = toneColor(line.tone), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))

            // Dates
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val rows = mutableListOf<Triple<String, String, String>>()
                    when (status) {
                        Status.TRIAL -> {
                            val trialEnd = s.firstPaymentDate()
                            rows += Triple("Trial ends", trialEnd.format(dateFormat), Renewals.relativeLabel(Renewals.daysUntil(today, trialEnd)))
                            rows += Triple("Cancel by", trialEnd.minusDays(1).format(dateFormat), "to avoid being charged")
                            rows += Triple("Then costs", share, s.period().label)
                        }
                        Status.TRIAL_CANCELLED -> {
                            rows += Triple("Trial", "Cancelled", "no charge will happen")
                            rows += Triple("Free until", end?.format(dateFormat) ?: "", "")
                        }
                        Status.FREE -> {
                            rows += Triple("Free until", end?.format(dateFormat) ?: "No end date", end?.let { Renewals.relativeLabel(Renewals.daysUntil(today, it)) } ?: "")
                            rows += Triple("Free since", s.firstPaymentDate().format(dateFormat), "")
                        }
                        Status.OWNED -> {
                            val date = s.firstPaymentDate()
                            rows += Triple(if (date.isAfter(today)) "Purchase on" else "Bought", date.format(dateFormat), "")
                            rows += Triple("Paid", share, "once")
                        }
                        else -> {
                            val next = s.nextCharge(today)
                            if (status != Status.ENDED && status != Status.PAUSED) {
                                rows += Triple(
                                    "Next payment",
                                    next?.format(dateFormat) ?: "None",
                                    next?.let { Renewals.relativeLabel(Renewals.daysUntil(today, it)) } ?: "no more payments",
                                )
                            }
                            if (end != null) rows += Triple(if (status == Status.ENDED) "Ended" else "Active until", end.format(dateFormat), "")
                            rows += Triple("Last payment", s.previousCharge(today)?.format(dateFormat) ?: "Not yet", "")
                            rows += Triple("First payment", s.firstPaymentDate().format(dateFormat), "")
                            val paid = s.paymentsMade(today)
                            rows += Triple(
                                "Paid so far",
                                if (paid == 0L) "Nothing yet" else Money.format(Plans.paidSoFarMinor(plan, today), s.currency),
                                if (paid == 0L) "" else "$paid payment${if (paid == 1L) "" else "s"}, at today's price",
                            )
                        }
                    }
                    rows.forEachIndexed { i, (label, value, note) ->
                        if (i > 0) HorizontalDivider()
                        InfoRow(label, value, note)
                    }
                }
            }

            // Costs
            if (type == PlanType.RECURRING || type == PlanType.TRIAL || s.regularAmountMinor != null || s.splitCount > 1) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        var first = true
                        fun divider() {
                            first = false
                        }
                        if (s.splitCount > 1) {
                            InfoRow("Your share", share, "$full split ${s.splitCount} ways")
                            divider()
                        }
                        if (type == PlanType.RECURRING || type == PlanType.TRIAL) {
                            if (!first) HorizontalDivider()
                            val perMonth = Renewals.monthlyEquivalentMinor(s.shareMinor(), s.period())
                            InfoRow("Per month", Money.format(perMonth, s.currency), if (s.period().isMonthly) "" else "average")
                            HorizontalDivider()
                            InfoRow("Per year", Money.format(Renewals.yearlyEquivalentMinor(s.shareMinor(), s.period()), s.currency))
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Count in monthly bill", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        when {
                                            !s.includeInMonthly -> "Not counted"
                                            s.monthlyBillMinor(today) == 0L -> "Counted when it's running"
                                            s.period().isMonthly -> "Adds ${Money.format(s.monthlyBillMinor(today), s.currency)}"
                                            else -> "Adds ${Money.format(s.monthlyBillMinor(today), s.currency)} a month (${s.period().label.lowercase()}, spread out)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = s.includeInMonthly,
                                    onCheckedChange = { vm.save(s.copy(includeInMonthly = it), replacedIcon = s.iconFile) },
                                )
                            }
                            divider()
                        }
                        Plans.savingMinor(plan)?.let { saving ->
                            if (!first) HorizontalDivider()
                            InfoRow(
                                "You save",
                                Money.format(saving, s.currency),
                                "per payment vs ${Money.format(s.regularAmountMinor ?: 0, s.currency)} regular price",
                            )
                        }
                    }
                }
            }

            // Payment and reminders
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    InfoRow("Payment method", s.cardLabel().ifEmpty { "Not set" })
                    if (type != PlanType.LIFETIME) {
                        HorizontalDivider()
                        InfoRow("Reminder", reminderLabel(s.remindDaysBefore))
                    }
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

            // Actions
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canCancel = (type == PlanType.RECURRING || type == PlanType.TRIAL) && end == null && s.active
                if (canCancel) {
                    OutlinedButton(onClick = { cancelDialog = true }) { Text(if (status == Status.TRIAL) "I cancelled the trial" else "Mark as cancelled") }
                }
                if ((type == PlanType.RECURRING || type == PlanType.TRIAL) && end != null) {
                    OutlinedButton(onClick = { vm.save(s.copy(endEpochDay = null), replacedIcon = s.iconFile) }) {
                        Text(if (status == Status.ENDED) "Resume subscription" else "Undo cancel")
                    }
                }
                OutlinedButton(onClick = { vm.save(s.copy(active = !s.active), replacedIcon = s.iconFile) }) {
                    Text(if (s.active) "Pause" else "Unpause")
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (cancelDialog) {
            CancelDialog(s, today, onDismiss = { cancelDialog = false }, onConfirm = { endDay ->
                cancelDialog = false
                vm.save(s.copy(endEpochDay = endDay), replacedIcon = s.iconFile)
            })
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${s.name}?") },
                text = { Text("This removes it and its history from Duedial. It can't be undone. To stop tracking without losing history, mark it as cancelled instead.") },
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

/**
 * Asks until when a cancelled subscription stays active. Defaults to the day before the next
 * charge: you keep what you've paid for, and no further payment happens.
 */
@Composable
private fun CancelDialog(s: Subscription, today: LocalDate, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val context = LocalContext.current
    val default = remember(s.id, today) {
        val next = s.nextCharge(today)
        (next?.minusDays(1) ?: today).let { if (it.isBefore(today)) today else it }
    }
    var endDay by rememberSaveable { mutableLongStateOf(default.toEpochDay()) }
    val date = LocalDate.ofEpochDay(endDay)
    val trial = s.status(today) == Status.TRIAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trial) "Trial cancelled" else "Mark ${s.name} as cancelled") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (trial) {
                        "Duedial will stop reminding you and won't count a charge. You can keep using it until:"
                    } else {
                        "No payments will be counted after this date. Usually it's the last day of the period you've paid for:"
                    },
                )
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> endDay = LocalDate.of(y, m + 1, d).toEpochDay() },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    ).show()
                }) { Text("Active until ${date.format(dateFormat)}") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(endDay) }) { Text("Mark cancelled") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}

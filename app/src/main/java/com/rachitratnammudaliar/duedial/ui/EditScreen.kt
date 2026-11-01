@file:OptIn(ExperimentalMaterial3Api::class)

package com.rachitratnammudaliar.duedial.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rachitratnammudaliar.duedial.data.IconStore
import com.rachitratnammudaliar.duedial.data.Subscription
import com.rachitratnammudaliar.duedial.data.period
import com.rachitratnammudaliar.duedial.domain.BillingPeriod
import com.rachitratnammudaliar.duedial.domain.Brands
import com.rachitratnammudaliar.duedial.domain.Category
import com.rachitratnammudaliar.duedial.domain.Money
import com.rachitratnammudaliar.duedial.domain.PeriodUnit
import com.rachitratnammudaliar.duedial.domain.PlanType
import com.rachitratnammudaliar.duedial.domain.Plans
import com.rachitratnammudaliar.duedial.domain.Renewals
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** A supporting-text slot that only exists when there's an error to show. */
private fun errorText(show: Boolean, message: String): (@Composable () -> Unit)? =
    if (show) {
        { Text(message) }
    } else {
        null
    }

private fun pickDate(context: Context, epochDay: Long, onPicked: (Long) -> Unit) {
    val d = LocalDate.ofEpochDay(epochDay)
    DatePickerDialog(
        context,
        { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day).toEpochDay()) },
        d.year,
        d.monthValue - 1,
        d.dayOfMonth,
    ).show()
}

@Composable
fun EditScreen(
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

    var type by rememberSaveable { mutableStateOf(PlanType.RECURRING.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var regular by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    var periodCount by rememberSaveable { mutableIntStateOf(1) }
    var periodUnit by rememberSaveable { mutableStateOf(PeriodUnit.MONTH.name) }
    var customPeriod by rememberSaveable { mutableStateOf(false) }
    var customCountText by rememberSaveable { mutableStateOf("2") }
    var firstDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var hasEnd by rememberSaveable { mutableStateOf(false) }
    var endDay by rememberSaveable { mutableLongStateOf(today.plusMonths(1).toEpochDay()) }
    var category by rememberSaveable { mutableStateOf(Category.OTHER.name) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var cardNickname by rememberSaveable { mutableStateOf("") }
    var cardLast4 by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableIntStateOf(1) }
    var remindTouched by rememberSaveable { mutableStateOf(false) }
    var split by rememberSaveable { mutableIntStateOf(1) }
    var include by rememberSaveable { mutableStateOf(true) }
    var includeTouched by rememberSaveable { mutableStateOf(false) }
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
                val t = PlanType.fromName(s.planType)
                type = t.name
                name = s.name
                nickname = s.nickname
                amount = if (t == PlanType.FREE || s.amountMinor <= 0) "" else Money.minorToInput(s.amountMinor)
                regular = s.regularAmountMinor?.let { Money.minorToInput(it) } ?: ""
                currency = s.currency
                periodCount = s.periodCount
                periodUnit = s.periodUnit
                customPeriod = s.period() !in BillingPeriod.presets
                customCountText = s.periodCount.toString()
                firstDay = s.firstPaymentEpochDay
                hasEnd = s.endEpochDay != null
                endDay = s.endEpochDay ?: today.plusMonths(1).toEpochDay()
                category = s.category
                categoryTouched = true
                cardNickname = s.cardNickname
                cardLast4 = s.cardLast4
                remind = s.remindDaysBefore
                remindTouched = true
                split = s.splitCount.coerceIn(1, 20)
                include = s.includeInMonthly
                includeTouched = true
                notes = s.notes
                active = s.active
                iconFile = s.iconFile
                originalIcon = s.iconFile
                loaded = true
            }
        }
    }

    val planType = PlanType.fromName(type)
    val isPaidPlan = planType == PlanType.RECURRING || planType == PlanType.TRIAL
    val countFromText = customCountText.toIntOrNull()
    val effectiveCount = if (customPeriod) countFromText ?: 0 else periodCount
    val period = if (effectiveCount in 1..999) BillingPeriod(effectiveCount, PeriodUnit.fromName(periodUnit)) else null

    // Defaults that follow the choices until the user changes them.
    LaunchedEffect(type, effectiveCount, periodUnit) {
        if (!includeTouched && period != null) include = Plans.defaultIncludeInMonthly(planType, period)
        if (!remindTouched) remind = if (planType == PlanType.TRIAL) 2 else 1
    }

    // Validation
    val amountMinor = Money.parseToMinor(amount)
    val regularMinor = if (regular.isBlank()) null else Money.parseToMinor(regular)
    val nameError = name.isBlank()
    val amountError = planType != PlanType.FREE && amountMinor == null
    val regularError = regular.isNotBlank() && regularMinor == null
    val periodError = isPaidPlan && period == null
    val last4Error = !Money.isValidLast4(cardLast4)
    val endError = hasEnd && (planType == PlanType.RECURRING || planType == PlanType.FREE) && endDay < firstDay

    fun leave(saved: Boolean) {
        if (!saved && iconFile.isNotBlank() && iconFile != originalIcon) IconStore.delete(context, iconFile)
        onDone(saved && !isNew)
    }
    BackHandler { leave(saved = false) }

    fun save() {
        if (nameError || amountError || regularError || periodError || last4Error || endError) {
            showErrors = true
            scope.launch { snackbar.showSnackbar("Check the highlighted fields") }
            return
        }
        val p = period ?: BillingPeriod.MONTHLY
        vm.save(
            Subscription(
                id = id,
                name = name.trim(),
                nickname = nickname.trim(),
                amountMinor = if (planType == PlanType.FREE) 0 else amountMinor ?: 0,
                currency = currency,
                periodCount = p.count,
                periodUnit = p.unit.name,
                firstPaymentEpochDay = firstDay,
                category = category,
                cardNickname = if (planType == PlanType.FREE) "" else cardNickname.trim(),
                cardLast4 = if (planType == PlanType.FREE) "" else cardLast4,
                remindDaysBefore = if (planType == PlanType.LIFETIME) -1 else remind,
                notes = notes.trim(),
                iconFile = iconFile,
                active = active,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                planType = planType.name,
                endEpochDay = when (planType) {
                    PlanType.RECURRING, PlanType.FREE -> if (hasEnd) endDay else null
                    PlanType.TRIAL -> if (hasEnd) firstDay - 1 else null
                    PlanType.LIFETIME -> null
                },
                includeInMonthly = isPaidPlan && include,
                splitCount = if (isPaidPlan) split else 1,
                regularAmountMinor = regularMinor,
            ),
            replacedIcon = originalIcon,
        )
        leave(saved = true)
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val newFile = vm.saveIcon(uri)
                    if (iconFile.isNotBlank() && iconFile != originalIcon) IconStore.delete(context, iconFile)
                    iconFile = newFile
                } catch (e: Exception) {
                    snackbar.showSnackbar("That image couldn't be used. Try another one.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New" else "Edit") },
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
            // Type
            ChipRow(PlanType.entries, selected = planType, label = { it.label }, onSelect = { type = it.name })
            Text(planType.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Icon and name
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscriptionBadge(name, iconFile, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                OutlinedButton(onClick = {
                    iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (iconFile.isBlank()) "Choose icon" else "Change icon") }
                if (iconFile.isNotBlank()) {
                    TextButton(onClick = {
                        if (iconFile != originalIcon) IconStore.delete(context, iconFile)
                        iconFile = ""
                    }) { Text("Remove") }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { input ->
                    name = input.take(60)
                    if (isNew && !categoryTouched) category = (Brands.match(name)?.category ?: Category.OTHER).name
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
                placeholder = { Text(if (planType == PlanType.FREE) "Student offer" else "Family plan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Price
            SectionLabel(
                when (planType) {
                    PlanType.RECURRING -> "Price"
                    PlanType.TRIAL -> "Price after the trial"
                    PlanType.FREE -> "Normal price (optional)"
                    PlanType.LIFETIME -> "Price paid"
                },
            )
            if (planType == PlanType.FREE) {
                MoneyField(regular, { regular = it }, "Normally costs", "119", showErrors && regularError)
            } else {
                MoneyField(amount, { amount = it }, "Amount", "649", showErrors && amountError)
            }
            ChipRow(Money.currencies, selected = currency, label = { it }, onSelect = { currency = it })

            // Billing period
            if (isPaidPlan) {
                SectionLabel("Billing period")
                val presetSelected = if (customPeriod) null else BillingPeriod.presets.firstOrNull {
                    it.count == periodCount && it.unit.name == periodUnit
                }
                ChipRow(
                    options = BillingPeriod.presets.map<BillingPeriod, BillingPeriod?> { it } + listOf(null),
                    selected = presetSelected,
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
            }

            // Dates
            SectionLabel("Dates")
            OutlinedButton(onClick = { pickDate(context, firstDay) { firstDay = it } }, modifier = Modifier.fillMaxWidth()) {
                val label = when (planType) {
                    PlanType.RECURRING -> "First payment"
                    PlanType.TRIAL -> "Trial ends (first charge)"
                    PlanType.FREE -> "Free since"
                    PlanType.LIFETIME -> "Purchase date"
                }
                Text("$label: ${LocalDate.ofEpochDay(firstDay).format(dateFormat)}")
            }
            val first = LocalDate.ofEpochDay(firstDay)
            when (planType) {
                PlanType.RECURRING -> {
                    if (period != null) {
                        val next = Renewals.nextRenewal(first, period, today)
                        Hint("${period.label}. Next payment: ${next.format(dateFormat)}, ${Renewals.relativeLabel(Renewals.daysUntil(today, next))}.")
                    } else {
                        Hint("Enter how often it repeats (1 to 999).", error = true)
                    }
                    SwitchRow(
                        title = "Cancelled or fixed term",
                        subtitle = "No payments are counted after the end date",
                        checked = hasEnd,
                        onChange = { hasEnd = it },
                    )
                    if (hasEnd) {
                        OutlinedButton(onClick = { pickDate(context, endDay) { endDay = it } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Active until: ${LocalDate.ofEpochDay(endDay).format(dateFormat)}")
                        }
                        if (showErrors && endError) Hint("The end date can't be before the first payment.", error = true)
                    }
                }
                PlanType.TRIAL -> {
                    Hint("Cancel by ${first.minusDays(1).format(dateFormat)} to avoid being charged. You'll get a reminder before.")
                    SwitchRow(
                        title = "Already cancelled",
                        subtitle = "Free until the trial ends, then nothing is charged",
                        checked = hasEnd,
                        onChange = { hasEnd = it },
                    )
                }
                PlanType.FREE -> {
                    SwitchRow(
                        title = "Free until a date",
                        subtitle = "For offers that end, like a student plan. You'll get a reminder.",
                        checked = hasEnd,
                        onChange = { hasEnd = it },
                    )
                    if (hasEnd) {
                        OutlinedButton(onClick = { pickDate(context, endDay) { endDay = it } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Free until: ${LocalDate.ofEpochDay(endDay).format(dateFormat)}")
                        }
                        if (showErrors && endError) Hint("The end date can't be before the start.", error = true)
                    } else {
                        Hint("No end date: free for as long as it lasts.")
                    }
                }
                PlanType.LIFETIME -> Hint("Paid once. No renewals and not part of the monthly bill.")
            }

            // Cost: sharing, discount, monthly bill
            if (isPaidPlan) {
                SectionLabel("Cost")
                Text("Split between", style = MaterialTheme.typography.bodyMedium)
                ChipRow(
                    options = (1..6).toList(),
                    selected = split,
                    label = { if (it == 1) "Just me" else "$it people" },
                    onSelect = { split = it },
                )
                val shareMinor = amountMinor?.let {
                    BigDecimal.valueOf(it).divide(BigDecimal(split), 0, RoundingMode.HALF_UP).toLong()
                }
                if (split > 1 && shareMinor != null) Hint("Your share: ${Money.format(shareMinor, currency)} per payment.")

                MoneyField(regular, { regular = it }, "Regular price (optional)", "If you pay a discounted price", showErrors && regularError)

                SwitchRow(
                    title = "Count in monthly bill",
                    subtitle = when {
                        shareMinor == null || period == null -> "Add it to the monthly total on the home screen"
                        period.isMonthly -> "Adds ${Money.format(shareMinor, currency)} a month"
                        else -> "Adds ${Money.format(Renewals.monthlyEquivalentMinor(shareMinor, period), currency)} a month " +
                            "(${Money.format(shareMinor, currency)} ${period.label.lowercase()}, spread out)"
                    } + if (planType == PlanType.TRIAL) ". Starts counting after the trial." else "",
                    checked = include,
                    onChange = { include = it; includeTouched = true },
                )
            }

            // Category
            SectionLabel("Category")
            ChipRow(
                options = Category.entries,
                selected = Category.fromName(category),
                label = { it.label },
                onSelect = { category = it.name; categoryTouched = true },
            )

            // Payment method
            if (planType != PlanType.FREE) {
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
                Hint(
                    if (showErrors && last4Error) "Enter exactly 4 digits, or leave it empty." else "Only the last 4 digits are stored, never the full card number.",
                    error = showErrors && last4Error,
                )
            }

            // Reminder
            if (planType != PlanType.LIFETIME) {
                SectionLabel(
                    when (planType) {
                        PlanType.TRIAL -> "Remind me before the trial ends"
                        PlanType.FREE -> "Remind me before the free period ends"
                        else -> "Reminder"
                    },
                )
                ChipRow(
                    reminderOptions.map { it.first },
                    selected = remind,
                    label = { reminderLabel(it) },
                    onSelect = { remind = it; remindTouched = true },
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(500) },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(
                title = "Active",
                subtitle = "Turn off to pause: no reminders, not counted",
                checked = active,
                onChange = { active = it },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${name.ifBlank { "subscription" }}?") },
            text = { Text("This removes it from Duedial. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let { vm.delete(it) }
                    if (iconFile.isNotBlank() && iconFile != originalIcon) IconStore.delete(context, iconFile)
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' || it == ',' }.take(12)) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        supportingText = errorText(isError, "Enter an amount above zero, up to 2 decimals"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

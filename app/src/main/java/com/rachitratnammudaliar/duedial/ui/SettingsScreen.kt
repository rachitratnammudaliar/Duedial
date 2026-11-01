@file:OptIn(ExperimentalMaterial3Api::class)

package com.rachitratnammudaliar.duedial.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rachitratnammudaliar.duedial.reminders.Reminders
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: SubscriptionsViewModel, onBack: () -> Unit) {
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

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Open your phone's Settings > Apps > Duedial instead") }
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
                    "On. Duedial checks every morning and reminds you before payments, when trials end and when free periods end."
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
                        snackbar.showSnackbar(if (Reminders.canNotify(context)) "Test reminder sent" else "Notifications are off for Duedial")
                    }
                }) { Text("Send a test") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samsung and other phones", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Battery saving can put apps to sleep and delay reminders. Open Duedial's app settings, " +
                            "tap Battery, and choose Unrestricted. Also make sure Duedial isn't in Sleeping apps.",
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
                FilledTonalButton(onClick = { exportLauncher.launch("duedial-backup-${LocalDate.now()}.json") }) {
                    Text("Export")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Import")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Privacy")
            Text(
                "Duedial works completely offline. It has no internet permission, no accounts, no ads and no tracking. " +
                    "Your data is stored only on this phone and is excluded from cloud backups.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Duedial $version · github.com/rachitratnammudaliar/duedial",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

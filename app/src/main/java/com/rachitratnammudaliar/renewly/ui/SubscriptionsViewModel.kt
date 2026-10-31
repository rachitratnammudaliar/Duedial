package com.rachitratnammudaliar.renewly.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rachitratnammudaliar.renewly.backup.Backup
import com.rachitratnammudaliar.renewly.data.AppDatabase
import com.rachitratnammudaliar.renewly.data.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.subscriptions()

    /** Null until the database has loaded, so the screen doesn't flash "empty". */
    val subscriptions: StateFlow<List<Subscription>?> = dao.observeAll()
        .map<List<Subscription>, List<Subscription>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun get(id: Long): Subscription? = dao.get(id)

    fun save(subscription: Subscription) {
        viewModelScope.launch {
            if (subscription.id == 0L) dao.insert(subscription) else dao.update(subscription)
        }
    }

    fun delete(subscription: Subscription) {
        viewModelScope.launch { dao.delete(subscription) }
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
}

package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What has been searched for on this device, most recent first. It stays in `hihu.db` and is
 * never sent anywhere; a query searched again moves back to the top instead of repeating.
 */
class SearchHistory(private val store: Store, private val scope: CoroutineScope) {
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    init {
        scope.launch { reload() }
    }

    /** Notes that [query] was searched for now. Blank queries are ignored. */
    fun record(query: String) {
        if (query.isBlank()) return
        update { db ->
            db.insertWithOnConflict(
                "search_history",
                null,
                ContentValues().apply {
                    put("query", query)
                    put("searched_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.execSQL(
                "DELETE FROM search_history WHERE query NOT IN " +
                    "(SELECT query FROM search_history ORDER BY searched_at DESC LIMIT $KEEP)",
            )
        }
    }

    fun remove(query: String) = update { db -> db.delete("search_history", "query = ?", arrayOf(query)) }

    fun clear() = update { db -> db.delete("search_history", null, null) }

    private fun update(block: (SQLiteDatabase) -> Unit) {
        scope.launch {
            store.transaction(block)
            reload()
        }
    }

    private suspend fun reload() {
        _recent.value = store.run { db ->
            db.rawQuery("SELECT query FROM search_history ORDER BY searched_at DESC", null).all { it.getString(0) }
        }
    }

    private companion object {
        const val KEEP = 50
    }
}

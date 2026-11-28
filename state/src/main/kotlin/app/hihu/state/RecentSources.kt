package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import app.hihu.music.Collection
import app.hihu.music.CollectionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An album or playlist songs were played from lately. */
sealed interface RecentSource {
    /** The source as a card shows it; a local playlist gets an id of its own that no provider uses. */
    val collection: Collection

    data class Remote(override val collection: Collection) : RecentSource

    data class Local(val playlist: PlaylistSummary) : RecentSource {
        override val collection = Collection(
            id = "$LOCAL_PREFIX${playlist.id}",
            kind = CollectionKind.Playlist,
            title = playlist.name,
            subtitle = null,
            artworkUri = playlist.artworkUri,
            count = playlist.count.toLong(),
        )
    }
}

/**
 * The albums and playlists songs were last played from, newest first, for Home's "Played lately".
 * Kept in `hihu.db` and never sent anywhere. Played again, a source moves back to the front.
 */
class RecentSources(private val store: Store, playlists: Playlists, private val scope: CoroutineScope) {
    private data class Saved(val kind: String, val ref: String, val title: String?, val subtitle: String?, val artwork: String?)

    private val saved = MutableStateFlow<List<Saved>>(emptyList())

    /** What to show; a local playlist that was deleted is left out, and a renamed one has its new name. */
    val recent: StateFlow<List<RecentSource>> = combine(saved, playlists.all) { rows, local ->
        val byId = local.associateBy { it.id }
        rows.mapNotNull { row ->
            when (row.kind == LOCAL) {
                true -> row.ref.toLongOrNull()?.let(byId::get)?.let(RecentSource::Local)
                false -> {
                    val kind = runCatching { CollectionKind.valueOf(row.kind) }.getOrNull() ?: return@mapNotNull null
                    RecentSource.Remote(Collection(row.ref, kind, row.title.orEmpty(), row.subtitle, row.artwork))
                }
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch { reload() }
    }

    /** Notes that songs of [collection], an album, playlist or artist from a provider, were played now. */
    fun record(collection: Collection) =
        put("yt:${collection.id}", collection.kind.name, collection.id, collection.title, collection.subtitle, collection.artworkUri)

    /** Notes that songs of the local playlist [id] were played now. */
    fun recordLocal(id: Long) = put("$LOCAL:$id", LOCAL, id.toString(), null, null, null)

    private fun put(key: String, kind: String, ref: String, title: String?, subtitle: String?, artwork: String?) {
        scope.launch {
            store.transaction { db ->
                db.insertWithOnConflict(
                    "recent_sources",
                    null,
                    ContentValues().apply {
                        put("key", key)
                        put("kind", kind)
                        put("ref", ref)
                        put("title", title)
                        put("subtitle", subtitle)
                        put("artwork", artwork)
                        put("played_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.execSQL(
                    "DELETE FROM recent_sources WHERE key NOT IN " +
                        "(SELECT key FROM recent_sources ORDER BY played_at DESC LIMIT $KEEP)",
                )
            }
            reload()
        }
    }

    private suspend fun reload() {
        saved.value = store.run { db ->
            db.rawQuery("SELECT kind, ref, title, subtitle, artwork FROM recent_sources ORDER BY played_at DESC", null)
                .all { Saved(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4)) }
        }
    }

    companion object {
        /** The local playlist a card's collection stands for, or null for a provider's. */
        fun localPlaylist(collection: Collection): Long? =
            collection.id.takeIf { it.startsWith(LOCAL_PREFIX) }?.removePrefix(LOCAL_PREFIX)?.toLongOrNull()
    }
}

private const val LOCAL = "Local"
private const val LOCAL_PREFIX = "hihu-local:"
private const val KEEP = 20

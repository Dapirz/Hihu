package app.hihu.state

import android.content.ContentValues
import app.hihu.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What has been played, most recent first and each song once. Old entries are pruned. */
class History(private val store: Store, private val scope: CoroutineScope) {
    private val _recent = MutableStateFlow<List<Song>>(emptyList())
    val recent: StateFlow<List<Song>> = _recent.asStateFlow()

    private val _mostPlayed = MutableStateFlow<List<Song>>(emptyList())

    /** The songs played most in the last 30 days, at least twice each, most played first. */
    val mostPlayed: StateFlow<List<Song>> = _mostPlayed.asStateFlow()

    init {
        scope.launch { reload() }
    }

    /** Notes that [song] started playing now. */
    fun record(song: Song) {
        scope.launch {
            store.transaction { db ->
                db.putSong(song)
                db.insert("history", null, ContentValues().apply {
                    put("song_id", song.id)
                    put("played_at", System.currentTimeMillis())
                })
                // Kept by age, so the rows that count the last weeks have them all; and at least the
                // last plays, however old.
                db.execSQL(
                    "DELETE FROM history WHERE played_at < ? AND id NOT IN (SELECT id FROM history ORDER BY played_at DESC LIMIT $KEEP_ROWS)",
                    arrayOf(System.currentTimeMillis() - KEEP_DAYS * DAY_MS),
                )
                // A song nothing points at any more, like one a radio played once, goes too.
                db.execSQL(
                    """
                    DELETE FROM songs WHERE id NOT IN (SELECT song_id FROM history)
                      AND id NOT IN (SELECT song_id FROM favorites)
                      AND id NOT IN (SELECT song_id FROM playlist_songs)
                      AND id NOT IN (SELECT song_id FROM queue)
                      AND id NOT IN (SELECT song_id FROM downloads)
                      AND id NOT IN (SELECT song_id FROM like_queue)
                      AND id NOT IN (SELECT song_id FROM hidden_songs)
                    """,
                )
            }
            reload()
        }
    }

    /**
     * The [count] artists played most in the last [days] days, most played first. A credit like
     * "A, B" counts for its first name, the one the song is mostly by.
     */
    suspend fun topArtists(days: Long, count: Int): List<String> {
        val since = System.currentTimeMillis() - days * DAY_MS
        val credits = store.run { db ->
            db.rawQuery(
                """
                SELECT s.artist, COUNT(*) FROM history h JOIN songs s ON s.id = h.song_id
                WHERE h.played_at >= ? AND s.artist IS NOT NULL GROUP BY s.artist
                """,
                arrayOf(since.toString()),
            ).all { it.getString(0) to it.getInt(1) }
        }
        return credits
            .groupBy({ (credit, _) -> credit.split(", ", " & ", " feat. ", " ft. ").first().trim() }, { it.second })
            .mapValues { it.value.sum() }
            .filterKeys { it.isNotEmpty() }
            .entries.sortedByDescending { it.value }
            .take(count)
            .map { it.key }
    }

    private suspend fun reload() {
        _recent.value = store.run { db ->
            db.rawQuery(
                """
                SELECT $SONG_COLUMNS FROM songs s
                JOIN (SELECT song_id, MAX(played_at) AS last FROM history GROUP BY song_id) h ON h.song_id = s.id
                ORDER BY h.last DESC LIMIT $SHOWN
                """,
                null,
            ).all { it.readSong() }
        }
        val since = System.currentTimeMillis() - MOST_PLAYED_DAYS * DAY_MS
        _mostPlayed.value = store.run { db ->
            db.rawQuery(
                """
                SELECT $SONG_COLUMNS FROM songs s
                JOIN (SELECT song_id, COUNT(*) AS plays, MAX(played_at) AS last FROM history
                      WHERE played_at >= ? GROUP BY song_id HAVING COUNT(*) >= 2) h ON h.song_id = s.id
                ORDER BY h.plays DESC, h.last DESC LIMIT $MOST_PLAYED
                """,
                arrayOf(since.toString()),
            ).all { it.readSong() }
        }
    }

    private companion object {
        const val SHOWN = 100
        const val KEEP_ROWS = 1000
        /** As far back as any row of Home looks: the releases of the artists of the last 60 days. */
        const val KEEP_DAYS = 60L
        const val MOST_PLAYED = 12
        const val MOST_PLAYED_DAYS = 30L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

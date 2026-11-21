package app.hihu.music.youtube

import app.hihu.music.AccountProfile
import app.hihu.music.Collection
import app.hihu.music.CollectionKind
import app.hihu.music.ExploreFeed
import app.hihu.music.Mood
import app.hihu.music.Page
import app.hihu.music.Release
import app.hihu.music.SearchResult
import app.hihu.music.SessionExpired
import app.hihu.music.Shelf
import app.hihu.music.Song
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject

/**
 * Turns YouTube Music's home, explore and mood pages into [Shelf]s. Albums and playlists become
 * [Collection]s whose id is a playlist URL, the same kind search gives, so NewPipeExtractor can
 * open them; artists become channel URLs.
 */
internal class YouTubeFeed(private val api: InnerTube) {

    fun home(): Page<Shelf> = homePage(sections(api.browse(browseId = HOME)))

    fun explore(): ExploreFeed {
        val list = sections(api.browse(browseId = EXPLORE))
        val moods = list.contents.flatMap { moodsIn(it) }
        val shelves = list.contents.mapNotNull(::shelf).filter { shelf -> shelf.items.isNotEmpty() }
        return ExploreFeed(shelves, moods)
    }

    fun mood(mood: Mood): List<Shelf> =
        sections(api.browse(browseId = MOOD_PAGE, params = mood.ref)).contents.mapNotNull(::shelf)

    /**
     * The plain lyrics YouTube Music shows for [videoId], or null when it has none. Its watch page
     * names a lyrics tab only for songs with lyrics; that tab is a browse page with the text.
     */
    fun lyrics(videoId: String): String? {
        val tabs = api.next(videoId).getObject("contents").getObject("singleColumnMusicWatchNextResultsRenderer")
            .getObject("tabbedRenderer").getObject("watchNextTabbedResultsRenderer").getArray("tabs").objects()
        val browseId = tabs.map { it.getObject("tabRenderer").getObject("endpoint").getObject("browseEndpoint") }
            .firstOrNull { it.getString("browseId", "").startsWith(LYRICS_PREFIX) }
            ?.getString("browseId") ?: return null
        val shelf = api.browse(browseId = browseId).getObject("contents").getObject("sectionListRenderer")
            .getArray("contents").objects().firstNotNullOfOrNull { it.getObject("musicDescriptionShelfRenderer").takeIf { s -> s.isNotEmpty() } }
            ?: return null
        return shelf.getObject("description").getArray("runs").objects()
            .joinToString("") { it.getString("text", "") }.takeIf { it.isNotBlank() }
    }

    /**
     * The songs of the first chart on YouTube Music's charts page for the client's country, which
     * is its trending list ("Trending 20"). Null when the page has no chart playlist.
     */
    fun trending(): List<Song>? {
        val chart = findAll(api.browse(browseId = CHARTS), CARD)
            .map { it.getObject("navigationEndpoint").getObject("browseEndpoint") }
            .firstOrNull { pageType(it) == PLAYLIST }
            ?.getString("browseId", null)?.removePrefix("VL") ?: return null
        return playlistSongs(chart).items
    }

    /**
     * The list id of the radio on artist [channelId]'s page, from its Radio button, or null when
     * the page has none.
     */
    fun artistRadioId(channelId: String): String? =
        findObject(api.browse(browseId = channelId), "startRadioButton")?.let { findString(it, "playlistId") }

    /**
     * The first albums and singles on artist [channelId]'s page, which lists them newest first,
     * with the year each came out in, read from the card's subtitle ("Single • 2024").
     */
    fun artistReleases(channelId: String): List<Release> {
        val sections = sections(api.browse(browseId = channelId)).contents
        return sections.flatMap { section ->
            section.getObject("musicCarouselShelfRenderer").getArray("contents").objects()
                .mapNotNull { entry ->
                    val collection = (item(entry) as? SearchResult.Group)?.collection ?: return@mapNotNull null
                    if (collection.kind != CollectionKind.Album) return@mapNotNull null
                    val subtitle = entry.getObject(CARD).getObject("subtitle").runsText().orEmpty()
                    val year = YEAR.findAll(subtitle).lastOrNull()?.value?.toIntOrNull() ?: return@mapNotNull null
                    Release(collection, year)
                }
                .take(RELEASES_PER_SHELF)
        }
    }

    /** Dislikes [videoId] or takes the dislike back; [api] must carry a session. */
    fun dislike(videoId: String, disliked: Boolean) = api.dislike(videoId, disliked)

    /** Likes or unlikes [videoId]; [api] must carry a session. */
    fun like(videoId: String, liked: Boolean) = api.like(videoId, liked)

    /** The signed-in account from the account menu; [api] must carry a session. */
    fun profile(): AccountProfile {
        val header = findObject(api.accountMenu(), "activeAccountHeaderRenderer")
            ?: throw SessionExpired("youtube: the account menu names no account")
        val photo = header.getObject("accountPhoto").getArray("thumbnails").objects()
            .maxByOrNull { it.getInt("width", 0) }?.getString("url", null)
        return AccountProfile(
            name = header.getObject("accountName").runsText() ?: throw SessionExpired("youtube: the account has no name"),
            handle = header.getObject("channelHandle").runsText(),
            photoUri = photo,
        )
    }

    /** A grid or list of the account's library, such as its playlists or albums, as collections. */
    fun library(browseId: String): Page<Collection> = collections(api.browse(browseId = browseId))

    private fun collections(json: JsonObject): Page<Collection> {
        val entries = findAll(json, CARD).map { JsonObject(mapOf(CARD to it)) } +
            findAll(json, ROW).map { JsonObject(mapOf(ROW to it)) }
        val items = entries.mapNotNull { (item(it) as? SearchResult.Group)?.collection }
        return Page(items, nextPage(json, ::collections))
    }

    /**
     * Playlist [id]'s title (null when the page has none) and its songs, or null when the page
     * has neither, as for a playlist the account cannot see.
     */
    fun playlist(id: String): Pair<String?, Page<Song>>? {
        val json = api.browse(browseId = "VL$id")
        val title = (findObject(json, "musicResponsiveHeaderRenderer") ?: findObject(json, "musicDetailHeaderRenderer"))
            ?.getObject("title")?.runsText()
        val songs = songs(json)
        if (title == null && songs.items.isEmpty()) return null
        return title to songs
    }

    /**
     * Mix [playlistId] as YouTube Music queues it to play, page by page. A mix made for the
     * account, like My Supermix, only opens on the signed-in feed.
     */
    fun mix(playlistId: String): Page<Song> = queuePage(playlistId, api.queue(playlistId))

    private fun queuePage(playlistId: String, json: JsonObject): Page<Song> {
        val items = findAll(json, QUEUE_ITEM).mapNotNull { item ->
            val videoId = item.getString("videoId", null) ?: return@mapNotNull null
            val title = item.getObject("title").runsText() ?: return@mapNotNull null
            val length = item.getObject("lengthText").runsText()?.let(::clockMs) ?: 0
            val artwork = item.getObject("thumbnail").getArray("thumbnails").objects()
                .maxByOrNull { it.getInt("width", 0) }?.getString("url", null)
            song(videoId, title, artists(item.getObject("longBylineText")), artwork, length)
        }
        val token = findObject(json, "nextRadioContinuationData")?.getString("continuation", null)
        return Page(items, token?.let { { queuePage(playlistId, api.queue(playlistId, it)) } })
    }

    /** The songs of playlist [id] as the account sees it, so private playlists and liked songs open too. */
    fun playlistSongs(id: String): Page<Song> = songs(api.browse(browseId = "VL$id"))

    private fun songs(json: JsonObject): Page<Song> {
        val items = findAll(json, ROW).mapNotNull { (listSong(it) as? SearchResult.Track)?.song }
        return Page(items, nextPage(json, ::songs))
    }

    /**
     * How to fetch the page after [json], or null on the last one. YouTube sends the token in a
     * `continuationCommand` (asked for in the body) or, on older pages, in `nextContinuationData`.
     */
    private fun <T> nextPage(json: JsonObject, read: (JsonObject) -> Page<T>): (suspend () -> Page<T>)? {
        findObject(json, "continuationItemRenderer")?.let { findString(it, "token") }?.let { token ->
            return suspend { read(api.browseContinuation(token)) }
        }
        val token = findObject(json, "nextContinuationData")?.getString("continuation", null) ?: return null
        return suspend { read(api.browse(continuation = token)) }
    }

    /** One page of home and, when there is more, how to fetch the next one. */
    private fun homePage(list: SectionList): Page<Shelf> {
        val shelves = list.contents.mapNotNull(::shelf).filter { it.items.isNotEmpty() }
        val next = list.continuation?.let { token ->
            suspend { homePage(continuation(api.browse(continuation = token))) }
        }
        return Page(shelves, next)
    }

    private class SectionList(val contents: List<JsonObject>, val continuation: String?)

    private fun sections(json: JsonObject): SectionList {
        val list = json.getObject("contents").getObject("singleColumnBrowseResultsRenderer")
            .getArray("tabs").getObject(0).getObject("tabRenderer").getObject("content")
            .getObject("sectionListRenderer")
        return SectionList(list.getArray("contents").objects(), nextToken(list))
    }

    private fun continuation(json: JsonObject): SectionList {
        val list = json.getObject("continuationContents").getObject("sectionListContinuation")
        return SectionList(list.getArray("contents").objects(), nextToken(list))
    }

    private fun nextToken(list: JsonObject): String? =
        list.getArray("continuations").getObject(0).getObject("nextContinuationData").getString("continuation", null)

    /** A carousel of songs, albums, playlists or artists; null for anything else. */
    private fun shelf(section: JsonObject): Shelf? {
        val carousel = section.getObject("musicCarouselShelfRenderer").takeIf { it.isNotEmpty() } ?: return null
        val title = carousel.getObject("header").getObject("musicCarouselShelfBasicHeaderRenderer")
            .getObject("title").runsText() ?: return null
        val entries = carousel.getArray("contents").objects()
        val ranked = entries.any { it.getObject("musicResponsiveListItemRenderer").has("customIndexColumn") }
        return Shelf(title, entries.mapNotNull(::item), ranked)
    }

    /** The mood and genre buttons in a section, from a carousel or a grid. */
    private fun moodsIn(section: JsonObject): List<Mood> {
        val buttons = section.getObject("musicCarouselShelfRenderer").getArray("contents").objects() +
            section.getObject("gridRenderer").getArray("items").objects()
        return buttons.mapNotNull { entry ->
            val button = entry.getObject("musicNavigationButtonRenderer").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val endpoint = button.getObject("clickCommand").getObject("browseEndpoint")
            if (endpoint.getString("browseId", null) != MOOD_PAGE) return@mapNotNull null
            Mood(
                title = button.getObject("buttonText").runsText() ?: return@mapNotNull null,
                color = button.getObject("solid").getNumber("leftStripeColor")?.toLong(),
                ref = endpoint.getString("params", null) ?: return@mapNotNull null,
            )
        }
    }

    private fun item(entry: JsonObject): SearchResult? {
        entry.getObject("musicResponsiveListItemRenderer").takeIf { it.isNotEmpty() }?.let { return listSong(it) }
        val card = entry.getObject("musicTwoRowItemRenderer").takeIf { it.isNotEmpty() } ?: return null
        val title = card.getObject("title").runsText() ?: return null
        val subtitle = card.getObject("subtitle").runsText()
        val artwork = thumbnail(card.getObject("thumbnailRenderer"))
        val browse = card.getObject("navigationEndpoint").getObject("browseEndpoint")
        val collection = when (pageType(browse)) {
            ALBUM -> findString(card, "playlistId")?.let { playlist ->
                Collection(PLAYLIST_URL + playlist, CollectionKind.Album, title, subtitle, artwork)
            }
            PLAYLIST -> browse.getString("browseId", null)?.removePrefix("VL")?.let { playlist ->
                Collection(PLAYLIST_URL + playlist, CollectionKind.Playlist, title, subtitle, artwork)
            }
            ARTIST -> browse.getString("browseId", null)?.let { channel ->
                Collection(CHANNEL_URL + channel, CollectionKind.Artist, title, subtitle, artwork)
            }
            else -> null
        }
        if (collection != null) return SearchResult.Group(collection)
        // Anything else that can be played, like a music video, is a song.
        val videoId = findString(card, "videoId") ?: return null
        return SearchResult.Track(song(videoId, title, artists(card.getObject("subtitle")), artwork))
    }

    /** A list row: a song, or an artist or playlist that the row opens. */
    private fun listSong(row: JsonObject): SearchResult? {
        val columns = row.getArray("flexColumns").objects()
            .map { it.getObject("musicResponsiveListItemFlexColumnRenderer").getObject("text") }
        val title = columns.getOrNull(0)?.runsText() ?: return null
        val artwork = thumbnail(row.getObject("thumbnail"))
        val browse = row.getObject("navigationEndpoint").getObject("browseEndpoint")
        val opens = browse.getString("browseId", null)
        val subtitle = columns.getOrNull(1)?.runsText()
        val group = when (pageType(browse)) {
            ARTIST -> opens?.let { Collection(CHANNEL_URL + it, CollectionKind.Artist, title, subtitle, artwork) }
            PLAYLIST -> opens?.let { Collection(PLAYLIST_URL + it.removePrefix("VL"), CollectionKind.Playlist, title, subtitle, artwork) }
            else -> null
        }
        if (group != null) return SearchResult.Group(group)
        val videoId = row.getObject("playlistItemData").getString("videoId", null) ?: findString(row, "videoId") ?: return null
        val artist = columns.getOrNull(1)?.let(::artists)
        val length = row.getArray("fixedColumns").objects().firstOrNull()
            ?.getObject("musicResponsiveListItemFixedColumnRenderer")?.getObject("text")?.runsText()
        return SearchResult.Track(song(videoId, title, artist, artwork, length?.let(::clockMs) ?: 0))
    }

    private fun song(videoId: String, title: String, artist: String?, artwork: String?, durationMs: Long = 0) = Song(
        id = "yt:$videoId",
        title = title,
        artist = artist,
        album = null,
        durationMs = durationMs,
        uri = "hihu-yt://$videoId",
        artworkUri = artwork,
    )

    /** The names in a run list that link to artists, or the first run when none does. */
    private fun artists(text: JsonObject): String? {
        val runs = text.getArray("runs").objects()
        val linked = runs.filter { pageType(it.getObject("navigationEndpoint").getObject("browseEndpoint")) == ARTIST }
            .mapNotNull { it.getString("text", null) }
        return linked.joinToString(", ").ifEmpty { runs.firstOrNull()?.getString("text", null) }
    }

    private fun pageType(browse: JsonObject): String? = browse
        .getObject("browseEndpointContextSupportedConfigs").getObject("browseEndpointContextMusicConfig")
        .getString("pageType", null)

    /** The largest thumbnail, asked for at a size that suits a card. */
    private fun thumbnail(holder: JsonObject): String? {
        val url = holder.getObject("musicThumbnailRenderer").getObject("thumbnail").getArray("thumbnails").objects()
            .maxByOrNull { it.getInt("width", 0) }?.getString("url", null) ?: return null
        return url.replace(SIZE_WH, "=w$ARTWORK_SIZE-h$ARTWORK_SIZE")
    }

    private companion object {
        const val HOME = "FEmusic_home"
        const val LYRICS_PREFIX = "MPLY"
        const val CHARTS = "FEmusic_charts"
        const val ROW = "musicResponsiveListItemRenderer"
        const val CARD = "musicTwoRowItemRenderer"
        const val QUEUE_ITEM = "playlistPanelVideoRenderer"
        const val EXPLORE = "FEmusic_explore"
        const val MOOD_PAGE = "FEmusic_moods_and_genres_category"
        const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
        const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
        const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
        const val PLAYLIST_URL = "https://music.youtube.com/playlist?list="
        const val CHANNEL_URL = "https://www.youtube.com/channel/"
        const val ARTWORK_SIZE = 544
        val SIZE_WH = Regex("=w\\d+-h\\d+")
        val YEAR = Regex("\\b(19|20)\\d{2}\\b")
        /** The newest few of an artist's albums, and of their singles. */
        const val RELEASES_PER_SHELF = 2
    }
}

/** The objects in a JSON array, skipping anything else. */
private fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

/** The text of a `runs` holder, all runs joined, or null when empty. */
private fun JsonObject.runsText(): String? =
    getArray("runs").objects().joinToString("") { it.getString("text", "").orEmpty() }.trim().ifEmpty { null }

/** Every object under [key] anywhere inside [json], in order, without looking inside the ones found. */
private fun findAll(json: Any?, key: String): List<JsonObject> = when (json) {
    is JsonObject -> (json[key] as? JsonObject)?.let(::listOf) ?: json.values.flatMap { findAll(it, key) }
    is JsonArray -> json.flatMap { findAll(it, key) }
    else -> emptyList()
}

/** The first object under [key] anywhere inside [json], depth first. */
private fun findObject(json: Any?, key: String): JsonObject? = when (json) {
    is JsonObject -> (json[key] as? JsonObject) ?: json.values.firstNotNullOfOrNull { findObject(it, key) }
    is JsonArray -> json.firstNotNullOfOrNull { findObject(it, key) }
    else -> null
}

/** A length shown as `m:ss` or `h:mm:ss`, in milliseconds, or null when it is not one. */
private fun clockMs(text: String): Long? {
    val parts = text.split(':').map { it.toLongOrNull() ?: return null }
    if (parts.size !in 2..3) return null
    return parts.fold(0L) { total, part -> total * 60 + part } * 1000
}

/** The first string value under [key] anywhere inside [json], depth first. */
private fun findString(json: Any?, key: String): String? = when (json) {
    is JsonObject -> (json[key] as? String) ?: json.values.firstNotNullOfOrNull { findString(it, key) }
    is JsonArray -> json.firstNotNullOfOrNull { findString(it, key) }
    else -> null
}

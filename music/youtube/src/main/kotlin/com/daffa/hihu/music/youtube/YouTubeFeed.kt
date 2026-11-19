package com.daffa.hihu.music.youtube

import com.daffa.hihu.music.Collection
import com.daffa.hihu.music.CollectionKind
import com.daffa.hihu.music.ExploreFeed
import com.daffa.hihu.music.Mood
import com.daffa.hihu.music.Page
import com.daffa.hihu.music.SearchResult
import com.daffa.hihu.music.Shelf
import com.daffa.hihu.music.Song
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

    private fun listSong(row: JsonObject): SearchResult? {
        val columns = row.getArray("flexColumns").objects()
            .map { it.getObject("musicResponsiveListItemFlexColumnRenderer").getObject("text") }
        val title = columns.getOrNull(0)?.runsText() ?: return null
        val videoId = row.getObject("playlistItemData").getString("videoId", null) ?: findString(row, "videoId") ?: return null
        val artist = columns.getOrNull(1)?.let(::artists)
        return SearchResult.Track(song(videoId, title, artist, thumbnail(row.getObject("thumbnail"))))
    }

    private fun song(videoId: String, title: String, artist: String?, artwork: String?) = Song(
        id = "yt:$videoId",
        title = title,
        artist = artist,
        album = null,
        durationMs = 0,
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
        const val EXPLORE = "FEmusic_explore"
        const val MOOD_PAGE = "FEmusic_moods_and_genres_category"
        const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
        const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
        const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
        const val PLAYLIST_URL = "https://music.youtube.com/playlist?list="
        const val CHANNEL_URL = "https://www.youtube.com/channel/"
        const val ARTWORK_SIZE = 544
        val SIZE_WH = Regex("=w\\d+-h\\d+")
    }
}

/** The objects in a JSON array, skipping anything else. */
private fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

/** The text of a `runs` holder, all runs joined, or null when empty. */
private fun JsonObject.runsText(): String? =
    getArray("runs").objects().joinToString("") { it.getString("text", "").orEmpty() }.trim().ifEmpty { null }

/** The first string value under [key] anywhere inside [json], depth first. */
private fun findString(json: Any?, key: String): String? = when (json) {
    is JsonObject -> (json[key] as? String) ?: json.values.firstNotNullOfOrNull { findString(it, key) }
    is JsonArray -> json.firstNotNullOfOrNull { findString(it, key) }
    else -> null
}

package com.daffa.hihu.music.youtube

import com.daffa.hihu.music.AccountProfile
import com.daffa.hihu.music.AudioQuality
import com.daffa.hihu.music.Chart
import com.daffa.hihu.music.Collection
import com.daffa.hihu.music.ContentUnavailable
import com.daffa.hihu.music.LinkedPlaylist
import com.daffa.hihu.music.Lyrics
import com.daffa.hihu.music.LyricsProvider
import com.daffa.hihu.music.MusicAccount
import com.daffa.hihu.music.PlaylistLinks
import com.daffa.hihu.music.ExploreFeed
import com.daffa.hihu.music.Mood
import com.daffa.hihu.music.MusicCharts
import com.daffa.hihu.music.MusicFeed
import com.daffa.hihu.music.Shelf
import com.daffa.hihu.music.CollectionKind
import com.daffa.hihu.music.MusicBrowse
import com.daffa.hihu.music.MusicRadio
import com.daffa.hihu.music.MusicSearch
import com.daffa.hihu.music.Page
import com.daffa.hihu.music.ResolvedStream
import com.daffa.hihu.music.SearchKind
import com.daffa.hihu.music.SearchResult
import com.daffa.hihu.music.Song
import com.daffa.hihu.music.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePlaylistLinkHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.Page as NewPipePage

/**
 * YouTube Music through NewPipeExtractor, which also deals with YouTube's stream signatures.
 * Songs carry a `hihu-yt://<videoId>` URI; [resolve] turns it into a stream URL when it plays.
 * Only audio is ever fetched, music videos included. Create one per process: NewPipe keeps its
 * settings in global state.
 */
class YouTubeMusic(
    locale: Locale = Locale.getDefault(),
    /** Read every time a song is resolved, so a changed setting applies to the next song. */
    private val quality: () -> AudioQuality = { AudioQuality.Normal },
) : MusicSearch, MusicBrowse, MusicRadio, MusicCharts, MusicFeed, PlaylistLinks, LyricsProvider, MusicAccount, StreamResolver {

    private val service = ServiceList.YouTube
    private val resolved = ConcurrentHashMap<String, Cached>()

    private val feed: YouTubeFeed

    /** The signed-in session's cookies, or null. Only [account] sends them; everything else stays anonymous. */
    @Volatile
    private var session: String? = null
    private val account: YouTubeFeed

    init {
        val country = locale.country.ifBlank { FALLBACK_COUNTRY }
        val language = locale.language.ifBlank { "en" }
        NewPipe.init(HttpDownloader(), Localization(locale.language, country), ContentCountry(country))
        feed = YouTubeFeed(InnerTube(language, country))
        account = YouTubeFeed(InnerTube(language, country) { session })
    }

    override val signInUrl: String = SIGN_IN_URL

    override val sessionUrl: String = MUSIC_ORIGIN

    // YouTube signs the music API with SAPISID; without it the sign-in did not finish.
    override fun isSignedIn(cookies: String): Boolean =
        InnerTube.cookie(cookies, "SAPISID") != null || InnerTube.cookie(cookies, "__Secure-3PAPISID") != null

    override fun useSession(cookies: String?) {
        session = cookies
    }

    override suspend fun profile(): AccountProfile = io { account.profile() }

    override suspend fun savedPlaylists(): Page<Collection> = io { account.library(LIBRARY_PLAYLISTS) }.onIo()

    override suspend fun savedAlbums(): Page<Collection> = io { account.library(LIBRARY_ALBUMS) }.onIo()

    override suspend fun followedArtists(): Page<Collection> = io { account.library(LIBRARY_ARTISTS) }.onIo()

    override suspend fun home(): Page<Shelf> = io { feed.home() }.onIo()

    override suspend fun explore(): ExploreFeed = io { feed.explore() }

    override suspend fun mood(mood: Mood): List<Shelf> = io { feed.mood(mood) }

    override val scheme: String = SCHEME

    /** YouTube Music's own lyrics, plain text without times; only for songs from YouTube. */
    override suspend fun lyrics(song: Song): Lyrics? {
        if (!song.uri.startsWith("$SCHEME://")) return null
        val text = io { feed.lyrics(song.uri.removePrefix("$SCHEME://")) } ?: return null
        return Lyrics(synced = null, plain = text, source = LYRICS_SOURCE)
    }

    override suspend fun search(query: String, kind: SearchKind): Page<SearchResult> = io {
        val filter = when (kind) {
            SearchKind.Songs -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            SearchKind.Videos -> YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
            SearchKind.Albums -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            SearchKind.Artists -> YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
        }
        val extractor = service.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        page(extractor.initialPage, { io { extractor.getPage(it) } }, ::toResult)
    }

    override suspend fun suggestions(query: String): List<String> = io {
        service.suggestionExtractor.suggestionList(query)
    }

    override suspend fun songs(collection: Collection): Page<Song> = io {
        // Signed in, playlists open as the account sees them, so private ones and liked songs work.
        if (session != null && collection.kind == CollectionKind.Playlist) {
            val list = collection.id.substringAfter("list=", "").substringBefore('&')
            if (list.isNotEmpty()) return@io account.playlistSongs(list).onIo()
        }
        when (collection.kind) {
            CollectionKind.Album, CollectionKind.Playlist -> {
                val info = PlaylistInfo.getInfo(service, collection.id)
                val first = page(
                    InfoItemsPage(info.relatedItems, info.nextPage, emptyList()),
                    { io { PlaylistInfo.getMoreItems(service, collection.id, it) } },
                    ::toSong,
                )
                // Search results never carry an album's length, but the album page itself does.
                Page(first.items, first.next, info.streamCount.takeIf { it >= 0 })
            }
            CollectionKind.Artist -> {
                val channel = runCatching { ChannelInfo.getInfo(service, collection.id) }.getOrNull()
                val tabs = channel?.tabs.orEmpty()
                // YouTube Music artist channels come back without tabs, so fall back to the
                // artist's songs from a song search.
                val tab = tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
                    ?: tabs.firstOrNull { ChannelTabs.TRACKS in it.contentFilters }
                    ?: return@io songsBy(collection.title)
                val info = ChannelTabInfo.getInfo(service, tab)
                page(
                    InfoItemsPage(info.relatedItems, info.nextPage, emptyList()),
                    { io { ChannelTabInfo.getMoreItems(service, tab, it) } },
                    { item -> (item as? StreamInfoItem)?.let(::toSong) },
                )
            }
        }
    }

    override suspend fun playlist(link: String): LinkedPlaylist? = io {
        val handlers = YoutubePlaylistLinkHandlerFactory.getInstance()
        // Links from the share sheet carry tracking (`si=`) and may point at a video in the list.
        val url = runCatching { handlers.fromUrl(link).url }.getOrNull() ?: return@io null
        val info = try {
            PlaylistInfo.getInfo(service, url)
        } catch (e: ContentNotAvailableException) {
            throw ContentUnavailable("youtube: playlist unavailable", e)
        }
        val first = page(
            InfoItemsPage(info.relatedItems, info.nextPage, emptyList()),
            { io { PlaylistInfo.getMoreItems(service, url, it) } },
            ::toSong,
        )
        val collection = Collection(
            id = url,
            kind = CollectionKind.Playlist,
            title = info.name,
            subtitle = info.uploaderName?.takeIf { it.isNotBlank() },
            artworkUri = artwork(info.thumbnails),
            count = info.streamCount.takeIf { it >= 0 },
        )
        LinkedPlaylist(collection, Page(first.items, first.next, collection.count))
    }

    override suspend fun trending(): Chart = io {
        val country = NewPipe.getPreferredContentCountry().countryCode
        val language = NewPipe.getPreferredLocalization().languageCode
        // YouTube Charts covers about sixty countries; elsewhere, or if it fails, show the US chart.
        val songs = runCatching { YouTubeCharts.trending(country, language) }.getOrNull()?.takeIf { it.isNotEmpty() }
        when (songs) {
            null -> Chart(FALLBACK_COUNTRY, Page(YouTubeCharts.trending(FALLBACK_COUNTRY, language), null))
            else -> Chart(country, Page(songs, null))
        }
    }

    override fun hasRadio(song: Song): Boolean = song.uri.startsWith("$SCHEME://")

    override suspend fun radio(song: Song): Page<Song> = io {
        // YouTube Music's own radio for a song is the mix whose id is RDAMVM plus the video id.
        val id = song.uri.removePrefix("$SCHEME://")
        val url = "$WATCH_URL$id&list=RDAMVM$id"
        val info = PlaylistInfo.getInfo(service, url)
        page(
            InfoItemsPage(info.relatedItems, info.nextPage, emptyList()),
            { io { PlaylistInfo.getMoreItems(service, url, it) } },
        ) { item -> toSong(item)?.takeIf { it.id != song.id } }
    }

    override fun resolve(uri: String): ResolvedStream {
        val id = uri.removePrefix("$SCHEME://")
        val wanted = quality()
        val key = "$id@$wanted"
        resolved[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.stream }

        val info = StreamInfo.getInfo(service, "$WATCH_URL$id")
        val streams = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val audio = when (wanted) {
            AudioQuality.High -> streams.maxByOrNull { it.averageBitrate }
            AudioQuality.Normal -> streams.maxWithOrNull(compareBy<AudioStream>({ it.format == MediaFormat.M4A }, { it.averageBitrate }))
            AudioQuality.DataSaver -> streams.minByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: Int.MAX_VALUE }
        } ?: error("youtube: no playable audio stream for $id")

        val stream = ResolvedStream(audio.content, headersFor(audio.content))
        resolved[key] = Cached(stream, System.currentTimeMillis() + STREAM_TTL_MS)
        return stream
    }

    private class Cached(val stream: ResolvedStream, val expiresAt: Long)

    private companion object {
        const val SCHEME = "hihu-yt"
        const val MUSIC_ORIGIN = "https://music.youtube.com"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"
        const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
        const val LIBRARY_ALBUMS = "FEmusic_liked_albums"
        const val LIBRARY_ARTISTS = "FEmusic_library_corpus_artists"
        const val LYRICS_SOURCE = "YouTube Music"
        const val WATCH_URL = "https://www.youtube.com/watch?v="
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        const val FALLBACK_COUNTRY = "US"
        // Stream URLs are valid for about six hours; refresh well before that.
        const val STREAM_TTL_MS = 3L * 60 * 60 * 1000
        const val ARTWORK_SIZE = 544
    }

    /**
     * Wraps a NewPipe page, keeping what [map] accepts. The page after it is fetched with [fetch],
     * which closes over the extractor or URL the first page came from.
     */
    private fun <I : InfoItem, T> page(
        page: InfoItemsPage<I>,
        fetch: suspend (NewPipePage) -> InfoItemsPage<I>,
        map: (I) -> T?,
    ): Page<T> {
        val next = page.nextPage?.takeIf { NewPipePage.isValid(it) }
        return Page(page.items.mapNotNull(map), next?.let { { page(fetch(it), fetch, map) } })
    }

    /** Songs credited to [artist], from a song search for the name; other artists are dropped. */
    private fun songsBy(artist: String): Page<Song> {
        val extractor = service.getSearchExtractor(artist, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        extractor.fetchPage()
        val name = artist.lowercase(Locale.ROOT)
        return page(extractor.initialPage, { io { extractor.getPage(it) } }) { item ->
            (item as? StreamInfoItem)?.let(::toSong)?.takeIf { name in it.artist.orEmpty().lowercase(Locale.ROOT) }
        }
    }

    private fun toResult(item: InfoItem): SearchResult? = when (item) {
        is StreamInfoItem -> toSong(item)?.let(SearchResult::Track)
        is PlaylistInfoItem -> SearchResult.Group(
            Collection(
                id = item.url,
                kind = CollectionKind.Album,
                title = item.name,
                subtitle = item.uploaderName?.takeIf { it.isNotBlank() },
                artworkUri = artwork(item.thumbnails),
                count = item.streamCount.takeIf { it >= 0 },
            ),
        )
        is ChannelInfoItem -> SearchResult.Group(
            Collection(
                id = item.url,
                kind = CollectionKind.Artist,
                title = item.name,
                subtitle = null,
                artworkUri = artwork(item.thumbnails),
                count = item.subscriberCount.takeIf { it >= 0 },
            ),
        )
        else -> null
    }

    private fun toSong(item: StreamInfoItem): Song? {
        val id = runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(item.url) }.getOrNull() ?: return null
        return Song(
            id = "yt:$id",
            title = item.name,
            artist = item.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() },
            album = null,
            durationMs = item.duration.takeIf { it > 0 }?.times(1000) ?: 0,
            uri = "$SCHEME://$id",
            artworkUri = artwork(item.thumbnails),
        )
    }

    /** The largest thumbnail, asked for at a size that suits the full player. */
    private fun artwork(images: List<Image>): String? {
        val url = images.maxByOrNull { it.height * it.width }?.url ?: return null
        return url
            .replace(SIZE_WH, "=w$ARTWORK_SIZE-h$ARTWORK_SIZE")
            .replace(SIZE_S, "=s$ARTWORK_SIZE")
    }

    /** The headers a stream URL expects, which depend on the YouTube client it was issued to. */
    override fun invalidate(uri: String) {
        val prefix = uri.removePrefix("$SCHEME://") + "@"
        resolved.keys.removeIf { it.startsWith(prefix) }
    }

    private fun headersFor(url: String): Map<String, String> = when {
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
        YoutubeParsingHelper.isWebStreamingUrl(url) -> mapOf(
            "User-Agent" to HttpDownloader.USER_AGENT,
            "Origin" to YOUTUBE_ORIGIN,
            "Referer" to "$YOUTUBE_ORIGIN/",
        )
        else -> mapOf("User-Agent" to HttpDownloader.USER_AGENT)
    }
}

/** Makes every later page of [this] fetch on the IO dispatcher too, not just the first. */
private fun <T> Page<T>.onIo(): Page<T> = Page(items, next?.let { fetch -> suspend { io { fetch() }.onIo() } }, total)

/** NewPipe blocks on the network, so every call goes through here. */
private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

/** The size part of a googleusercontent image URL, as in `=w120-h120`. */
private val SIZE_WH = Regex("=w\\d+-h\\d+")

/** The size part of an avatar URL, as in `=s88`. */
private val SIZE_S = Regex("=s\\d+")

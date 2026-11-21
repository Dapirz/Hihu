package app.hihu.state

import android.content.Context
import app.hihu.music.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark, or whatever the system is set to. */
enum class Appearance { System, Light, Dark }

/** The colour family the theme is built from: the wallpaper's, or a fixed one. */
enum class Palette { Wallpaper, Violet, Blue, Teal, Green, Rose, Amber }

/**
 * The user's settings, kept in SharedPreferences so no extra library is needed. Each setting is
 * a [StateFlow] the UI and providers read; writes are saved straight away.
 */
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(read(KEY_APPEARANCE, Appearance.System))
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _palette = MutableStateFlow(read(KEY_PALETTE, Palette.Wallpaper))
    val palette: StateFlow<Palette> = _palette.asStateFlow()

    /** Pure black backgrounds in the dark theme, for AMOLED screens. */
    private val _black = MutableStateFlow(prefs.getBoolean(KEY_BLACK, false))
    val black: StateFlow<Boolean> = _black.asStateFlow()

    /** The streaming quality on Wi-Fi and other unmetered networks. */
    private val _quality = MutableStateFlow(read(KEY_QUALITY, AudioQuality.Normal))
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    /** The streaming quality on mobile data and other metered networks; at first the same as on Wi-Fi. */
    private val _qualityMobile = MutableStateFlow(read(KEY_QUALITY_MOBILE, _quality.value))
    val qualityMobile: StateFlow<AudioQuality> = _qualityMobile.asStateFlow()

    /**
     * Playing on as soon as headphones connect, while Hihu is paused in the background with
     * something queued. Off by default, so music never starts on its own unasked.
     */
    private val _resumeOnConnect = MutableStateFlow(prefs.getBoolean(KEY_RESUME_ON_CONNECT, false))
    val resumeOnConnect: StateFlow<Boolean> = _resumeOnConnect.asStateFlow()

    fun setResumeOnConnect(value: Boolean) {
        _resumeOnConnect.value = value
        prefs.edit().putBoolean(KEY_RESUME_ON_CONNECT, value).apply()
    }

    /** Turning songs louder than YouTube's target down, so the volume stays even. Off unless turned on, since it makes songs quieter on a phone speaker. */
    private val _normalize = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE, false))
    val normalize: StateFlow<Boolean> = _normalize.asStateFlow()

    fun setNormalize(value: Boolean) {
        _normalize.value = value
        prefs.edit().putBoolean(KEY_NORMALIZE, value).apply()
    }

    /** Going on with songs like the last one when a queue ends, instead of stopping. Off by default. */
    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, false))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    fun setAutoplay(value: Boolean) {
        _autoplay.value = value
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }

    fun setAppearance(value: Appearance) {
        _appearance.value = value
        prefs.edit().putString(KEY_APPEARANCE, value.name).apply()
    }

    fun setPalette(value: Palette) {
        _palette.value = value
        prefs.edit().putString(KEY_PALETTE, value.name).apply()
    }

    fun setBlack(value: Boolean) {
        _black.value = value
        prefs.edit().putBoolean(KEY_BLACK, value).apply()
    }

    fun setQuality(value: AudioQuality) {
        _quality.value = value
        prefs.edit().putString(KEY_QUALITY, value.name).apply()
    }

    /** The quality songs are downloaded in; High at first, since a download is played many times. */
    private val _qualityDownload = MutableStateFlow(read(KEY_QUALITY_DOWNLOAD, AudioQuality.High))
    val qualityDownload: StateFlow<AudioQuality> = _qualityDownload.asStateFlow()

    fun setQualityDownload(value: AudioQuality) {
        _qualityDownload.value = value
        prefs.edit().putString(KEY_QUALITY_DOWNLOAD, value.name).apply()
    }

    /** Downloading each song as it is hearted, over Wi-Fi only. Off by default. */
    private val _downloadLikes = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_LIKES, false))
    val downloadLikes: StateFlow<Boolean> = _downloadLikes.asStateFlow()

    fun setDownloadLikes(value: Boolean) {
        _downloadLikes.value = value
        prefs.edit().putBoolean(KEY_DOWNLOAD_LIKES, value).apply()
        if (!value) setPendingDownloads(emptySet())
    }

    /** Downloading hearted songs on mobile data too, not only on Wi-Fi. Off by default. */
    private val _downloadLikesMobile = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_LIKES_MOBILE, false))
    val downloadLikesMobile: StateFlow<Boolean> = _downloadLikesMobile.asStateFlow()

    fun setDownloadLikesMobile(value: Boolean) {
        _downloadLikesMobile.value = value
        prefs.edit().putBoolean(KEY_DOWNLOAD_LIKES_MOBILE, value).apply()
    }

    /** Hearted songs waiting for Wi-Fi to be downloaded. */
    fun pendingDownloads(): Set<String> = prefs.getStringSet(KEY_PENDING_DOWNLOADS, null).orEmpty().toSet()

    fun setPendingDownloads(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_PENDING_DOWNLOADS, ids).apply()
    }

    /** How large lyrics are drawn, a step of [LYRICS_SIZES]; the second, 100%, at first. */
    private val _lyricsSize = MutableStateFlow(prefs.getInt(KEY_LYRICS_SIZE, 1).coerceIn(LYRICS_SIZES.indices))
    val lyricsSize: StateFlow<Int> = _lyricsSize.asStateFlow()

    fun setLyricsSize(value: Int) {
        val step = value.coerceIn(LYRICS_SIZES.indices)
        _lyricsSize.value = step
        prefs.edit().putInt(KEY_LYRICS_SIZE, step).apply()
    }

    fun setQualityMobile(value: AudioQuality) {
        _qualityMobile.value = value
        prefs.edit().putString(KEY_QUALITY_MOBILE, value.name).apply()
    }

    private val sorts = SortedList.entries.associateWith { list ->
        MutableStateFlow(read(sortKey(list), defaultSort(list)))
    }
    private val sortFlows = sorts.mapValues { it.value.asStateFlow() }

    /** How [list] is ordered in the library. */
    fun sort(list: SortedList): StateFlow<LibrarySort> = sortFlows.getValue(list)

    fun setSort(list: SortedList, value: LibrarySort) {
        sorts.getValue(list).value = value
        prefs.edit().putString(sortKey(list), value.name).apply()
    }

    private fun sortKey(list: SortedList) = "sort_" + list.name.lowercase()

    /** Files on the phone have no date added here, so they start by title; the rest newest first. */
    private fun defaultSort(list: SortedList) = when (list) {
        SortedList.Device -> LibrarySort.Title
        SortedList.Favorites, SortedList.Downloads, SortedList.Playlists -> LibrarySort.Added
    }

    /** Reads an enum by name, falling back to [default] when unset or no longer known. */
    private inline fun <reified E : Enum<E>> read(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val FILE = "settings"
        const val KEY_NORMALIZE = "normalize_volume"
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_PALETTE = "palette"
        const val KEY_BLACK = "pure_black"
        const val KEY_QUALITY = "audio_quality"
        const val KEY_QUALITY_MOBILE = "audio_quality_mobile"
        const val KEY_QUALITY_DOWNLOAD = "audio_quality_download"
        const val KEY_LYRICS_SIZE = "lyrics_size"
        const val KEY_DOWNLOAD_LIKES = "download_likes"
        const val KEY_DOWNLOAD_LIKES_MOBILE = "download_likes_mobile"
        const val KEY_PENDING_DOWNLOADS = "pending_like_downloads"
        const val KEY_RESUME_ON_CONNECT = "resume_on_connect"
    }
}

/**
 * How a list in the library is ordered: as it was added (newest first), by title, by artist, or,
 * for playlists, by how many songs they hold.
 */
enum class LibrarySort { Added, Title, Artist, Count }

/** The sizes lyrics can be drawn at, as a share of the normal size. */
val LYRICS_SIZES = listOf(0.9f, 1f, 1.2f, 1.4f)

/** The library lists the user can put in another order. */
enum class SortedList { Favorites, Downloads, Device, Playlists }

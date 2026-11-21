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
        SortedList.Favorites, SortedList.Downloads -> LibrarySort.Added
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
        const val KEY_RESUME_ON_CONNECT = "resume_on_connect"
    }
}

/** How a list in the library is ordered: as it was added (newest first), by title, or by artist. */
enum class LibrarySort { Added, Title, Artist }

/** The library lists the user can put in another order. */
enum class SortedList { Favorites, Downloads, Device }

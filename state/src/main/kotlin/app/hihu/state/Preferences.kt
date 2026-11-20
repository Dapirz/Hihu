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

    private val _quality = MutableStateFlow(read(KEY_QUALITY, AudioQuality.Normal))
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    /** Turning songs louder than YouTube's target down, so the volume stays even. */
    private val _normalize = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE, true))
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
    }
}

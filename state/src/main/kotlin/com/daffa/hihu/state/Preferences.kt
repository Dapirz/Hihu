package com.daffa.hihu.state

import android.content.Context
import com.daffa.hihu.music.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark, or whatever the system is set to. More themes slot in here later. */
enum class Appearance { System, Light, Dark }

/**
 * The user's settings, kept in SharedPreferences so no extra library is needed. Each setting is
 * a [StateFlow] the UI and providers read; writes are saved straight away.
 */
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(read(KEY_APPEARANCE, Appearance.System))
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _quality = MutableStateFlow(read(KEY_QUALITY, AudioQuality.Normal))
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    fun setAppearance(value: Appearance) {
        _appearance.value = value
        prefs.edit().putString(KEY_APPEARANCE, value.name).apply()
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
        const val KEY_APPEARANCE = "appearance"
        const val KEY_QUALITY = "audio_quality"
    }
}

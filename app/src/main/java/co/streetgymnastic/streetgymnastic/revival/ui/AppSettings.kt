package co.streetgymnastic.streetgymnastic.revival.ui

import android.content.Context
import java.util.Locale

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var keepScreenAwake: Boolean
        get() = preferences.getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
        set(value) = preferences.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, value).apply()

    var soundCues: Boolean
        get() = preferences.getBoolean(KEY_SOUND_CUES, true)
        set(value) = preferences.edit().putBoolean(KEY_SOUND_CUES, value).apply()

    fun selectedLocale(): Locale = Locale.ENGLISH

    companion object {
        private const val PREFERENCES_NAME = "street_gymnastic_settings"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_SOUND_CUES = "sound_cues"
    }
}

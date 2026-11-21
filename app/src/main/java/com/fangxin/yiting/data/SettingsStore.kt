package com.fangxin.yiting.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 朗读开关，默认全开，只存在本机。 */
class SettingsStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _screenSpeech = MutableStateFlow(prefs.getBoolean(KEY_SCREEN, true))

    /** 切到新页面时，把这一屏要紧的话念一遍。 */
    val screenSpeech: StateFlow<Boolean> = _screenSpeech.asStateFlow()

    private val _replySpeech = MutableStateFlow(prefs.getBoolean(KEY_REPLY, true))

    /** 判定结论、小望的回话、家人端收到的提醒，用声音说出来。 */
    val replySpeech: StateFlow<Boolean> = _replySpeech.asStateFlow()

    fun setScreenSpeech(on: Boolean) {
        _screenSpeech.value = on
        prefs.edit().putBoolean(KEY_SCREEN, on).apply()
    }

    fun setReplySpeech(on: Boolean) {
        _replySpeech.value = on
        prefs.edit().putBoolean(KEY_REPLY, on).apply()
    }

    companion object {
        private const val KEY_SCREEN = "screen_speech"
        private const val KEY_REPLY = "reply_speech"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}

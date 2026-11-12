package com.myra.assistant.utils

import android.content.Context
import com.myra.assistant.BuildConfig

/**
 * Gemini key used by the app. A key the user typed in Settings wins; otherwise the key that was injected at BUILD time
 * (GEMINI_DEFAULT_KEY, see README / CHANGES) is used, so people can use the app without setup.
 * The key is NOT stored in the source code or in git.
 *
 * NOTE: a key inside a built APK can still be extracted by someone determined - limit its quota/budget in Google AI Studio.
 */
object ApiKeys {
    fun gemini(context: Context): String {
        val own = context.applicationContext.prefs().getString(Constants.KEY_API_KEY, "")?.trim().orEmpty()
        return if (own.isNotEmpty()) own else BuildConfig.DEFAULT_GEMINI_KEY
    }
}

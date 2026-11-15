package com.myra.assistant.utils

import android.util.Log

object Logger {
    private const val TAG_PREFIX = "MYRA_"

    fun d(tag: String, msg: String) = Log.d(TAG_PREFIX + tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG_PREFIX + tag, msg, t)
        else Log.e(TAG_PREFIX + tag, msg)
    }
    fun w(tag: String, msg: String) = Log.w(TAG_PREFIX + tag, msg)
    fun i(tag: String, msg: String) = Log.i(TAG_PREFIX + tag, msg)
}

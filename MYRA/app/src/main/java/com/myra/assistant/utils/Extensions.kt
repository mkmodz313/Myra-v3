package com.myra.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.Toast

fun Context.toast(msg: String, long: Boolean = false) {
    Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Context.prefs(): SharedPreferences =
    getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun String.containsIgnoreCase(other: String) = this.lowercase().contains(other.lowercase())

fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

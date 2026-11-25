
package com.axemote.app

import android.content.Context

object OverlayPrefs {
    private const val FILE = "ax_overlay_state"
    private const val KEY = "enabled"

    fun isOn(context: Context): Boolean {
        return context.getSharedPreferences(
            FILE, Context.MODE_PRIVATE
        ).getBoolean(KEY, false)
    }

    fun setOn(context: Context, value: Boolean) {
        context.getSharedPreferences(
            FILE, Context.MODE_PRIVATE
        ).edit().putBoolean(KEY, value).apply()
    }
}

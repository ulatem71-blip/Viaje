package com.viajerentable.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** One source of truth for the app's day/night visual palette. */
object VisualTheme {
    data class Palette(
        val background: Int,
        val card: Int,
        val brand: Int,
        val accept: Int,
        val reject: Int,
        val neutral: Int,
        val warning: Int,
        val border: Int,
        val text: Int,
        val muted: Int,
        val darkText: Int
    )

    fun palette(context: Context): Palette {
        val night = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Palette(
                background = Color.rgb(15, 23, 36),
                card = Color.rgb(24, 34, 53),
                brand = Color.rgb(20, 184, 166),
                accept = Color.rgb(34, 197, 94),
                reject = Color.rgb(239, 68, 68),
                neutral = Color.rgb(148, 163, 184),
                warning = Color.rgb(245, 158, 11),
                border = Color.rgb(52, 71, 106),
                text = Color.rgb(241, 245, 249),
                muted = Color.rgb(166, 180, 201),
                darkText = Color.rgb(7, 24, 31)
            )
        } else {
            Palette(
                background = Color.rgb(244, 246, 250),
                card = Color.WHITE,
                brand = Color.rgb(20, 184, 166),
                accept = Color.rgb(22, 163, 74),
                reject = Color.rgb(220, 38, 38),
                neutral = Color.rgb(100, 116, 139),
                warning = Color.rgb(180, 83, 9),
                border = Color.rgb(182, 194, 212),
                text = Color.rgb(15, 23, 42),
                muted = Color.rgb(71, 85, 105),
                darkText = Color.rgb(7, 24, 31)
            )
        }
    }
}

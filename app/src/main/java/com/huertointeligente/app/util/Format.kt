package com.huertointeligente.app.util

import android.content.Context
import android.text.format.DateUtils
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.huertointeligente.app.R
import com.huertointeligente.app.domain.Level
import java.util.Locale

fun Float.fmt0(): String = String.format(Locale.getDefault(), "%.0f", this)

fun Float.fmt1(): String = String.format(Locale.getDefault(), "%.1f", this)

fun Float.fmt2(): String = String.format(Locale.getDefault(), "%.2f", this)

/** "hace 2 min", "hace 3 h", etc. */
fun relativeTime(timestamp: Long): CharSequence = DateUtils.getRelativeTimeSpanString(
    timestamp,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE
)

/** Color del tema (por ejemplo colorOnSurface) con un valor de respaldo. */
fun Context.themeColor(@AttrRes attr: Int, fallback: Int): Int {
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true)) {
        if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    } else fallback
}

fun Context.levelColor(level: Level): Int = ContextCompat.getColor(
    this,
    when (level) {
        Level.LOW -> R.color.status_warn
        Level.OPTIMAL -> R.color.status_ok
        Level.HIGH -> R.color.status_bad
    }
)

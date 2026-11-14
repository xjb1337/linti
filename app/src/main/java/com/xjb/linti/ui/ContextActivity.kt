package com.xjb.linti.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        val next = (current as? ContextWrapper)?.baseContext
        if (next === current) return null
        current = next
    }
    return null
}

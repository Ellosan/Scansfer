package com.scansfer.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose hands out a wrapped context; dig out the hosting Activity. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

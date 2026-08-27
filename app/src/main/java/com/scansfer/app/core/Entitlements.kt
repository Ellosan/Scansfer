package com.scansfer.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scansfer.app.R

/**
 * Whether this install has premium, and how it got there.
 *
 * Redeeming is deliberately not tied to the device: a code survives a reinstall
 * or a new phone, which matters more for someone who paid than the sharing it
 * allows. There is no account and nothing leaves the device.
 */
class Entitlements(private val context: Context) {

    private val prefs = context.getSharedPreferences("scansfer.entitlements", Context.MODE_PRIVATE)

    var isPremium by mutableStateOf(prefs.getBoolean(KEY_PREMIUM, false))
        private set

    /** The code that unlocked this install, for the user's own reference. */
    val redeemedCode: String? get() = prefs.getString(KEY_CODE, null)

    private val table: ByteArray by lazy {
        context.resources.openRawResource(R.raw.unlock_codes).use { it.readBytes() }
    }

    /** @return true when the code was accepted; premium is then on for good. */
    fun redeem(code: String): Boolean {
        if (!Unlock.isValid(code, table)) return false
        prefs.edit()
            .putBoolean(KEY_PREMIUM, true)
            .putString(KEY_CODE, Unlock.format(code))
            .apply()
        isPremium = true
        return true
    }

    private companion object {
        const val KEY_PREMIUM = "premium"
        const val KEY_CODE = "code"
    }
}

object Premium {
    /**
     * Where buyers get a code. While this is blank the unlock screen simply
     * omits its purchase button rather than pointing nowhere.
     */
    const val PURCHASE_URL = "https://ellosan.itch.io/scansfer-premium"
}

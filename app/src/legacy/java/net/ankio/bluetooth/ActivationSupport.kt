package net.ankio.bluetooth

import android.app.Application

/**
 * Legacy-flavor activation. Nothing to do at app startup: LegacyStatusEntry reports activation
 * from inside the hooked app process, and the hook reads configuration via XSharedPreferences.
 */
object ActivationSupport {
    @Suppress("UNUSED_PARAMETER")
    fun install(app: Application) {
        // no-op
    }
}

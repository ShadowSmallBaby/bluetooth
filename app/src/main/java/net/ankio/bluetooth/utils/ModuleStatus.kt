package net.ankio.bluetooth.utils

import androidx.annotation.Keep

/** Activation in this app process; this does not verify Bluetooth scan injection. */
@Keep
object ModuleStatus {
    enum class Framework(val minimumApiVersion: Int) {
        LEGACY_XPOSED(93),
        LIBXPOSED(102)
    }

    data class Activation(val framework: Framework, val apiVersion: Int) {
        val isSupported: Boolean
            get() = apiVersion >= framework.minimumApiVersion
    }

    @Volatile
    var activation: Activation? = null
        private set

    val isActive: Boolean
        get() = activation != null

    val isSupported: Boolean
        get() = activation?.isSupported == true

    val usesLegacyPreferences: Boolean
        get() = activation?.let { it.framework == Framework.LEGACY_XPOSED && it.isSupported } == true

    /** Called through the app class loader by the module entry, before Application.onCreate. */
    @Suppress("unused") // Invoked reflectively across the framework/app class loader boundary.
    @JvmStatic
    fun reportActivation(apiVersion: Int, modern: Boolean) {
        activation = Activation(
            if (modern) Framework.LIBXPOSED else Framework.LEGACY_XPOSED,
            apiVersion
        )
    }
}

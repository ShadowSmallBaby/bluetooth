package net.ankio.bluetooth

import android.app.Application
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import net.ankio.bluetooth.config.HookSettings
import net.ankio.bluetooth.utils.ModuleStatus
import net.ankio.bluetooth.utils.sp

/**
 * Modern-flavor activation. When the LibXposed framework service binds, the module is active, so we
 * mark it activated and publish the hook config snapshot into the shared remote preferences — the
 * only channel the hooked process can read (plain app SharedPreferences are not bridged).
 */
object ActivationSupport {
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(app: Application) {
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    ModuleStatus.reportActivation(service.apiVersion, modern = true)
                    publish(service)
                    observe(service)
                }

                override fun onServiceDied(service: XposedService) {}
            })
        } catch (_: Throwable) {
            // No modern service available; nothing to do.
        }
    }

    private fun publish(service: XposedService) {
        try {
            val snapshot = HookSettings.fromPreferences(sp.all).encode()
            service.getRemotePreferences(HookSettings.GROUP).edit()
                .putString(HookSettings.SNAPSHOT, snapshot).apply()
        } catch (_: Throwable) {
        }
    }

    private fun observe(service: XposedService) {
        if (listener != null) return
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> publish(service) }
        listener = l
        sp.registerOnSharedPreferenceChangeListener(l)
    }
}

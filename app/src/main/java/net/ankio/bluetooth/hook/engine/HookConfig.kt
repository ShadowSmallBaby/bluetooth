package net.ankio.bluetooth.hook.engine

import net.ankio.bluetooth.config.HookSettings

/** Framework adapters supply only the raw snapshot. Decoding is shared and cached. */
class HookConfig(private val readSnapshot: () -> String?, private val log: HookLog) {
    private var previousJson: String? = null
    private var settings = HookSettings()
    private var failed = false

    @Synchronized
    fun read(): HookSettings = try {
        val json = readSnapshot()
        if (json != previousJson) {
            settings = HookSettings.decode(json)
            previousJson = json
        }
        failed = false
        settings
    } catch (error: Exception) {
        if (!failed) log.write("Cannot read Hook configuration; simulation disabled", error)
        failed = true
        HookSettings()
    }
}


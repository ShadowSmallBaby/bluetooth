package net.ankio.bluetooth.config

import com.google.gson.Gson
import com.google.gson.JsonParser

/** A single preference value is the transaction boundary in both framework transports. */
data class HookSettings(
    val schemaVersion: Int = 1,
    val enabled: Boolean = false,
    val logEnabled: Boolean = false,
    val mac: String = "",
    val rssi: String = "-50",
    val data: String = "",
    val rulesJson: String? = null,
    val previousRulesJson: String? = null
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        const val GROUP = "hook"
        const val SNAPSHOT = "snapshot"

        fun fromPreferences(values: Map<String, *>): HookSettings {
            fun string(key: String, fallback: String = "") = values[key] as? String ?: fallback
            fun boolean(key: String) = when (val value = values[key]) {
                is Boolean -> value
                is String -> value.toBooleanStrictOrNull() ?: false
                else -> false
            }
            return HookSettings(
                enabled = boolean(PreferenceKeys.ENABLED), logEnabled = boolean(PreferenceKeys.HOOK_LOG),
                mac = string(PreferenceKeys.MAC), rssi = string(PreferenceKeys.RSSI, "-50"),
                data = string(PreferenceKeys.DATA),
                rulesJson = string(PreferenceKeys.HOOK_RULES).ifBlank { null },
                previousRulesJson = string(PreferenceKeys.PREVIOUS_HOOK_RULES).ifBlank { null }
            )
        }

        fun decode(json: String?): HookSettings {
            if (json.isNullOrBlank()) return HookSettings()
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("schemaVersion")?.asInt == 1) { "Unsupported configuration version" }
            fun string(key: String, fallback: String = "") = root.get(key)?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Invalid $key" }
                it.asString
            } ?: fallback
            fun boolean(key: String) = root.get(key)?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "Invalid $key" }
                it.asBoolean
            } ?: false
            return HookSettings(
                enabled = boolean("enabled"), logEnabled = boolean("logEnabled"),
                mac = string("mac"), rssi = string("rssi", "-50"), data = string("data"),
                rulesJson = string("rulesJson").ifBlank { null },
                previousRulesJson = string("previousRulesJson").ifBlank { null }
            )
        }
    }
}

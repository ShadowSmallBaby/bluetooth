package net.ankio.bluetooth.config

/** Single source of truth for persisted preference names. Keep values stable across upgrades. */
object PreferenceKeys {
    // Simulation / hook
    const val ENABLED = "pref_enable"
    const val MAC = "pref_mac"
    const val DATA = "pref_data"
    const val RSSI = "pref_rssi"
    const val HOOK_LOG = "pref_hook_log_enabled"
    const val HOOK_RULES = "hook_rules"
    const val PREVIOUS_HOOK_RULES = "previous_hook_rules"
    const val RULE_URL = "hook_rule_url"

    // WebDAV sync
    const val WEBDAV_ENABLED = "pref_enable_webdav"
    const val WEBDAV_SENDER = "pref_as_sender"
    const val WEBDAV_MAC = "pref_mac2"
    const val WEBDAV_COMPANY = "pref_company"
    const val WEBDAV_SERVER = "webdav_server"
    const val WEBDAV_USERNAME = "webdav_username"
    const val WEBDAV_PASSWORD = "webdav_password"
    const val WEBDAV_LAST = "webdav_last"

    // History & BLE advertiser
    const val HISTORY = "history"
    const val BLE_ADVERTISER_ENABLED = "pref_ble_advertiser_enabled"

    // App-level
    const val ANALYZE = "app_center_analyze"
    const val LANGUAGE = "setting_language"
    const val HIDE_ICON = "hide_icon"
    const val APP_VERSION = "app_version"
}

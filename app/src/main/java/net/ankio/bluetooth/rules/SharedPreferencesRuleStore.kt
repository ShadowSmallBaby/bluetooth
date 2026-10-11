package net.ankio.bluetooth.rules

import net.ankio.bluetooth.config.PreferenceKeys
import net.ankio.bluetooth.utils.SpUtils

/**
 * Backs the rule store with the module app's "config" preferences. The hook reads the very same
 * keys through its configuration snapshot, so an installed update reaches the hook without any
 * extra transport.
 */
class SharedPreferencesRuleStore : RuleStore {
    override val active: String?
        get() = SpUtils.getString(PreferenceKeys.HOOK_RULES, "").ifBlank { null }

    override val previous: String?
        get() = SpUtils.getString(PreferenceKeys.PREVIOUS_HOOK_RULES, "").ifBlank { null }

    override fun replace(active: String?, previous: String?) {
        SpUtils.putString(PreferenceKeys.HOOK_RULES, active ?: "")
        SpUtils.putString(PreferenceKeys.PREVIOUS_HOOK_RULES, previous ?: "")
    }
}

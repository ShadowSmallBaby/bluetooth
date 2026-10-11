package net.ankio.bluetooth.hook
import net.ankio.bluetooth.hook.model.*
import net.ankio.bluetooth.hook.engine.*

import android.os.Build
import net.ankio.bluetooth.config.HookSettings
import net.ankio.bluetooth.rules.RuleRepository

/**
 * Shared, framework-neutral startup. Both the legacy and modern entries load the same rules,
 * select the matching profile for the loaded package and plan the hook through this object, so
 * the two frameworks behave identically.
 *
 * The active rules are chosen from, in order: the user's downloaded rules, the previously
 * installed rules, and finally the rules bundled on the module classpath. A broken download can
 * therefore never take the simulation offline.
 */
object HookBootstrap {
    private const val BUNDLED_RULES = "/hook_rules.json"

    fun bundledRules(loader: ClassLoader): String {
        val stream = HookBootstrap::class.java.getResourceAsStream(BUNDLED_RULES)
            ?: loader.getResourceAsStream(BUNDLED_RULES)
            ?: error("Bundled hook rules are missing from the module")
        return stream.use { RuleRepository.readLimited(it) }
    }

    fun selectRules(settings: HookSettings, bundled: String): HookRules =
        RuleRepository.candidates(settings.rulesJson, settings.previousRulesJson, bundled)
            .first().rules

    /** Returns the first profile that both targets this package and plans against a present class. */
    fun planFor(packageName: String, rules: HookRules, loader: ClassLoader): Pair<HookProfile, HookPlan>? {
        val sdk = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER ?: ""
        for (profile in rules.profiles) {
            if (!profile.appliesTo(packageName, sdk, manufacturer)) continue
            val plan = HookPlanner.plan(profile, loader) ?: continue
            return profile to plan
        }
        return null
    }
}

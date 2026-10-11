package net.ankio.bluetooth.rules

import net.ankio.bluetooth.hook.model.HookRules
import java.io.InputStream
import java.util.zip.ZipFile

interface RuleStore {
    val active: String?
    val previous: String?
    fun replace(active: String?, previous: String?)
}

data class SelectedRules(val rules: HookRules, val json: String, val source: String)

/** Validate before the atomic store write; a rejected download cannot replace working rules. */
class RuleRepository(private val bundled: String, private val store: RuleStore) {
    init { HookRules.parse(bundled) }

    fun current(): SelectedRules = candidates(store.active, store.previous, bundled).first()

    fun install(json: String): SelectedRules {
        val incoming = HookRules.parse(json)
        val current = current()
        if (incoming == current.rules) return current
        require(incoming.revision > current.rules.revision) { "Rule revision must be newer than ${current.rules.revision}" }
        store.replace(json, current.json)
        return SelectedRules(incoming, json, "custom")
    }

    fun rollback(): SelectedRules {
        val previous = store.previous ?: error("No previous rules")
        val parsed = HookRules.parse(previous)
        store.replace(previous, current().json)
        return SelectedRules(parsed, previous, "previous")
    }

    fun reset() = store.replace(null, null)

    companion object {
        fun candidates(active: String?, previous: String?, bundled: String): List<SelectedRules> =
            listOf("custom" to active, "previous" to previous, "bundled" to bundled)
                .mapNotNull { (source, json) ->
                    if (json.isNullOrBlank()) null else try {
                        SelectedRules(HookRules.parse(json), json, source)
                    } catch (_: Exception) { null }
                }.distinctBy { it.rules }

        fun readBundledApk(moduleApk: String): String = ZipFile(moduleApk).use { zip ->
            // Rules ship as a Java resource packaged at the APK root; the single source of truth is
            // app/src/main/resources/hook_rules.json.
            val entry = zip.getEntry("hook_rules.json") ?: error("Module has no bundled hook rules")
            zip.getInputStream(entry).use(::readLimited)
        }

        fun readLimited(input: InputStream): String {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= HookRules.MAX_BYTES) { "Rule file exceeds 128 KiB" }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}

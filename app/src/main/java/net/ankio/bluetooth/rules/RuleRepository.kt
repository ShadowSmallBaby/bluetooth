package net.ankio.bluetooth.rules

import net.ankio.bluetooth.hook.model.HookRules
import java.io.InputStream

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

    fun hasPrevious(): Boolean = !store.previous.isNullOrBlank()

    /**
     * Installs [json] over the active rules. Remote updates must increase the revision so a stale
     * mirror cannot roll the rules back; a locally imported file may keep the same revision, which
     * lets a hand-edited copy replace the current one while debugging.
     */
    fun install(json: String, allowSameRevision: Boolean = false): SelectedRules {
        val incoming = HookRules.parse(json)
        val current = current()
        if (incoming == current.rules) return current
        require(if (allowSameRevision) incoming.revision >= current.rules.revision
                else incoming.revision > current.rules.revision) {
            "Rule revision must be newer than ${current.rules.revision}"
        }
        store.replace(json, current.json)
        return SelectedRules(incoming, json, "custom")
    }

    fun rollback(): SelectedRules {
        val previous = store.previous ?: error("No previous rules")
        val parsed = HookRules.parse(previous)
        store.replace(previous, current().json)
        return SelectedRules(parsed, previous, "previous")
    }

    companion object {
        fun candidates(active: String?, previous: String?, bundled: String): List<SelectedRules> =
            listOf("custom" to active, "previous" to previous, "bundled" to bundled)
                .mapNotNull { (source, json) ->
                    if (json.isNullOrBlank()) null else try {
                        SelectedRules(HookRules.parse(json), json, source)
                    } catch (_: Exception) { null }
                }.distinctBy { it.rules }

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

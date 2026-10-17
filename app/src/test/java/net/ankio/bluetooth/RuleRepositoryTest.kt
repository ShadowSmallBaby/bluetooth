package net.ankio.bluetooth

import net.ankio.bluetooth.hook.model.HookRules
import net.ankio.bluetooth.rules.RuleRepository
import net.ankio.bluetooth.rules.RuleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RuleRepositoryTest {
    private val bundled =
        javaClass.getResourceAsStream("/hook_rules.json")!!.use { RuleRepository.readLimited(it) }
    private val bundledRevision = HookRules.parse(bundled).revision

    /** A revision strictly newer than the bundled one, derived so a rules bump cannot break this. */
    private val newerRevision = bundledRevision + 1
    private val newerRules = bundled.replace("\"revision\": $bundledRevision", "\"revision\": $newerRevision")

    private class FakeStore(
        override var active: String? = null,
        override var previous: String? = null
    ) : RuleStore {
        override fun replace(active: String?, previous: String?) {
            this.active = active
            this.previous = previous
        }
    }

    @Test
    fun installNewerRevisionReplacesActiveAndKeepsPrevious() {
        val store = FakeStore()
        val result = RuleRepository(bundled, store).install(newerRules)
        assertEquals(newerRevision, result.rules.revision)
        assertEquals("custom", result.source)
        assertNotNull(store.active)
        assertEquals(bundled, store.previous)
    }

    @Test
    fun rollbackRestoresPreviousRules() {
        val store = FakeStore()
        val repository = RuleRepository(bundled, store)
        repository.install(newerRules)
        assertEquals(bundledRevision, repository.rollback().rules.revision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun installingOlderOrEqualRevisionIsRejected() {
        val store = FakeStore(active = newerRules)
        RuleRepository(bundled, store).install(bundled)
    }

    @Test
    fun localImportMayReplaceTheSameRevision() {
        val store = FakeStore()
        val repository = RuleRepository(bundled, store)
        val edited = bundled.replace("\"intervalMs\": 500", "\"intervalMs\": 600")

        val result = repository.install(edited, allowSameRevision = true)

        assertEquals(bundledRevision, result.rules.revision)
        assertEquals(edited, store.active)
        assertEquals(600, result.rules.profiles.single().intervalMs)
    }
}

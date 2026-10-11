package net.ankio.bluetooth

import net.ankio.bluetooth.rules.RuleRepository
import net.ankio.bluetooth.rules.RuleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RuleRepositoryTest {
    private val bundled =
        javaClass.getResourceAsStream("/hook_rules.json")!!.use { RuleRepository.readLimited(it) }
    private val revisionTwo = bundled.replace("\"revision\": 1", "\"revision\": 2")

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
        val result = RuleRepository(bundled, store).install(revisionTwo)
        assertEquals(2, result.rules.revision)
        assertEquals("custom", result.source)
        assertNotNull(store.active)
        assertEquals(bundled, store.previous)
    }

    @Test
    fun rollbackRestoresPreviousRules() {
        val store = FakeStore()
        val repository = RuleRepository(bundled, store)
        repository.install(revisionTwo)
        assertEquals(1, repository.rollback().rules.revision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun installingOlderOrEqualRevisionIsRejected() {
        val store = FakeStore(active = revisionTwo)
        RuleRepository(bundled, store).install(bundled)
    }
}

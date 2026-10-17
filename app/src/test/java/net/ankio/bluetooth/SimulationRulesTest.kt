package net.ankio.bluetooth

import net.ankio.bluetooth.hook.model.HookRules
import net.ankio.bluetooth.hook.model.MemberKind
import net.ankio.bluetooth.hook.model.MemberStep
import net.ankio.bluetooth.hook.engine.HookPlanner
import net.ankio.bluetooth.hook.engine.ScanPayload
import net.ankio.bluetooth.hook.engine.ScanTargetResolver
import net.ankio.bluetooth.rules.RuleRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake AOSP scan controller exposing the full-signature internal callback. */
class FakeScanController {
    var captured: Array<Any?>? = null
    fun onScanResultInternal(
        eventType: Int, addressType: Int, address: String, primaryPhy: Int,
        secondaryPhy: Int, advertisingSid: Int, txPower: Int, rssi: Int,
        periodicAdvInt: Int, advData: ByteArray, originalAddress: String
    ) {
        captured = arrayOf(
            eventType, addressType, address, primaryPhy, secondaryPhy,
            advertisingSid, txPower, rssi, periodicAdvInt, advData, originalAddress
        )
    }

    @Suppress("unused")
    fun stop() = Unit
}

class FakeGattService(private val controller: FakeScanController) {
    @Suppress("unused")
    fun getScanController(): FakeScanController = controller
}

/** Mirrors ColorOS 17's GattService: a lifecycle entry but no path to any scan target. */
class FakeConstructorOnlyService {
    @Suppress("unused")
    fun stop() = Unit
}

class SimulationRulesTest {
    private val bundled: String =
        javaClass.getResourceAsStream("/hook_rules.json")!!.use { RuleRepository.readLimited(it) }

    @Test
    fun bundledRulesParseAndExposeTheAospProfile() {
        val rules = HookRules.parse(bundled)
        assertTrue(rules.revision > 0)
        val profile = rules.profiles.single()
        assertEquals("aosp-gatt", profile.id)
        assertTrue(profile.appliesTo("com.android.bluetooth", 34, "Xiaomi"))
        assertTrue(!profile.appliesTo("com.other.app", 34, "Xiaomi"))
    }

    @Test
    fun candidatesFallBackToBundledWhenNoCustomRulesExist() {
        val selected = RuleRepository.candidates(active = null, previous = null, bundled = bundled).first()
        assertEquals("bundled", selected.source)
    }

    @Test
    fun resolverMapsFullSignatureArgumentsThroughTheRuleProfile() {
        val profile = HookRules.parse(bundled).profiles.single()
        val controller = FakeScanController()
        val target = ScanTargetResolver.resolve(controller, profile)
        assertNotNull("Scan target should resolve through the empty path", target)

        val data = byteArrayOf(0x02, 0x01, 0x06)
        target!!.inject(ScanPayload.parse("AA:BB:CC:DD:EE:FF", "-50", "020106"))

        val captured = controller.captured!!
        assertEquals(0x1b, captured[0])            // eventType constant
        assertEquals("AA:BB:CC:DD:EE:FF", captured[2]) // mac
        assertEquals(-50, captured[7])             // rssi
        assertArrayEquals(data, captured[9] as ByteArray) // advertisement data
        assertEquals("AA:BB:CC:DD:EE:FF", captured[10])   // originalAddress mapped to mac
    }

    @Test
    fun resolverStillReachesTheControllerWhenTheEntryIsAService() {
        val profile = HookRules.parse(bundled).profiles.single()
        val controller = FakeScanController()
        val target = ScanTargetResolver.resolve(FakeGattService(controller), profile)
        assertNotNull("Scan target should resolve via getScanController", target)
        target!!.inject(ScanPayload.parse("AA:BB:CC:DD:EE:FF", "-50", "020106"))
        assertEquals("AA:BB:CC:DD:EE:FF", controller.captured!![2])
    }

    /**
     * Regression for ColorOS 17: the scan controller must be planned before any service class,
     * otherwise the planner hooks a constructor whose target paths all resolve to nothing.
     */
    @Test
    fun plannerPrefersTheScanControllerOverAnEarlierConstructorOnlyClass() {
        val profile = HookRules.parse(bundled).profiles.single()
        assertTrue(
            "ScanController must precede GattService in the rule profile",
            profile.classes.indexOf("com.android.bluetooth.le_scan.ScanController") <
                profile.classes.indexOf("com.android.bluetooth.gatt.GattService")
        )
        assertTrue("The empty path must be tried first", profile.targetPaths.first().isEmpty())
        assertTrue("No handler path may target the scan controller", profile.handlerPaths.isEmpty())
    }

    /**
     * Class order is only a hint: a class that cannot reach any scan target must be skipped even
     * when it is listed first, so a ROM that moved the callback still gets a working plan.
     */
    @Test
    fun plannerSkipsAClassWhoseTargetPathsAllDeadEnd() {
        val profile = HookRules.parse(bundled).profiles.single().copy(
            classes = listOf(
                "net.ankio.bluetooth.FakeConstructorOnlyService",
                "net.ankio.bluetooth.FakeScanController"
            ),
            // The empty path is the only one that resolves, and only for the controller itself.
            targetPaths = listOf(emptyList())
        )
        val plan = HookPlanner.plan(profile, javaClass.classLoader!!)
        assertNotNull("The dead-end class must be skipped", plan)
        assertEquals(FakeScanController::class.java, plan!!.type)
    }
}

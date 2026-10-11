package net.ankio.bluetooth

import net.ankio.bluetooth.hook.model.HookRules
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
}

class FakeGattService(private val controller: FakeScanController) {
    @Suppress("unused")
    fun getScanController(): FakeScanController = controller
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
        val target = ScanTargetResolver.resolve(FakeGattService(controller), profile)
        assertNotNull("Scan target should resolve via getScanController", target)

        val data = byteArrayOf(0x02, 0x01, 0x06)
        target!!.inject(ScanPayload.parse("AA:BB:CC:DD:EE:FF", "-50", "020106"))

        val captured = controller.captured!!
        assertEquals(0x1b, captured[0])            // eventType constant
        assertEquals("AA:BB:CC:DD:EE:FF", captured[2]) // mac
        assertEquals(-50, captured[7])             // rssi
        assertArrayEquals(data, captured[9] as ByteArray) // advertisement data
        assertEquals("AA:BB:CC:DD:EE:FF", captured[10])   // originalAddress mapped to mac
    }
}

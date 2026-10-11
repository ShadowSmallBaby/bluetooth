package net.ankio.bluetooth

import net.ankio.bluetooth.hook.ModuleActivationReporter
import net.ankio.bluetooth.utils.ModuleStatus
import net.ankio.bluetooth.utils.ModuleStatus.Activation
import net.ankio.bluetooth.utils.ModuleStatus.Framework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleStatusTest {
    @Test
    fun legacyFrameworkRequiresApi93() {
        assertFalse(Activation(Framework.LEGACY_XPOSED, 92).isSupported)
        assertTrue(Activation(Framework.LEGACY_XPOSED, 93).isSupported)
    }

    @Test
    fun modernFrameworkUsesItsOwnApiRequirement() {
        assertFalse(Activation(Framework.LIBXPOSED, 93).isSupported)
        assertFalse(Activation(Framework.LIBXPOSED, 101).isSupported)
        assertTrue(Activation(Framework.LIBXPOSED, 102).isSupported)
    }

    @Test
    fun entryReportsActivationThroughTheAppClassLoader() {
        ModuleActivationReporter.report(javaClass.classLoader!!, 103, true)

        assertTrue(ModuleStatus.isActive)
        assertTrue(ModuleStatus.isSupported)
        assertEquals(Activation(Framework.LIBXPOSED, 103), ModuleStatus.activation)
        assertFalse(ModuleStatus.usesLegacyPreferences)
    }

    @Test
    fun unsupportedLegacyFrameworkDoesNotEnableWorldReadablePreferences() {
        ModuleActivationReporter.report(javaClass.classLoader!!, 82, false)

        assertTrue(ModuleStatus.isActive)
        assertFalse(ModuleStatus.isSupported)
        assertFalse(ModuleStatus.usesLegacyPreferences)
    }
}

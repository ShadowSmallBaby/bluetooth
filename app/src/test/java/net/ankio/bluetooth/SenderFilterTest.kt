package net.ankio.bluetooth

import net.ankio.bluetooth.service.SenderFilter
import org.junit.Assert.*
import org.junit.Test

class SenderFilterTest {
    @Test fun emptyCompanyDoesNotOverrideMacFilter() {
        val filter = SenderFilter(" AA:BB:CC:DD:EE:FF ", " ")
        assertTrue(filter.matches("aa:bb:cc:dd:ee:ff", "Vendor"))
        assertFalse(filter.matches("11:22:33:44:55:66", "Vendor"))
    }

    @Test fun companyAndMacAreIndependentAlternatives() {
        val filter = SenderFilter("AA:BB:CC:DD:EE:FF", " Vendor ")
        assertTrue(filter.matches("11:22:33:44:55:66", "vendor Inc."))
        assertTrue(filter.matches("AA:BB:CC:DD:EE:FF", "Other"))
        assertFalse(filter.matches("11:22:33:44:55:66", "Other"))
    }

    @Test fun missingFiltersCannotMatchAnything() {
        val filter = SenderFilter(" ", "")
        assertFalse(filter.isConfigured)
        assertFalse(filter.matches("AA:BB:CC:DD:EE:FF", "Vendor"))
    }
}

package net.ankio.bluetooth.service

class SenderFilter(mac: String, company: String) {
    private val mac = mac.trim()
    private val company = company.trim()
    val isConfigured: Boolean get() = mac.isNotEmpty() || company.isNotEmpty()

    fun matches(address: String, manufacturer: String): Boolean =
        (mac.isNotEmpty() && mac.equals(address, ignoreCase = true)) ||
            (company.isNotEmpty() && manufacturer.contains(company, ignoreCase = true))
}

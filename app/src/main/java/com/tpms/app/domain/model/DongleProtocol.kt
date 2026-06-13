package com.tpms.app.domain.model

enum class DongleProtocolMode(val label: String) {
    AUTO("Auto"),
    HID_GENERIC("USB HID"),
    SERIAL_AA55("Serial 0x55AA"),
    DEELIFE("Deelife / MU7J");

    fun toProtocol(): DongleProtocol = when (this) {
        AUTO -> DongleProtocol.HID_GENERIC
        HID_GENERIC -> DongleProtocol.HID_GENERIC
        SERIAL_AA55 -> DongleProtocol.SERIAL_AA55
        DEELIFE -> DongleProtocol.DEELIFE
    }
}

enum class DongleProtocol {
    HID_GENERIC,
    SERIAL_AA55,
    DEELIFE;

    val displayName: String
        get() = when (this) {
            HID_GENERIC -> "USB HID"
            SERIAL_AA55 -> "Serial 0x55AA"
            DEELIFE -> "Deelife"
        }
}

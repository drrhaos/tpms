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

enum class DongleProtocol(
    /**
     * The receiver repeats its slot cache constantly (AA55/Deelife), so silence means a dead link and
     * a reading's timestamp is the frame arrival time. HID dongles may stay quiet until a sensor
     * transmits, and their timestamps are the sensor's own.
     */
    val streamsContinuously: Boolean
) {
    HID_GENERIC(streamsContinuously = false),
    SERIAL_AA55(streamsContinuously = true),
    DEELIFE(streamsContinuously = true);

    val displayName: String
        get() = when (this) {
            HID_GENERIC -> "USB HID"
            SERIAL_AA55 -> "Serial 0x55AA"
            DEELIFE -> "Deelife"
        }

    companion object {
        /** Parses a persisted [name]; null when missing or unknown (e.g. written by another version). */
        fun fromNameOrNull(name: String?): DongleProtocol? = entries.firstOrNull { it.name == name }
    }
}

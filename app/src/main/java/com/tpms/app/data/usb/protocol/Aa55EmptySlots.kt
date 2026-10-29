package com.tpms.app.data.usb.protocol

/**
 * Empty receiver slots from the AA55 answers (`09`) to the ID query `0x07` (wayfinder 11). The frame
 * already carries the wheel label: `09` numbers its slots 1..5 = FL/FR/RL/RR/SP (see
 * [Aa55SerialProtocol] `ID_SLOTS`, docs/research/aa55-status-flags.md), unlike the tire codes of `08`.
 */
object Aa55EmptySlots {
    /** [current] after [frames], in order: an empty answer marks its slot empty, a sensor id clears it. */
    fun apply(current: Set<String>, frames: List<Aa55AuxFrame.SensorId>): Set<String> =
        frames.fold(current) { slots, frame ->
            if (frame.sensorId == null) slots + frame.slot else slots - frame.slot
        }
}

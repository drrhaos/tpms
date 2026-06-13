package com.tpms.app.data.usb

internal fun ByteArray.toHexPreview(maxBytes: Int = 32): String {
    val preview = take(maxBytes).joinToString(" ") { "%02X".format(it) }
    return if (size > maxBytes) "$preview …(+${size - maxBytes}b)" else preview
}

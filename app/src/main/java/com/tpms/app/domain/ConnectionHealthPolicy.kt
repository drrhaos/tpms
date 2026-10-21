package com.tpms.app.domain

object ConnectionHealthPolicy {

  const val DEFAULT_STALE_FRAME_MS = 90_000L
  const val DEFAULT_SILENCE_MS = 15_000L
  const val DEFAULT_PROTOCOL_UNHEALTHY_MS = 120_000L

  /**
   * Bytes keep arriving but none of them made a valid frame for [staleThresholdMs]: a garbage stream.
   * Full silence is not stale here — the silence timeout handles it, and for HID it is normal.
   */
  fun shouldReconnectStaleFrame(
      isUsbConnected: Boolean,
      dongleOpenedAtMs: Long,
      lastValidFrameAtMs: Long,
      staleThresholdMs: Long,
      lastBytesAtMs: Long,
      now: Long = System.currentTimeMillis()
  ): Boolean {
    if (!isUsbConnected || dongleOpenedAtMs <= 0L) return false
    val referenceMs = if (lastValidFrameAtMs >= dongleOpenedAtMs) {
      lastValidFrameAtMs
    } else {
      dongleOpenedAtMs
    }
    return now - referenceMs > staleThresholdMs && lastBytesAtMs > referenceMs
  }

  fun isProtocolUnhealthy(
      dongleOpenedAtMs: Long,
      lastValidFrameAtMs: Long,
      unhealthyThresholdMs: Long = DEFAULT_PROTOCOL_UNHEALTHY_MS,
      now: Long = System.currentTimeMillis()
  ): Boolean {
    if (dongleOpenedAtMs <= 0L) return false
    if (now - dongleOpenedAtMs < unhealthyThresholdMs) return false
    return lastValidFrameAtMs <= 0L || now - lastValidFrameAtMs > unhealthyThresholdMs
  }
}

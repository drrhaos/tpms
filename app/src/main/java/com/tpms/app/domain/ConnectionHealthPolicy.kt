package com.tpms.app.domain

object ConnectionHealthPolicy {

  const val DEFAULT_STALE_FRAME_MS = 90_000L
  const val DEFAULT_PROTOCOL_UNHEALTHY_MS = 120_000L
  const val POLL_STUCK_MS = 15_000L

  fun shouldReconnectStaleFrame(
      isUsbConnected: Boolean,
      dongleOpenedAtMs: Long,
      lastValidFrameAtMs: Long,
      staleThresholdMs: Long,
      now: Long = System.currentTimeMillis()
  ): Boolean {
    if (!isUsbConnected || dongleOpenedAtMs <= 0L) return false
    val referenceMs = if (lastValidFrameAtMs >= dongleOpenedAtMs) {
      lastValidFrameAtMs
    } else {
      dongleOpenedAtMs
    }
    return now - referenceMs > staleThresholdMs
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

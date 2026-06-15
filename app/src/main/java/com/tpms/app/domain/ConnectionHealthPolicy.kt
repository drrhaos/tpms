package com.tpms.app.domain

object ConnectionHealthPolicy {

  const val DEFAULT_STALE_FRAME_MS = 90_000L
  const val DEFAULT_PROTOCOL_UNHEALTHY_MS = 120_000L
  const val POLL_STUCK_MS = 15_000L

  fun shouldReconnectStaleFrame(
      isUsbConnected: Boolean,
      lastValidFrameAtMs: Long,
      staleThresholdMs: Long,
      now: Long = System.currentTimeMillis()
  ): Boolean {
    if (!isUsbConnected || lastValidFrameAtMs <= 0L) return false
    return now - lastValidFrameAtMs > staleThresholdMs
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

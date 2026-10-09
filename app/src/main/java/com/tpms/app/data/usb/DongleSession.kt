package com.tpms.app.data.usb

import android.hardware.usb.UsbRequest
import com.tpms.app.domain.model.DongleProtocol
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

/**
 * One open dongle. The only reader of its connection: a dedicated thread waits on a queued
 * [UsbRequest] (interruptible, unlike a blocking bulkTransfer) and hands chunks to a channel.
 * Not reused: a reconnect creates a new session, so parser state never outlives a connection.
 */
class DongleSession(
    private val link: UsbLink,
    val decoder: FrameDecoder,
    private val debugLog: UsbDebugLog,
    private val clock: () -> Long = System::currentTimeMillis
) {
    val protocol: DongleProtocol get() = decoder.protocol
    val deviceName: String get() = link.deviceName
    val vidPid: String get() = link.vidPid
    val openedAtMs: Long = clock()

    /** AA55 receivers repeat every slot constantly; HID dongles may stay quiet until a value changes. */
    val streamsContinuously: Boolean get() = protocol != DongleProtocol.HID_GENERIC

    @Volatile
    var lastBytesAtMs: Long = openedAtMs
        private set

    @Volatile
    var closeReason: String? = null
        private set

    val isClosed: Boolean get() = closeReason != null

    private val chunks = Channel<ByteArray>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile
    private var request: UsbRequest? = null
    private val closeLock = Any()
    private var closeResult: Boolean? = null
    private val reader = Thread({ readLoop() }, "tpms-usb-reader").apply { isDaemon = true }

    fun start() {
        decoder.handshakeFrames().forEach { frame ->
            val ok = link.write(frame)
            debugLog.raw(TAG, "TX ${if (ok) "OK" else "FAIL"} ${frame.size}b: ${frame.toHexPreview()}")
        }
        reader.start()
    }

    /** Next chunk, or null on timeout or once the reader has stopped (check [isClosed]). */
    suspend fun receive(timeoutMs: Long): ByteArray? =
        withTimeoutOrNull(timeoutMs) { chunks.receiveCatching().getOrNull() }

    fun silentForMs(now: Long = clock()): Long = now - lastBytesAtMs

    /**
     * Stops the reader step by step, each step with its own deadline: cancel the queued request,
     * then release the interfaces (aborts the transfer in the kernel), and close the connection
     * only once the reader thread has exited. Returns false if the reader is still stuck.
     * Idempotent: later calls wait for the first one and return its result.
     */
    fun close(reason: String): Boolean = synchronized(closeLock) {
        closeResult ?: stop(reason).also { closeResult = it }
    }

    private fun stop(reason: String): Boolean {
        markClosed(reason)
        runCatching { request?.cancel() }
        if (awaitReader()) {
            link.releaseInterfaces()
            link.close()
            return true
        }
        debugLog.warn(TAG, "Reader did not stop after cancel — releasing interfaces")
        link.releaseInterfaces()
        if (awaitReader()) {
            link.close()
            return true
        }
        // Closing under a blocked read risks use-after-free in usbfs; leave the fd to the process.
        debugLog.error(TAG, "Reader thread stuck in kernel — connection left open")
        return false
    }

    private fun awaitReader(): Boolean {
        if (!reader.isAlive) return true
        runCatching { reader.join(STOP_STEP_TIMEOUT_MS) }
        return !reader.isAlive
    }

    private fun markClosed(reason: String) {
        synchronized(this) {
            if (closeReason == null) closeReason = reason
        }
    }

    private fun readLoop() {
        val connection = link.connection
        val endpoint = link.inEndpoint
        val usbRequest = UsbRequest()
        try {
            if (!usbRequest.initialize(connection, endpoint)) {
                markClosed("request init failed")
                return
            }
            request = usbRequest
            val buffer = ByteBuffer.allocateDirect(endpoint.maxPacketSize.coerceAtLeast(64))
            while (!isClosed) {
                buffer.clear()
                if (!usbRequest.queue(buffer)) {
                    markClosed("request queue failed")
                    break
                }
                val completed = connection.requestWait()
                if (isClosed) break
                if (completed == null) {
                    markClosed("read failed")
                    break
                }
                val size = buffer.position()
                if (size > 0) {
                    val data = ByteArray(size)
                    buffer.flip()
                    buffer.get(data)
                    lastBytesAtMs = clock()
                    chunks.trySend(data)
                }
            }
        } catch (t: Throwable) {
            markClosed("read error: ${t.message}")
        } finally {
            request = null
            runCatching { usbRequest.close() }
            chunks.close()
        }
    }

    companion object {
        private const val TAG = "TPMS_RAW"
        private const val STOP_STEP_TIMEOUT_MS = 1_000L
    }
}

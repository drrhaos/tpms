package com.tpms.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.tpms.app.R
import com.tpms.app.domain.AlertPresentation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the alert signals itself (wayfinder alerts 04): the notification channel's sound may stay
 * silent without the app knowing (stream at 0, rate limits, a silenced channel), so the alert
 * channel is silent and this player is the only sound.
 *
 * - Own sounds from `res/raw` (`tools/alert_sounds`), `USAGE_ALARM`: alarm volume, never below 1 in
 *   AOSP, passes "alarms only" DND.
 * - Audio focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for each signal, released right after: music is
 *   ducked, navigation speech mixes in.
 * - If the sound pool fails, a [ToneGenerator] on the alarm stream stands in.
 *
 * Main thread only.
 */
@Singleton
class AlertSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var pool: SoundPool? = null
    private val loaded = HashSet<Int>()
    private var faultId = 0
    private var attentionId = 0
    private var focus: AudioFocusRequest? = null
    private val releaseFocus = Runnable { abandonFocus() }

    /** Plays [sound] once; false if neither the pool nor the tone generator could play it. */
    fun play(sound: AlertPresentation.Sound): Boolean {
        val soundPool = ensurePool()
        val id = if (sound == AlertPresentation.Sound.FAULT) faultId else attentionId
        requestFocus(durationMs(sound))
        val played = id != 0 && id in loaded && soundPool.play(id, 1f, 1f, 1, 0, 1f) != 0
        return played || playTone(sound)
    }

    /** Loads the sounds ahead of the first alert, so it is not delayed by decoding. */
    fun prepare() {
        ensurePool()
    }

    fun release() {
        handler.removeCallbacks(releaseFocus)
        abandonFocus()
        pool?.release()
        pool = null
        loaded.clear()
        faultId = 0
        attentionId = 0
    }

    private fun ensurePool(): SoundPool = pool ?: SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(attributes)
        .build()
        .also { created ->
            created.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
            faultId = created.load(context, R.raw.alert_fault, 1)
            attentionId = created.load(context, R.raw.alert_attention, 1)
            pool = created
        }

    private fun playTone(sound: AlertPresentation.Sound): Boolean = runCatching {
        val generator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        val tone = if (sound == AlertPresentation.Sound.FAULT) ToneGenerator.TONE_CDMA_HIGH_PBX_SSL else ToneGenerator.TONE_PROP_BEEP2
        generator.startTone(tone, durationMs(sound).toInt())
        handler.postDelayed({ generator.release() }, durationMs(sound) + 200)
    }.isSuccess

    private fun requestFocus(holdMs: Long) {
        handler.removeCallbacks(releaseFocus)
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { focus = it }
        runCatching { audioManager.requestAudioFocus(request) }
        handler.postDelayed(releaseFocus, holdMs + FOCUS_TAIL_MS)
    }

    private fun abandonFocus() {
        focus?.let { request -> runCatching { audioManager.abandonAudioFocusRequest(request) } }
    }

    private fun durationMs(sound: AlertPresentation.Sound): Long =
        if (sound == AlertPresentation.Sound.FAULT) FAULT_MS else ATTENTION_MS

    companion object {
        private const val FAULT_MS = 750L
        private const val ATTENTION_MS = 1_150L
        private const val FOCUS_TAIL_MS = 300L
    }
}

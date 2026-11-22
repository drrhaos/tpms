package com.tpms.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tpms.app.R
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertPresentation
import com.tpms.app.domain.AlertSoundRoute
import com.tpms.app.startup.HeadUnitSupport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the alert signals itself (wayfinder alerts 04): the notification channel's sound may stay
 * silent without the app knowing (stream at 0, rate limits, a silenced channel), so the alert
 * channel is silent and this player is the only sound.
 *
 * - Own sounds from `res/raw` (`tools/alert_sounds`) on the stream the user chose
 *   ([AlertSoundRoute]: alarm by default, media on Teyes, whose MCU may not play the alarm stream).
 * - Audio focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for each signal, released right after: music is
 *   ducked, navigation speech mixes in.
 * - A signal asked for before its sound has loaded plays once it has; if the sound pool fails, a
 *   [ToneGenerator] on the same stream stands in.
 *
 * Main thread only.
 */
@Singleton
class AlertSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val headUnitSupport: HeadUnitSupport
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var pool: SoundPool? = null
    private var poolRoute: AlertSoundRoute? = null
    private val loaded = HashSet<Int>()
    private var faultId = 0
    private var attentionId = 0
    private var focus: AudioFocusRequest? = null
    private var focusRoute: AlertSoundRoute? = null
    private val releaseFocus = Runnable { abandonFocus() }

    /** A signal waiting for its sound to load; the tone stands in if loading takes too long. */
    private var pending: AlertPresentation.Sound? = null
    private val pendingTimeout = Runnable { pending?.let { pending = null; playTone(it, poolRoute ?: route()) } }

    /** The stream the signals go to now. */
    fun route(): AlertSoundRoute =
        AlertSoundRoute.effective(settingsStore.alertSoundRoute.value, headUnitSupport.isTeyesHeadUnit())

    /**
     * Plays [sound] once on [route] (the chosen one unless a test asks for another); false if neither
     * the pool nor the tone generator could play it.
     */
    fun play(sound: AlertPresentation.Sound, route: AlertSoundRoute = route()): Boolean {
        val soundPool = ensurePool(route)
        val id = sampleId(sound)
        requestFocus(route, durationMs(sound))
        Log.i(TAG, "play $sound route=$route volume=${volumeOf(route)}/${maxVolumeOf(route)}")
        if (id != 0 && id !in loaded) {
            pending = sound
            handler.removeCallbacks(pendingTimeout)
            handler.postDelayed(pendingTimeout, LOAD_WAIT_MS)
            return true
        }
        val played = id != 0 && soundPool.play(id, 1f, 1f, 1, 0, 1f) != 0
        return played || playTone(sound, route)
    }

    /** Loads the sounds ahead of the first alert, so it is not delayed by decoding. */
    fun prepare() {
        ensurePool(route())
    }

    fun release() {
        handler.removeCallbacks(releaseFocus)
        handler.removeCallbacks(pendingTimeout)
        pending = null
        abandonFocus()
        releasePool()
    }

    private fun releasePool() {
        pool?.release()
        pool = null
        poolRoute = null
        loaded.clear()
        faultId = 0
        attentionId = 0
    }

    private fun sampleId(sound: AlertPresentation.Sound): Int =
        if (sound == AlertPresentation.Sound.FAULT) faultId else attentionId

    /** The pool for [route]; a changed route rebuilds it, since a pool's attributes are fixed. */
    private fun ensurePool(route: AlertSoundRoute): SoundPool {
        pool?.takeIf { poolRoute == route }?.let { return it }
        releasePool()
        return SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes(route))
            .build()
            .also { created ->
                created.setOnLoadCompleteListener { soundPool, sampleId, status ->
                    if (status != 0 || soundPool !== pool) return@setOnLoadCompleteListener
                    loaded += sampleId
                    val waiting = pending
                    if (waiting != null && sampleId(waiting) == sampleId) {
                        pending = null
                        handler.removeCallbacks(pendingTimeout)
                        if (soundPool.play(sampleId, 1f, 1f, 1, 0, 1f) == 0) playTone(waiting, route)
                    }
                }
                pool = created
                poolRoute = route
                faultId = created.load(context, R.raw.alert_fault, 1)
                attentionId = created.load(context, R.raw.alert_attention, 1)
            }
    }

    private fun playTone(sound: AlertPresentation.Sound, route: AlertSoundRoute): Boolean = runCatching {
        val generator = ToneGenerator(stream(route), ToneGenerator.MAX_VOLUME)
        val tone = if (sound == AlertPresentation.Sound.FAULT) ToneGenerator.TONE_CDMA_HIGH_PBX_SSL else ToneGenerator.TONE_PROP_BEEP2
        generator.startTone(tone, durationMs(sound).toInt())
        handler.postDelayed({ generator.release() }, durationMs(sound) + 200)
    }.onFailure { Log.w(TAG, "tone failed on $route", it) }.isSuccess

    private fun requestFocus(route: AlertSoundRoute, holdMs: Long) {
        handler.removeCallbacks(releaseFocus)
        if (focusRoute != route) {
            abandonFocus()
            focus = null
        }
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes(route))
            .setOnAudioFocusChangeListener { }
            .build()
            .also { focus = it; focusRoute = route }
        runCatching { audioManager.requestAudioFocus(request) }
        handler.postDelayed(releaseFocus, holdMs + FOCUS_TAIL_MS)
    }

    private fun abandonFocus() {
        focus?.let { request -> runCatching { audioManager.abandonAudioFocusRequest(request) } }
    }

    private fun volumeOf(route: AlertSoundRoute): Int = runCatching { audioManager.getStreamVolume(stream(route)) }.getOrDefault(-1)

    private fun maxVolumeOf(route: AlertSoundRoute): Int = runCatching { audioManager.getStreamMaxVolume(stream(route)) }.getOrDefault(-1)

    private fun durationMs(sound: AlertPresentation.Sound): Long =
        if (sound == AlertPresentation.Sound.FAULT) FAULT_MS else ATTENTION_MS

    companion object {
        private const val TAG = "AlertSound"
        private const val FAULT_MS = 750L
        private const val ATTENTION_MS = 1_150L
        private const val FOCUS_TAIL_MS = 300L
        private const val LOAD_WAIT_MS = 1_500L

        fun attributes(route: AlertSoundRoute): AudioAttributes = AudioAttributes.Builder()
            .setUsage(
                when (route) {
                    AlertSoundRoute.ALARM -> AudioAttributes.USAGE_ALARM
                    AlertSoundRoute.NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                    AlertSoundRoute.MEDIA -> AudioAttributes.USAGE_MEDIA
                }
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** The volume stream of [route] (navigation guidance follows the media volume in AOSP). */
        fun stream(route: AlertSoundRoute): Int =
            if (route == AlertSoundRoute.ALARM) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
    }
}

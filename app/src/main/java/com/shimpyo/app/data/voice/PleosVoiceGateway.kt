package com.shimpyo.app.data.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * Pleos TextToSpeech / SpeechToText v3.3.1 adapter in ON_DEVICE mode (no app registration, works
 * offline). Voice data stays in the car.
 *
 * TODO(spec): HYBRID/SERVER modes need the Pleos Playground project Client ID/Secret (registerApp).
 * TODO(spec): setAudioZoneId could route prompts to the driver zone only; confirm zone IDs per vehicle.
 * TODO(privacy): Add a privacy notice for voice answers before release, even though they stay on-device.
 */
@Singleton
class PleosVoiceGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceGateway {

    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null

    private val _ttsReady = MutableStateFlow(false)
    private val _sttReady = MutableStateFlow(false)
    private val _listening = MutableStateFlow(false)
    private val _lastSpoken = MutableStateFlow<String?>(null)

    override val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()
    override val sttReady: StateFlow<Boolean> = _sttReady.asStateFlow()
    override val listening: StateFlow<Boolean> = _listening.asStateFlow()
    override val lastSpoken: StateFlow<String?> = _lastSpoken.asStateFlow()

    private val lock = Any()
    private var speakCont: CancellableContinuation<Boolean>? = null
    private var listenCont: CancellableContinuation<Heard?>? = null
    private var listenStartedAt = 0L
    private var firstSpeechAt: Long? = null
    private var lastPartial: String? = null

    /** Demo answers tapped before Shimpyo starts listening are kept briefly for the next question. */
    private var queuedAnswer: Pair<String, Long>? = null

    private val ttsListener = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = resumeSpeak(true)
        override fun onError(errorType: TtsErrorType) {
            Log.w(TAG, "TTS error $errorType")
            resumeSpeak(false)
        }
        override fun onStop() = resumeSpeak(false)
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            if (stt.isNotBlank()) {
                synchronized(lock) { if (firstSpeechAt == null) firstSpeechAt = SystemClock.elapsedRealtime() }
                lastPartial = stt
            }
            if (completed) resumeListen(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = resumeListen(lastPartial)
        override fun onError(errorType: SttErrorType) {
            Log.w(TAG, "STT error $errorType")
            resumeListen(null)
        }
    }

    override fun start(locale: Locale) {
        if (tts == null) runCatching {
            val t = TextToSpeech(context, TtsMode.ON_DEVICE)
            tts = t
            t.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    t.addEventListener(ttsListener)
                    t.setLocale(locale)
                    _ttsReady.value = true
                }
                override fun onFailure(errorType: TtsErrorType) {
                    Log.w(TAG, "TTS init failed $errorType")
                    _ttsReady.value = false
                }
            })
        }.onFailure { Log.w(TAG, "TTS unavailable", it) }

        if (stt == null) runCatching {
            val s = SpeechToText(context, SttMode.ON_DEVICE)
            stt = s
            s.initialize(object : SttInitListener {
                override fun onSuccess() {
                    s.addListener(sttListener)
                    s.setLocale(locale)
                    _sttReady.value = true
                }
                override fun onFailure(errorType: SttErrorType) {
                    Log.w(TAG, "STT init failed $errorType")
                    _sttReady.value = false
                }
            })
        }.onFailure { Log.w(TAG, "STT unavailable", it) }
    }

    override fun stop() {
        cancel()
        runCatching { tts?.removeEventListener(ttsListener); tts?.release() }
        runCatching { stt?.removeListener(sttListener); stt?.release() }
        tts = null
        stt = null
        _ttsReady.value = false
        _sttReady.value = false
    }

    override suspend fun speak(text: String): Boolean {
        _lastSpoken.value = text
        val t = tts
        if (t == null || !_ttsReady.value) {
            // Keep the conversation timing believable even without a speech engine (caption only).
            delay(estimateMs(text))
            return false
        }
        return withTimeoutOrNull(estimateMs(text) + 8_000) {
            suspendCancellableCoroutine { cont ->
                synchronized(lock) { speakCont = cont }
                cont.invokeOnCancellation { runCatching { t.stop() } }
                runCatching { t.speak(text, true) }.onFailure { resumeSpeak(false) }
            }
        } ?: false
    }

    override suspend fun listen(timeoutMs: Long): Heard? {
        val queued = synchronized(lock) { queuedAnswer.also { queuedAnswer = null } }
        if (queued != null && SystemClock.elapsedRealtime() - queued.second < QUEUED_ANSWER_TTL_MS) {
            return Heard(queued.first, responseLatencyMs = QUEUED_ANSWER_LATENCY_MS)
        }
        _listening.value = true
        try {
            val heard = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    synchronized(lock) {
                        listenCont = cont
                        lastPartial = null
                        firstSpeechAt = null
                        listenStartedAt = SystemClock.elapsedRealtime()
                    }
                    cont.invokeOnCancellation { runCatching { stt?.stop() } }
                    val s = stt
                    if (s != null && _sttReady.value) {
                        runCatching { s.request() }.onFailure { Log.w(TAG, "STT request failed", it) }
                    }
                    // Without STT the call still completes through injectAnswer() or the timeout.
                }
            }
            if (heard == null) runCatching { stt?.stop() }
            return heard
        } finally {
            synchronized(lock) { listenCont = null }
            _listening.value = false
        }
    }

    override fun cancel() {
        runCatching { tts?.stop() }
        runCatching { stt?.stop() }
        resumeSpeak(false)
        resumeListen(null)
    }

    override fun injectAnswer(text: String) {
        val listeningNow = synchronized(lock) {
            if (listenCont == null) {
                queuedAnswer = text to SystemClock.elapsedRealtime()
                false
            } else {
                if (firstSpeechAt == null) firstSpeechAt = SystemClock.elapsedRealtime()
                true
            }
        }
        if (listeningNow) resumeListen(text)
    }

    private fun resumeSpeak(ok: Boolean) {
        val cont = synchronized(lock) { speakCont.also { speakCont = null } } ?: return
        if (cont.isActive) cont.resume(ok)
    }

    private fun resumeListen(text: String?) {
        val (cont, latency) = synchronized(lock) {
            val c = listenCont
            listenCont = null
            c to firstSpeechAt?.let { it - listenStartedAt }
        }
        if (cont == null || !cont.isActive) return
        cont.resume(text?.takeIf { it.isNotBlank() }?.let { Heard(it, latency) })
    }

    private fun estimateMs(text: String): Long = 1_200L + text.length * 70L

    private companion object {
        const val TAG = "PleosVoice"
        const val QUEUED_ANSWER_TTL_MS = 20_000L
        const val QUEUED_ANSWER_LATENCY_MS = 800L
    }
}

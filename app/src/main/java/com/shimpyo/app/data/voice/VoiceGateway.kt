package com.shimpyo.app.data.voice

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** A recognized answer and how long the driver took to start speaking (null if unknown). */
data class Heard(val text: String, val responseLatencyMs: Long?)

/** Voice in and out (backed by the Pleos TextToSpeech and SpeechToText SDKs). */
interface VoiceGateway {
    val ttsReady: StateFlow<Boolean>
    val sttReady: StateFlow<Boolean>
    val listening: StateFlow<Boolean>
    /** The last line Shimpyo said, mirrored on screen as a caption. */
    val lastSpoken: StateFlow<String?>

    fun start(locale: Locale)
    fun stop()

    /** Suspends until the line has been spoken (or failed). */
    suspend fun speak(text: String): Boolean

    /** Suspends until a final recognition result, silence, or [timeoutMs]. */
    suspend fun listen(timeoutMs: Long = 8_000): Heard?

    /** Stops speaking and listening. */
    fun cancel()

    /** Completes a pending [listen] with [text] (on-screen answer buttons and demo controls). */
    fun injectAnswer(text: String)
}

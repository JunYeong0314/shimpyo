package com.shimpyo.app.domain.logic

import com.shimpyo.app.domain.model.RestMode

/**
 * Interprets short spoken answers in Korean and English.
 * STT can return digits ("7"), Sino-Korean ("칠"), native Korean ("일곱") or English ("seven").
 */
object UtteranceParser {

    enum class YesNo { YES, NO }

    private val nativeKorean = listOf(
        "아홉" to 9, "여덟" to 8, "일곱" to 7, "여섯" to 6, "다섯" to 5, "넷" to 4, "셋" to 3, "둘" to 2, "하나" to 1,
    )
    private val english = listOf(
        "nine" to 9, "eight" to 8, "seven" to 7, "six" to 6, "five" to 5, "four" to 4, "three" to 3, "two" to 2, "one" to 1,
    )
    private val sinoKorean = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)

    private val sleepyWords = listOf("졸려", "졸리", "피곤", "잠와", "잠 와", "sleepy", "tired", "exhausted")
    private val fineWords = listOf("괜찮", "안 졸", "멀쩡", "쌩쌩", "fine", "good", "okay", "not sleepy", "awake")

    /** Karolinska Sleepiness Scale answer (1 very alert … 9 fighting sleep), or null if unclear. */
    fun parseSleepiness(text: String?): Int? {
        val t = text?.lowercase()?.trim().orEmpty()
        if (t.isEmpty()) return null
        t.firstOrNull { it in '1'..'9' }?.let { return it.digitToInt() }
        nativeKorean.firstOrNull { t.contains(it.first) }?.let { return it.second }
        english.firstOrNull { Regex("\\b${it.first}\\b").containsMatchIn(t) }?.let { return it.second }
        // Single Sino-Korean syllable answers such as "칠" or "칠이요".
        t.firstOrNull { it in sinoKorean.keys }?.let { c ->
            if (t.length <= 4) return sinoKorean.getValue(c)
        }
        if (sleepyWords.any { t.contains(it) }) return 7
        if (fineWords.any { t.contains(it) }) return 3
        return null
    }

    fun parseYesNo(text: String?): YesNo? {
        val t = text?.lowercase()?.trim().orEmpty()
        if (t.isEmpty()) return null
        val no = listOf("아니", "싫어", "안 할", "다음에", "괜찮아", "no", "nope", "not now", "later")
        val yes = listOf("응", "네", "예", "그래", "좋아", "가자", "들를", "넣어", "yes", "yeah", "yep", "sure", "ok", "please")
        // "아니, 괜찮아" must win over the "좋아"-like fragments, so check NO first.
        if (no.any { t.contains(it) }) return YesNo.NO
        if (yes.any { t.contains(it) }) return YesNo.YES
        return null
    }

    fun parseRestMode(text: String?): RestMode? {
        val t = text?.lowercase()?.trim().orEmpty()
        return when {
            t.isEmpty() -> null
            t.contains("커피") || t.contains("coffee") -> RestMode.COFFEE_NAP
            t.contains("스트레칭") || t.contains("stretch") -> RestMode.STRETCH
            t.contains("카냅") || t.contains("낮잠") || t.contains("잠") || t.contains("nap") || t.contains("sleep") -> RestMode.CAR_NAP
            else -> null
        }
    }

    /** Extracts single digits in spoken order, e.g. "일, 팔, 삼" → [1, 8, 3]. */
    fun parseDigits(text: String?): List<Int> {
        val t = text?.lowercase().orEmpty()
        val out = mutableListOf<Int>()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            val native = nativeKorean.firstOrNull { t.startsWith(it.first, i) }
            val eng = english.firstOrNull { t.startsWith(it.first, i) }
            when {
                c in '1'..'9' -> { out += c.digitToInt(); i++ }
                native != null -> { out += native.second; i += native.first.length }
                eng != null -> { out += eng.second; i += eng.first.length }
                c in sinoKorean.keys -> { out += sinoKorean.getValue(c); i++ }
                else -> i++
            }
        }
        return out
    }
}

package com.shimpyo.app.core.time

import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of "virtual" time. Real driving uses scale 1; demo builds can speed time up
 * (for example ×60 so one real second counts as one driving minute).
 */
@Singleton
class AppClock @Inject constructor() {
    @Volatile
    var timeScale: Int = 1
        private set

    fun setScale(scale: Int) {
        timeScale = scale.coerceIn(1, 600)
    }

    fun localTime(): LocalTime = LocalTime.now()

    fun nowMillis(): Long = System.currentTimeMillis()
}

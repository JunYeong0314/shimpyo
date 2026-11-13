package com.shimpyo.app.core.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation switches for recording the contest video.
 * Only the debug-only DemoCommandReceiver (src/debug) changes them, so release builds keep the defaults.
 */
object DemoUi {
    private val _panelVisible = MutableStateFlow(true)
    val panelVisible: StateFlow<Boolean> = _panelVisible.asStateFlow()

    fun setPanelVisible(visible: Boolean) {
        _panelVisible.value = visible
    }
}

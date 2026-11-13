package com.shimpyo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.data.settings.SettingsRepository
import com.shimpyo.app.ui.main.MainScreen
import com.shimpyo.app.ui.onboarding.OnboardingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
}

@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** null while loading. */
    val onboardingDone: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun ShimpyoRoot(root: RootViewModel = hiltViewModel()) {
    val done by root.onboardingDone.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(ShimpyoTheme.colors.background)) {
        val onboardingDone = done ?: return@Box
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = if (onboardingDone) Routes.MAIN else Routes.ONBOARDING) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.MAIN) { MainScreen() }
        }
    }
}

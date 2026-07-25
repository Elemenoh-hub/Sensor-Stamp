package com.sensorstamp.openwifi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sensorstamp.openwifi.ui.screens.HomeScreen
import com.sensorstamp.openwifi.ui.screens.OnboardingScreen
import com.sensorstamp.openwifi.ui.theme.SensorStampTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Grants can change while we are backgrounded (the user may have gone to
        // system settings), so re-read them every time we come back to the front.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshRequirements()
            }
        }

        setContent {
            SensorStampTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRequirements()
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()

    val blockingRequirements = requirements.filterValues { !it }.keys.filter { it.required }
    val needsOnboarding = !settings.onboardingComplete || blockingRequirements.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = needsOnboarding,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "root",
        ) { onboarding ->
            if (onboarding) {
                OnboardingScreen(viewModel)
            } else {
                HomeScreen(viewModel)
            }
        }
    }
}

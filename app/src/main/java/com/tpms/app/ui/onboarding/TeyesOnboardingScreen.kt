package com.tpms.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R

@Composable
fun TeyesOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: TeyesOnboardingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (step) {
                    0 -> stringResource(R.string.onboarding_step_usb)
                    1 -> stringResource(R.string.onboarding_step_notifications)
                    2 -> stringResource(R.string.onboarding_step_teyes)
                    else -> stringResource(R.string.onboarding_step_widget)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (step) {
                0 -> OnboardingActions(
                    primary = stringResource(R.string.onboarding_grant_usb),
                    onPrimary = { viewModel.requestUsbPermission() },
                    secondary = stringResource(R.string.onboarding_next),
                    onSecondary = { viewModel.nextStep() }
                )
                1 -> OnboardingActions(
                    primary = stringResource(R.string.onboarding_open_notifications),
                    onPrimary = { viewModel.openNotifications() },
                    secondary = stringResource(R.string.onboarding_next),
                    onSecondary = { viewModel.nextStep() }
                )
                2 -> OnboardingActions(
                    primary = stringResource(R.string.onboarding_open_battery),
                    onPrimary = { viewModel.openBattery() },
                    secondary = stringResource(R.string.onboarding_next),
                    onSecondary = { viewModel.nextStep() }
                )
                else -> OnboardingActions(
                    primary = stringResource(R.string.widget_pin_to_panel),
                    onPrimary = { viewModel.pinWidget() },
                    secondary = stringResource(R.string.onboarding_finish),
                    onSecondary = { viewModel.complete(onComplete) }
                )
            }
            TextButton(
                onClick = { viewModel.complete(onComplete) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun OnboardingActions(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit
) {
    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
        Text(primary)
    }
    Button(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
        Text(secondary)
    }
}

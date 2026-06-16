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
import androidx.compose.material3.OutlinedButton
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
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsState()
    val showTeyesSteps = viewModel.showTeyesSteps

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
                text = stringResource(
                    if (showTeyesSteps) R.string.onboarding_title_teyes else R.string.onboarding_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (step) {
                    OnboardingSteps.USB -> stringResource(R.string.onboarding_step_usb)
                    OnboardingSteps.PERMISSIONS -> stringResource(R.string.onboarding_step_notifications)
                    OnboardingSteps.TEYES_PERMISSIONS -> stringResource(R.string.onboarding_step_teyes)
                    else -> stringResource(R.string.onboarding_step_widget)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (step) {
                OnboardingSteps.USB -> OnboardingPrimaryAction(
                    label = stringResource(R.string.onboarding_grant_usb),
                    onClick = { viewModel.requestUsbPermission() }
                )
                OnboardingSteps.PERMISSIONS -> OnboardingStepActions(
                    primaryLabel = stringResource(R.string.onboarding_open_notifications),
                    onPrimary = { viewModel.openNotifications() },
                    secondaryLabel = stringResource(
                        if (showTeyesSteps) R.string.onboarding_next else R.string.onboarding_finish
                    ),
                    onSecondary = {
                        if (showTeyesSteps) viewModel.nextStep() else viewModel.complete(onComplete)
                    }
                )
                OnboardingSteps.TEYES_PERMISSIONS -> OnboardingStepActions(
                    primaryLabel = stringResource(R.string.onboarding_open_teyes_settings),
                    onPrimary = { viewModel.openTeyesSettings() },
                    secondaryLabel = stringResource(R.string.onboarding_next),
                    onSecondary = { viewModel.nextStep() }
                )
                else -> OnboardingStepActions(
                    primaryLabel = stringResource(R.string.onboarding_open_frontapp),
                    onPrimary = { viewModel.openFrontApp() },
                    secondaryLabel = stringResource(R.string.onboarding_finish),
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
private fun OnboardingPrimaryAction(
    label: String,
    onClick: () -> Unit
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun OnboardingStepActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit
) {
    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
        Text(primaryLabel)
    }
    OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
        Text(secondaryLabel)
    }
}

package com.tpms.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
                OnboardingSteps.USB -> {
                    Text(
                        text = stringResource(R.string.onboarding_usb_default_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OnboardingPrimaryAction(
                        label = stringResource(R.string.onboarding_grant_usb),
                        onClick = { viewModel.requestUsbPermission() }
                    )
                }
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
                OnboardingSteps.TEYES_PERMISSIONS -> {
                    val checklist by viewModel.teyesChecklist.collectAsState()
                    AutoStartAfterSleepCard(
                        autoStartDone = checklist.autoStart,
                        autoRunAwakeDone = checklist.autoRunAwake,
                        onOpenSettings = { viewModel.openTeyesSettings() },
                        onAutoStartChange = { viewModel.setAutoStartDone(it) },
                        onAutoRunAwakeChange = { viewModel.setAutoRunAwakeDone(it) }
                    )
                    Text(
                        text = stringResource(R.string.onboarding_teyes_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OnboardingPrimaryAction(
                        label = stringResource(R.string.onboarding_next),
                        onClick = { viewModel.nextStep() }
                    )
                }
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

/**
 * First and highlighted Teyes item: without FYT auto start the process is killed in sleep.
 * Which of the two FYT settings prevents that differs between firmwares, so both are asked for.
 */
@Composable
private fun AutoStartAfterSleepCard(
    autoStartDone: Boolean,
    autoRunAwakeDone: Boolean,
    onOpenSettings: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAutoRunAwakeChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_autostart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.onboarding_autostart_explanation),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_open_teyes_settings))
            }
            ManualCheckRow(
                label = stringResource(R.string.settings_teyes_auto_start),
                checked = autoStartDone,
                onCheckedChange = onAutoStartChange
            )
            ManualCheckRow(
                label = stringResource(R.string.settings_teyes_auto_run_awake),
                checked = autoRunAwakeDone,
                onCheckedChange = onAutoRunAwakeChange
            )
        }
    }
}

@Composable
private fun ManualCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
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

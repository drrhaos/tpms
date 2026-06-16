package com.tpms.app.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val usbScan by viewModel.usbScan.collectAsState()
    val entries by viewModel.logEntries.collectAsState()
    val logRecordingEnabled by viewModel.logRecordingEnabled.collectAsState()
    val hasPersistedCrash = viewModel.hasPersistedCrash
    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_debug), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.debug_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!logRecordingEnabled) {
                Text(
                    text = stringResource(R.string.debug_log_recording_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.warning
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (hasPersistedCrash) {
                Text(
                    text = stringResource(R.string.debug_crash_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.warning
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshUsbScan() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.debug_scan_usb))
                }
                OutlinedButton(
                    onClick = { viewModel.probeRead() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.debug_probe_read))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    shareLog(
                        context,
                        viewModel.exportFullReport(),
                        context.getString(R.string.debug_share_full_subject)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.debug_export_full))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { copyToClipboard(context, viewModel.exportLog()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.debug_copy))
                }
                OutlinedButton(
                    onClick = {
                        shareLog(
                            context,
                            viewModel.exportFullReport(),
                            context.getString(R.string.debug_share_full_subject)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.debug_share_full))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.clearLog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_clear_log))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.debug_usb_scan),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = usbScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TpmsColors.surfaceElevated, MaterialTheme.shapes.small)
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.debug_event_log, entries.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TpmsColors.surfaceElevated, MaterialTheme.shapes.small)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.debug_no_events),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEach { entry ->
                        LogLine(entry, timeFmt)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: UsbDebugLog.Entry, timeFmt: SimpleDateFormat) {
    val color = when (entry.level) {
        UsbDebugLog.Level.ERROR -> StatusColors.alert
        UsbDebugLog.Level.WARN -> StatusColors.warning
        UsbDebugLog.Level.USB -> MaterialTheme.colorScheme.primary
        UsbDebugLog.Level.RAW -> TpmsColors.accent
        UsbDebugLog.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = "${timeFmt.format(Date(entry.timestamp))} [${entry.level}] ${entry.tag}: ${entry.message}",
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = color,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.debug_clipboard_label), text))
}

private fun shareLog(context: Context, text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.debug_share_chooser)))
}

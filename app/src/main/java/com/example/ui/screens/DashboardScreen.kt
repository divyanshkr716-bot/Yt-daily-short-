package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobEntity
import com.example.ui.components.JobStatusBadge
import com.example.ui.components.StatusIndicatorDot
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenBatteryDialog: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val jobs by viewModel.allJobs.collectAsState()
    val unusedPhotosCount by viewModel.unusedPhotoCount.collectAsState()
    val isSyncingTelegram by viewModel.isSyncingTelegram.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()
    val renderProgress by viewModel.renderProgress.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()

    val isAutoPipelineRunning by viewModel.isAutoPipelineRunning.collectAsState()
    val autoPipelineStatusText by viewModel.autoPipelineStatusText.collectAsState()
    val autoPipelineStepNumber by viewModel.autoPipelineStepNumber.collectAsState()

    val tgStatus by viewModel.connectionStatusTelegram.collectAsState()
    val ytStatus by viewModel.connectionStatusYouTube.collectAsState()
    val aiStatus by viewModel.connectionStatusAi.collectAsState()

    var selectedJobForDetail by remember { mutableStateOf<JobEntity?>(null) }
    val secretCode by viewModel.secretCode.collectAsState()
    var secretInputText by remember(secretCode) { mutableStateOf(secretCode) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Active Profile Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE PROFILE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShortsRed,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeProfile?.name ?: "Default Profile",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Topic: ${activeProfile?.topic ?: "Facts & Knowledge"}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onNavigate("profiles") },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                                .testTag("btn_switch_profile")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Manage Profiles",
                                tint = TealAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val isAuto = activeProfile?.automationEnabled == true

                    // Mode Selector Banner (Manual vs Automatic)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Manual Mode Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isAuto) TealAccent else Color.Transparent)
                                .clickable { viewModel.toggleAutomation(false) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MANUAL MODE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isAuto) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Automatic Mode Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isAuto) ShortsRed else Color.Transparent)
                                .clickable { viewModel.toggleAutomation(true) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AUTOMATIC MODE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAuto) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode explanation & status row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            StatusIndicatorDot(isActive = isAuto)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isAuto) "Auto Mode: Generates & uploads in background on schedule" else "Manual Mode: User triggers photo selection, review & posting",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAuto) SuccessGreen else TealAccent
                                )
                                Text(
                                    text = if (isAuto) "Scheduled: ${viewModel.getNextRunFormatted()}" else "Full manual control enabled",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isAuto,
                            onCheckedChange = { viewModel.toggleAutomation(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ShortsRed
                            ),
                            modifier = Modifier.testTag("switch_automation")
                        )
                    }

                    // Early / Immediate Post Button (User can post anytime before schedule)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.runAutomationPipelineNow() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_post_early_pipeline"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAuto) "Post Now (Before Schedule)" else "Generate & Post Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Automatic Pipeline Live Tracker Card (Telegram -> Auto Render -> Scheduled Upload)
        if (activeProfile?.automationEnabled == true || isAutoPipelineRunning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusIndicatorDot(isActive = true)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AUTOPILOT PIPELINE ACTIVE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealAccent,
                                    letterSpacing = 1.sp
                                )
                            }
                            if (isAutoPipelineRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ShortsRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3-Step Visual Lifecycle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Step 1: Telegram Photo Sync
                            val step1Done = autoPipelineStepNumber > 1 || unusedPhotosCount >= 3
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (step1Done) SuccessGreen.copy(alpha = 0.2f) else DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = if (step1Done) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("1. Telegram", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Auto-Sync", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Text("➔", color = MaterialTheme.colorScheme.outline)

                            // Step 2: Auto Short Creator
                            val step2Done = autoPipelineStepNumber > 2 || jobs.any { it.status == "GENERATED" }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (step2Done) SuccessGreen.copy(alpha = 0.2f) else DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (step2Done) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("2. Short Video", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Auto-Render", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Text("➔", color = MaterialTheme.colorScheme.outline)

                            // Step 3: Scheduled Upload
                            val step3Done = jobs.any { it.status == "COMPLETED" }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (step3Done) SuccessGreen.copy(alpha = 0.2f) else DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        tint = if (step3Done) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("3. YouTube", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("On Schedule", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status text banner
                        val isAutomationOn = activeProfile?.automationEnabled == true
                        val currentText = autoPipelineStatusText ?: if (isAutomationOn) {
                            "App will automatically fetch Telegram channel photos, render 1080x1920 Short, and upload on schedule (${viewModel.getNextRunFormatted()})."
                        } else {
                            "Automatic mode is idle."
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = currentText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Ongoing Activity Progress (Rendering or Uploading)
        if (isRendering || isUploading || isSyncingTelegram) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ShortsRed.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = ShortsRed,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            val title = when {
                                isRendering -> "Rendering 1080x1920 Short ($renderProgress%)"
                                isUploading -> "Uploading to YouTube ($uploadProgress%)"
                                else -> "Syncing Telegram Channel..."
                            }
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Background processing in progress...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Connection Status Cards Grid
        item {
            Text(
                text = "SERVICE STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Telegram
                ServiceStatusRow(
                    title = "Telegram Channel",
                    subtitle = if (activeProfile?.telegramBotToken?.isNotBlank() == true) {
                        val ch = activeProfile?.telegramChannelUsername?.ifBlank { activeProfile?.telegramChannelChatId }
                        "Channel: $ch | Photos cached: $unusedPhotosCount"
                    } else "Not Configured",
                    statusText = tgStatus,
                    isConnected = activeProfile?.telegramBotToken?.isNotBlank() == true,
                    icon = Icons.Default.Send,
                    iconTint = Color(0xFF29B6F6),
                    onTestClick = { viewModel.testTelegramConnection() }
                )

                // YouTube
                ServiceStatusRow(
                    title = "YouTube API",
                    subtitle = if (activeProfile?.youtubeAccessToken?.isNotBlank() == true || activeProfile?.youtubeRefreshToken?.isNotBlank() == true) {
                        "Channel: ${activeProfile?.youtubeChannelTitle?.ifBlank { "Authorized" }} (${activeProfile?.youtubePrivacyStatus})"
                    } else "OAuth Not Connected",
                    statusText = ytStatus,
                    isConnected = activeProfile?.youtubeAccessToken?.isNotBlank() == true || activeProfile?.youtubeRefreshToken?.isNotBlank() == true,
                    icon = Icons.Default.PlayArrow,
                    iconTint = ShortsRed,
                    onTestClick = { viewModel.testYouTubeConnection() }
                )

                // AI Provider
                ServiceStatusRow(
                    title = "AI Provider",
                    subtitle = "Active: ${activeProfile?.primaryAiProvider} (Backup: ${activeProfile?.backupAiProvider})",
                    statusText = aiStatus,
                    isConnected = true,
                    icon = Icons.Default.AutoAwesome,
                    iconTint = AmberAccent,
                    onTestClick = { viewModel.testAiProvider(activeProfile?.primaryAiProvider ?: "GEMINI") }
                )
            }
        }

        // Quick Actions Section
        item {
            Text(
                text = "QUICK ACTIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.VideoLibrary,
                        label = "Generate Short",
                        sublabel = "Manual creation",
                        tint = ShortsRed,
                        onClick = { onNavigate("manual") },
                        testTag = "btn_quick_generate"
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Upload,
                        label = "Upload Now",
                        sublabel = "Run pipeline",
                        tint = TealAccent,
                        onClick = { viewModel.runAutomationPipelineNow() },
                        testTag = "btn_quick_upload"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Sync,
                        label = "Sync Telegram",
                        sublabel = "$unusedPhotosCount ready",
                        tint = Color(0xFF29B6F6),
                        onClick = { viewModel.syncTelegramPhotos() },
                        testTag = "btn_quick_sync"
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Alarm,
                        label = "Automation",
                        sublabel = "Schedules & Power",
                        tint = AmberAccent,
                        onClick = onOpenBatteryDialog,
                        testTag = "btn_quick_automation"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.History,
                        label = "Jobs History",
                        sublabel = "${jobs.size} jobs recorded",
                        tint = Color(0xFFAB47BC),
                        onClick = { onNavigate("jobs") },
                        testTag = "btn_quick_jobs"
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        sublabel = "All integrations",
                        tint = Color.LightGray,
                        onClick = { onNavigate("settings") },
                        testTag = "btn_quick_settings"
                    )
                }
            }
        }

        // Recent Jobs Preview List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT JOBS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "View All (${jobs.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ShortsRed,
                    modifier = Modifier.clickable { onNavigate("jobs") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (jobs.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Shorts generated yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'Generate Short' or turn on Automation to begin.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(jobs.take(4)) { job ->
            RecentJobItemCard(job = job, onClick = { selectedJobForDetail = job })
        }

        // Secret Browser Trigger Code Box (under Recent Jobs)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_secret_trigger_box"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(TealAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Access Code",
                                    tint = TealAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "YT",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealAccent,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "yt",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { onNavigate("calculator") },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .testTag("btn_open_calc_from_box")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Open Calculator",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = secretInputText,
                            onValueChange = { newText ->
                                val filtered = newText.filter { it.isDigit() }.take(10)
                                secretInputText = filtered
                                viewModel.updateSecretCode(filtered)
                            },
                            label = { Text("yt") },
                            placeholder = { Text("yt") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_secret_code"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Button(
                            onClick = { onNavigate("calculator") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            modifier = Modifier.testTag("btn_test_in_calculator")
                        ) {
                            Text("Test", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(
    title: String,
    subtitle: String,
    statusText: String,
    isConnected: Boolean,
    icon: ImageVector,
    iconTint: Color,
    onTestClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusIndicatorDot(isActive = isConnected)
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (statusText != "Not Checked") {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = if (statusText.contains("Connected") || statusText.contains("OK")) SuccessGreen else AmberAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onTestClick,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Test", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    sublabel: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sublabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentJobItemCard(
    job: JobEntity,
    onClick: () -> Unit
) {
    val dateStr = remember(job.createdAt) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(job.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JobStatusBadge(status = job.status)
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = job.title ?: "Untitled Short Video",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (job.errorMessage != null) {
                    Text(
                        text = job.errorMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (job.youtubeVideoId != null) {
                    Text(
                        text = "YouTube: youtu.be/${job.youtubeVideoId}",
                        fontSize = 11.sp,
                        color = TealAccent
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

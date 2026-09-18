package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MotionPhotosAuto
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ProfileEntity
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenBatteryDialog: () -> Unit
) {
    val context = LocalContext.current
    val activeProfile by viewModel.activeProfile.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Telegram", "AI", "YouTube", "Video", "Schedule", "Security")

    if (activeProfile == null) return
    val profile = activeProfile!!

    var botToken by remember(profile) { mutableStateOf(profile.telegramBotToken) }
    var channelUsername by remember(profile) { mutableStateOf(profile.telegramChannelUsername) }
    var channelChatId by remember(profile) { mutableStateOf(profile.telegramChannelChatId) }
    var syncLimit by remember(profile) { mutableFloatStateOf(profile.telegramSyncLimit.toFloat()) }
    var onlyPhotos by remember(profile) { mutableStateOf(profile.telegramOnlyPhotos) }
    var allowReuse by remember(profile) { mutableStateOf(profile.telegramAllowReuse) }

    // AI
    var primaryAi by remember(profile) { mutableStateOf(profile.primaryAiProvider) }
    var backupAi by remember(profile) { mutableStateOf(profile.backupAiProvider) }
    var geminiKey by remember(profile) { mutableStateOf(profile.geminiApiKey) }
    var youComKey by remember(profile) { mutableStateOf(profile.youComApiKey) }
    var openAiKey by remember(profile) { mutableStateOf(profile.openAiApiKey) }
    var openAiBaseUrl by remember(profile) { mutableStateOf(profile.openAiBaseUrl) }
    var openAiModel by remember(profile) { mutableStateOf(profile.openAiModel) }
    var customEndpoint by remember(profile) { mutableStateOf(profile.customAiEndpoint) }
    var customApiKey by remember(profile) { mutableStateOf(profile.customAiApiKey) }
    var defaultTitle by remember(profile) { mutableStateOf(profile.defaultUploadTitle) }
    var defaultDescription by remember(profile) { mutableStateOf(profile.defaultUploadDescription) }
    var defaultTags by remember(profile) { mutableStateOf(profile.defaultUploadTags) }

    // YouTube
    var ytClientId by remember(profile) { mutableStateOf(profile.youtubeClientId) }
    var ytClientSecret by remember(profile) { mutableStateOf(profile.youtubeClientSecret) }
    var ytPrivacy by remember(profile) { mutableStateOf(profile.youtubePrivacyStatus) }
    var ytDescTemplate by remember(profile) { mutableStateOf(profile.youtubeDescriptionTemplate) }
    var ytHashtags by remember(profile) { mutableStateOf(profile.youtubeDefaultHashtags) }
    var ytRetries by remember(profile) { mutableFloatStateOf(profile.youtubeUploadRetryCount.toFloat()) }
    var showAuthCodeDialog by remember { mutableStateOf(false) }
    var authCodeInput by remember { mutableStateOf("") }

    // Video Generator
    var targetDurationSec by remember(profile) { mutableFloatStateOf(profile.targetDurationSeconds.coerceIn(5, 60).toFloat()) }
    var photosCount by remember(profile) { mutableFloatStateOf(profile.photosPerShort.toFloat()) }
    var secPerPhoto by remember(profile) { mutableFloatStateOf(profile.secondsPerPhoto.toFloat()) }
    var transition by remember(profile) { mutableStateOf(profile.transitionEffect) }
    var zoom by remember(profile) { mutableStateOf(profile.zoomEffect) }
    var pan by remember(profile) { mutableStateOf(profile.panEffect) }
    var textOverlayMode by remember(profile) { mutableStateOf(profile.textOverlayMode) }
    var customCaptionText by remember(profile) { mutableStateOf(profile.customCaptionText) }
    var textPos by remember(profile) { mutableStateOf(profile.textPosition) }
    var enableIntro by remember(profile) { mutableStateOf(profile.enableIntro) }
    var introText by remember(profile) { mutableStateOf(profile.introText) }
    var enableOutro by remember(profile) { mutableStateOf(profile.enableOutro) }
    var outroText by remember(profile) { mutableStateOf(profile.outroText) }
    var enableWatermark by remember(profile) { mutableStateOf(profile.enableWatermark) }
    var watermarkText by remember(profile) { mutableStateOf(profile.watermarkText) }

    // CapCut Visual Tools & Effects
    var videoFilter by remember(profile) { mutableStateOf(profile.videoFilter) }
    var viralBannerType by remember(profile) { mutableStateOf(profile.viralBannerType) }
    var viralBannerText by remember(profile) { mutableStateOf(profile.viralBannerText) }
    var progressBarType by remember(profile) { mutableStateOf(profile.progressBarType) }
    var frameStyle by remember(profile) { mutableStateOf(profile.frameStyle) }
    var captionStyle by remember(profile) { mutableStateOf(profile.captionStyle) }
    var colorSaturation by remember(profile) { mutableFloatStateOf(profile.colorSaturation) }
    var colorContrast by remember(profile) { mutableFloatStateOf(profile.colorContrast) }
    var colorBrightness by remember(profile) { mutableFloatStateOf(profile.colorBrightness) }

    // Dynamic AI Variety & Real Video Effects
    var photoDurationMs by remember(profile) { mutableIntStateOf(profile.photoDurationMs.coerceAtLeast(30)) }
    var dynamicAiVariety by remember(profile) { mutableStateOf(profile.dynamicAiVariety) }
    var enableAiRealMotion by remember(profile) { mutableStateOf(profile.enableAiRealMotion) }
    var enableLensFlares by remember(profile) { mutableStateOf(profile.enableLensFlares) }
    var enableLightParticles by remember(profile) { mutableStateOf(profile.enableLightParticles) }
    var dynamicTimelineFx by remember(profile) { mutableStateOf(profile.dynamicTimelineFx) }

    // Automation & Sleep/Wake Lifecycle
    var autoEnabled by remember(profile) { mutableStateOf(profile.automationEnabled) }
    var dailySchedules by remember(profile) { mutableStateOf(profile.dailySchedulesCsv) }
    var enableSleepCycle by remember(profile) { mutableStateOf(profile.enableSleepCycle) }

    fun saveAll() {
        viewModel.updateProfile(
            profile.copy(
                telegramBotToken = botToken,
                telegramChannelUsername = channelUsername,
                telegramChannelChatId = channelChatId,
                telegramSyncLimit = syncLimit.toInt(),
                telegramOnlyPhotos = onlyPhotos,
                telegramAllowReuse = allowReuse,
                primaryAiProvider = primaryAi,
                backupAiProvider = backupAi,
                geminiApiKey = geminiKey,
                youComApiKey = youComKey,
                openAiApiKey = openAiKey,
                openAiBaseUrl = openAiBaseUrl,
                openAiModel = openAiModel,
                customAiEndpoint = customEndpoint,
                customAiApiKey = customApiKey,
                defaultUploadTitle = defaultTitle,
                defaultUploadDescription = defaultDescription,
                defaultUploadTags = defaultTags,
                youtubeClientId = ytClientId,
                youtubeClientSecret = ytClientSecret,
                youtubePrivacyStatus = ytPrivacy,
                youtubeDescriptionTemplate = ytDescTemplate,
                youtubeDefaultHashtags = ytHashtags,
                youtubeUploadRetryCount = ytRetries.toInt(),
                photosPerShort = photosCount.toInt(),
                secondsPerPhoto = secPerPhoto.toInt(),
                photoDurationMs = photoDurationMs,
                targetDurationSeconds = targetDurationSec.toInt(),
                transitionEffect = transition,
                zoomEffect = zoom,
                panEffect = pan,
                dynamicAiVariety = dynamicAiVariety,
                enableAiRealMotion = enableAiRealMotion,
                enableLensFlares = enableLensFlares,
                enableLightParticles = enableLightParticles,
                dynamicTimelineFx = dynamicTimelineFx,
                videoFilter = videoFilter,
                viralBannerType = viralBannerType,
                viralBannerText = viralBannerText,
                progressBarType = progressBarType,
                frameStyle = frameStyle,
                captionStyle = captionStyle,
                colorSaturation = colorSaturation,
                colorContrast = colorContrast,
                colorBrightness = colorBrightness,
                textOverlayMode = textOverlayMode,
                customCaptionText = customCaptionText,
                textPosition = textPos,
                enableIntro = enableIntro,
                introText = introText,
                enableOutro = enableOutro,
                outroText = outroText,
                enableWatermark = enableWatermark,
                watermarkText = watermarkText,
                automationEnabled = autoEnabled,
                dailySchedulesCsv = dailySchedules,
                enableSleepCycle = enableSleepCycle
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ShortsRed
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                // TELEGRAM TAB
                0 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("TELEGRAM INTEGRATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF29B6F6))
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = botToken,
                                    onValueChange = { botToken = it },
                                    label = { Text("Bot Token (from @BotFather)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth().testTag("input_tg_token"),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = channelUsername,
                                    onValueChange = { channelUsername = it },
                                    label = { Text("Channel Username (e.g. @mychannel)") },
                                    modifier = Modifier.fillMaxWidth().testTag("input_tg_username"),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = channelChatId,
                                    onValueChange = { channelChatId = it },
                                    label = { Text("Channel Chat ID (optional, e.g. -100123456789)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text("Sync Limit: ${syncLimit.toInt()} updates", fontSize = 13.sp)
                                Slider(
                                    value = syncLimit,
                                    onValueChange = { syncLimit = it },
                                    valueRange = 10f..100f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(thumbColor = ShortsRed, activeTrackColor = ShortsRed)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Only Photos (skip documents & videos)", fontSize = 13.sp)
                                    Switch(checked = onlyPhotos, onCheckedChange = { onlyPhotos = it })
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Allow Photo Reuse when pool empty", fontSize = 13.sp)
                                    Switch(checked = allowReuse, onCheckedChange = { allowReuse = it })
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            saveAll()
                                            viewModel.testTelegramConnection()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Test Bot & Channel")
                                    }
                                    Button(
                                        onClick = {
                                            saveAll()
                                            viewModel.syncTelegramPhotos()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                                    ) {
                                        Text("Sync Now")
                                    }
                                }
                            }
                        }
                    }
                }

                // AI TAB
                1 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("AI METADATA PROVIDERS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Primary Provider Dropdown
                                ProviderDropdown(
                                    label = "Primary AI Provider",
                                    selected = primaryAi,
                                    onSelected = { primaryAi = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Backup Provider Dropdown
                                ProviderDropdown(
                                    label = "Backup AI Provider",
                                    selected = backupAi,
                                    onSelected = { backupAi = it }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Google Gemini Key
                                OutlinedTextField(
                                    value = geminiKey,
                                    onValueChange = { geminiKey = it },
                                    label = { Text("Google Gemini API Key") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // You.com Key
                                OutlinedTextField(
                                    value = youComKey,
                                    onValueChange = { youComKey = it },
                                    label = { Text("You.com API Key") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // OpenAI API
                                OutlinedTextField(
                                    value = openAiKey,
                                    onValueChange = { openAiKey = it },
                                    label = { Text("OpenAI API Key") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = openAiBaseUrl,
                                    onValueChange = { openAiBaseUrl = it },
                                    label = { Text("OpenAI Base URL") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = openAiModel,
                                    onValueChange = { openAiModel = it },
                                    label = { Text("OpenAI Model (e.g. gpt-4o-mini)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = "MANUAL METADATA FALLBACK (IF AI DISCONNECTED)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAccent
                                )
                                Text(
                                    text = "If AI keys are not entered or offline, scheduled automation and manual uploads will automatically use these pre-set details.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = defaultTitle,
                                    onValueChange = { defaultTitle = it },
                                    label = { Text("Default Short Title") },
                                    placeholder = { Text("e.g. Incredible Mind-Blowing Facts #shorts") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = defaultDescription,
                                    onValueChange = { defaultDescription = it },
                                    label = { Text("Default Short Description") },
                                    placeholder = { Text("Subscribe for daily vertical shorts!") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = defaultTags,
                                    onValueChange = { defaultTags = it },
                                    label = { Text("Default Hashtags / Tags") },
                                    placeholder = { Text("#shorts #ytshorts #facts #viral") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            saveAll()
                                            viewModel.testAiProvider(primaryAi)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Test $primaryAi")
                                    }
                                    Button(
                                        onClick = { saveAll() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                                    ) {
                                        Text("Save Settings")
                                    }
                                }
                            }
                        }
                    }
                }

                // YOUTUBE TAB
                2 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("YOUTUBE DATA API V3 & OAUTH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ShortsRed)
                                Spacer(modifier = Modifier.height(12.dp))

                                val isChannelConnected = profile.youtubeChannelTitle.isNotBlank() || profile.youtubeAccessToken.isNotBlank()

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isChannelConnected) SuccessGreen.copy(alpha = 0.15f) else AmberAccent.copy(alpha = 0.15f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isChannelConnected) Icons.Default.Check else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isChannelConnected) SuccessGreen else AmberAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isChannelConnected) "Channel Connected" else "Channel Not Connected",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isChannelConnected) SuccessGreen else AmberAccent
                                        )
                                        Text(
                                            text = if (isChannelConnected) profile.youtubeChannelTitle.ifBlank { "YouTube Account Active" } else "Login to upload Shorts automatically",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = if (isChannelConnected) 
                                        "Channel change karne ke liye niche 'Change / Switch Channel' par tap karein aur uss channel ke Gmail se login karein."
                                    else 
                                        "Apne YouTube channel ke Gmail se sign in karein taaki Shorts seedhe upload ho sakein.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Main Google Login / Switch Channel Button
                                Button(
                                    onClick = {
                                        saveAll()
                                        // Google OAuth 2.0 Auth flow with select_account to always allow choosing/switching Gmail accounts
                                        val clientIdToUse = ytClientId.ifBlank { "1039805277023-android.apps.googleusercontent.com" }
                                        val redirect = "ytautoshorts://oauth2redirect"
                                        val scope = "https://www.googleapis.com/auth/youtube.upload%20https://www.googleapis.com/auth/youtube.readonly"
                                        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=$clientIdToUse&redirect_uri=$redirect&response_type=code&scope=$scope&access_type=offline&prompt=select_account%20consent"
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                        context.startActivity(browserIntent)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_google_signin_youtube"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ShortsRed),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isChannelConnected) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isChannelConnected) "Change / Switch YouTube Channel (Gmail)" else "Connect YouTube Channel (Google Login)",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                if (isChannelConnected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.disconnectYouTubeChannel()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_disconnect_youtube_channel"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Disconnect Current Channel", fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { showAuthCodeDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_enter_auth_code_manual"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Enter Authorization Code Manually", fontSize = 12.sp, color = TealAccent)
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Privacy Dropdown
                                GenericDropdown(
                                    label = "Default Privacy",
                                    selected = ytPrivacy,
                                    options = listOf("PRIVATE", "UNLISTED", "PUBLIC"),
                                    onSelected = { ytPrivacy = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = ytDescTemplate,
                                    onValueChange = { ytDescTemplate = it },
                                    label = { Text("Description Template") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = ytHashtags,
                                    onValueChange = { ytHashtags = it },
                                    label = { Text("Default Hashtags") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Upload Retries: ${ytRetries.toInt()}", fontSize = 13.sp)
                                Slider(
                                    value = ytRetries,
                                    onValueChange = { ytRetries = it },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(thumbColor = ShortsRed, activeTrackColor = ShortsRed)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        saveAll()
                                        viewModel.testYouTubeConnection()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                                ) {
                                    Text("Save & Verify YouTube")
                                }
                            }
                        }
                    }
                }

                // VIDEO GENERATOR TAB
                3 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("VIDEO GENERATOR (1080x1920)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealAccent)
                                Spacer(modifier = Modifier.height(12.dp))

                                // 1. Per-Second Total Video Duration
                                val durInt = targetDurationSec.roundToInt()
                                Text("Total Short Video Duration: ${durInt}s ${if (durInt >= 60) "(1 Min)" else ""}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TealAccent)
                                Text("Per-second exact control for YouTube Shorts (5s to 60s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (targetDurationSec > 5f) targetDurationSec -= 1f },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Minus 1 second", tint = TealAccent)
                                    }

                                    Slider(
                                        value = targetDurationSec,
                                        onValueChange = { targetDurationSec = it.roundToInt().toFloat() },
                                        valueRange = 5f..60f,
                                        steps = 54, // 1-second exact increments
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = TealAccent, activeTrackColor = TealAccent)
                                    )

                                    IconButton(
                                        onClick = { if (targetDurationSec < 60f) targetDurationSec += 1f },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Plus 1 second", tint = TealAccent)
                                    }
                                }

                                // Quick duration presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(10, 15, 20, 30, 45, 60).forEach { sec ->
                                        AssistChip(
                                            onClick = { targetDurationSec = sec.toFloat() },
                                            label = { Text("${sec}s", fontSize = 11.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (durInt == sec) TealAccent.copy(alpha = 0.25f) else Color.Transparent
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 2. Photos Count
                                Text("Photos per Short: ${photosCount.toInt()} (Range: 3 - 10)", fontSize = 13.sp)
                                Slider(
                                    value = photosCount,
                                    onValueChange = { photosCount = it },
                                    valueRange = 3f..10f,
                                    steps = 6,
                                    colors = SliderDefaults.colors(thumbColor = TealAccent, activeTrackColor = TealAccent)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 3. Granular Photo Pace / 30 Micro-Cut
                                val paceLabel = if (photoDurationMs <= 30) {
                                    "30ms (30 Micro-Cut Fast Beat 🔥)"
                                } else {
                                    "${photoDurationMs}ms (${String.format("%.2f", photoDurationMs / 1000f)}s)"
                                }
                                Text("Photo Display Pace: $paceLabel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AmberAccent)
                                Text("Supports ultra-fast 30ms micro-cuts up to 5000ms cinematic pacing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Slider(
                                    value = photoDurationMs.toFloat(),
                                    onValueChange = {
                                        val ms = it.roundToInt().coerceAtLeast(30)
                                        photoDurationMs = ms
                                        secPerPhoto = (ms / 1000f).coerceIn(1f, 5f)
                                    },
                                    valueRange = 30f..5000f,
                                    colors = SliderDefaults.colors(thumbColor = AmberAccent, activeTrackColor = AmberAccent)
                                )

                                // Quick pace chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        30 to "30ms 🔥",
                                        100 to "100ms",
                                        500 to "0.5s",
                                        1000 to "1s",
                                        2000 to "2s",
                                        3000 to "3s"
                                    ).forEach { (ms, label) ->
                                        AssistChip(
                                            onClick = {
                                                photoDurationMs = ms
                                                secPerPhoto = (ms / 1000f).coerceIn(1f, 5f)
                                            },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (photoDurationMs == ms) AmberAccent.copy(alpha = 0.25f) else Color.Transparent
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // AI DYNAMIC VARIETY & REAL-VIDEO ENGINE
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("AI REAL-VIDEO & DYNAMIC VARIETY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ShortsRed)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Dynamic Variety (Har Baar Alag-Alag)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Auto-rotates styles, colors & hooks on every Short", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = dynamicAiVariety, onCheckedChange = { dynamicAiVariety = it })
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("AI Real-Video 3D Motion", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Handheld sway & parallax drift makes still photos look alive", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = enableAiRealMotion, onCheckedChange = { enableAiRealMotion = it })
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Anamorphic Lens Flares & Sheen", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Hollywood optical streaks sweep across key frames", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = enableLensFlares, onCheckedChange = { enableLensFlares = it })
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Atmospheric Bokeh Particles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Floating optical dust motes & light specks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = enableLightParticles, onCheckedChange = { enableLightParticles = it })
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Intelligent Timeline FX Placement", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Hook flash at start, ambient flow in mid, climax vignette at end", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = dynamicTimelineFx, onCheckedChange = { dynamicTimelineFx = it })
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                GenericDropdown(
                                    label = "Default Transition Effect",
                                    selected = transition,
                                    options = listOf("CROSSFADE", "AI_WARP_ZOOM", "FLASH_WHITE", "ZOOM_BOUNCE", "GLITCH_FLASH", "SMOOTH_PUSH_UP", "WIPE_LEFT", "FADE", "NONE"),
                                    onSelected = { transition = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                GenericDropdown(
                                    label = "Default Camera Motion / Zoom",
                                    selected = zoom,
                                    options = listOf("PARALLAX_DRIFT", "HANDHELD_CAMERA", "DYNAMIC_PULSE", "SLOW_IN", "SLOW_OUT", "OFF"),
                                    onSelected = { zoom = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                GenericDropdown(
                                    label = "Pan Effect",
                                    selected = pan,
                                    options = listOf("OFF", "LEFT_TO_RIGHT", "RIGHT_TO_LEFT"),
                                    onSelected = { pan = it }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // CapCut-like Filters & Visual Effects
                                Text(
                                    text = "MANUAL OVERRIDE COLOR & VIRAL TOOLS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ShortsRed,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                GenericDropdown(
                                    label = "Video Filter (Color Grading)",
                                    selected = videoFilter,
                                    options = listOf(
                                        "AI_HYPER_REAL", "ANAMORPHIC_CINEMA", "DARK_FANTASY", "VIRAL_GLOW_POP",
                                        "VIBRANT_HDR", "TEAL_AND_ORANGE", "CYBERPUNK_NEON", "RETRO_FILM",
                                        "GOLDEN_HOUR", "GLITCH_AESTHETIC", "DREAMY_GLOW", "NOIR_BW", "NORMAL"
                                    ),
                                    onSelected = { videoFilter = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("Color Saturation: ${String.format("%.1f", colorSaturation)}x", fontSize = 12.sp)
                                Slider(
                                    value = colorSaturation,
                                    onValueChange = { colorSaturation = it },
                                    valueRange = 0.0f..2.0f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(thumbColor = ShortsRed, activeTrackColor = ShortsRed)
                                )

                                Text("Contrast: ${String.format("%.1f", colorContrast)}x", fontSize = 12.sp)
                                Slider(
                                    value = colorContrast,
                                    onValueChange = { colorContrast = it },
                                    valueRange = 0.5f..1.8f,
                                    steps = 12,
                                    colors = SliderDefaults.colors(thumbColor = ShortsRed, activeTrackColor = ShortsRed)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Viral Attention Banner
                                GenericDropdown(
                                    label = "Viral Attention Hook Banner",
                                    selected = viralBannerType,
                                    options = listOf("NONE", "VIRAL_YELLOW", "BREAKING_RED", "HORMOZI_BOX", "MEME_HEADLINE"),
                                    onSelected = { viralBannerType = it }
                                )

                                if (viralBannerType != "NONE") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = viralBannerText,
                                        onValueChange = { viralBannerText = it },
                                        label = { Text("Banner Hook Text (e.g. WAIT TILL THE END 😱)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Audience Retention Progress Bar
                                GenericDropdown(
                                    label = "Audience Retention Progress Bar",
                                    selected = progressBarType,
                                    options = listOf("YOUTUBE_RED", "NEON_CYAN", "GOLD_PREMIUM", "NONE"),
                                    onSelected = { progressBarType = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Frame Aesthetic FX
                                GenericDropdown(
                                    label = "Frame & Aesthetic Style",
                                    selected = frameStyle,
                                    options = listOf("NONE", "CINEMATIC_LETTERBOX", "NEON_BORDER", "VINTAGE_VIGNETTE", "VHS_OVERLAY"),
                                    onSelected = { frameStyle = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Dynamic Caption Glowing Style
                                GenericDropdown(
                                    label = "Caption Styling & Glow",
                                    selected = captionStyle,
                                    options = listOf("HORMOZI_YELLOW", "NEON_CYAN", "CLEAN_WHITE", "RED_ALERT"),
                                    onSelected = { captionStyle = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                GenericDropdown(
                                    label = "Text Overlay Mode",
                                    selected = textOverlayMode,
                                    options = listOf("AI_CAPTION", "CUSTOM", "NONE"),
                                    onSelected = { textOverlayMode = it }
                                )

                                if (textOverlayMode == "CUSTOM") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = customCaptionText,
                                        onValueChange = { customCaptionText = it },
                                        label = { Text("Custom Caption Text") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                GenericDropdown(
                                    label = "Caption Position",
                                    selected = textPos,
                                    options = listOf("BOTTOM", "CENTER", "TOP"),
                                    onSelected = { textPos = it }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enable Intro Slide", fontSize = 13.sp)
                                    Switch(checked = enableIntro, onCheckedChange = { enableIntro = it })
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enable Outro Slide", fontSize = 13.sp)
                                    Switch(checked = enableOutro, onCheckedChange = { enableOutro = it })
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enable Watermark Overlay", fontSize = 13.sp)
                                    Switch(checked = enableWatermark, onCheckedChange = { enableWatermark = it })
                                }

                                if (enableWatermark) {
                                    OutlinedTextField(
                                        value = watermarkText,
                                        onValueChange = { watermarkText = it },
                                        label = { Text("Watermark Text (e.g. @MyChannel)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { saveAll() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                                ) {
                                    Text("Save Video Settings")
                                }
                            }
                        }
                    }
                }

                // SCHEDULE & AUTOMATION TAB
                4 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("AUTOMATION & SCHEDULES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enable Automation for this Profile", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Switch(
                                        checked = autoEnabled,
                                        onCheckedChange = { autoEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ShortsRed)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = dailySchedules,
                                    onValueChange = { dailySchedules = it },
                                    label = { Text("Daily Schedules (CSV format, e.g. 09:00,21:00)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick schedule preset buttons (including 12h cycle)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { dailySchedules = "09:00,21:00" },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("12h Cycle (09:00, 21:00)", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { dailySchedules = "08:00,14:00,20:00" },
                                        modifier = Modifier.weight(0.7f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("3x Daily", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Sleep and Wake Cycle Automation
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Deep Sleep & Scheduled Wakeup Mode",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealAccent
                                                )
                                                Text(
                                                    text = "App renders video, enters 0% CPU sleep, wakes up via alarm at exact time, uploads to YouTube, and sleeps again.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = enableSleepCycle,
                                                onCheckedChange = { enableSleepCycle = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = TealAccent
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "• 💤 Render & Sleep: Prepares video and puts process to sleep\n" +
                                                   "• ⏰ On-Time Wakeup: Hardware WakeLock triggers at scheduled upload time\n" +
                                                   "• 🚀 Auto-Upload: Uploads Short to YouTube with viral tags\n" +
                                                   "• 🔁 Next-Day Cycle: Automatically returns to sleep until next scheduled slot",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Fallback Title & Metadata Protection
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Automatic Title & Metadata Fallback",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberAccent
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "If AI is disconnected or no custom title is provided, the system automatically reuses your previous successful Short's title and description so scheduled uploads never fail or get skipped.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Battery Saver Notice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Aggressive Android background battery savers can delay automated alarms. To ensure on-time uploads, exclude the app from battery optimization.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = onOpenBatteryDialog,
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black)
                                        ) {
                                            Text("Battery Optimization Settings", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { saveAll() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                                ) {
                                    Text("Save Schedule Settings")
                                }
                            }
                        }
                    }
                }

                // SECURITY TAB
                5 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ENCRYPTED CREDENTIAL STORAGE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "All bot tokens, API keys, and OAuth refresh tokens are encrypted using hardware Android Keystore AES-256 GCM. Secrets are never printed to Logcat or exposed over unencrypted storage.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = {
                                        botToken = ""
                                        geminiKey = ""
                                        youComKey = ""
                                        openAiKey = ""
                                        ytClientId = ""
                                        ytClientSecret = ""
                                        saveAll()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                ) {
                                    Text("Clear Stored Credentials")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAuthCodeDialog) {
        AlertDialog(
            onDismissRequest = { showAuthCodeDialog = false },
            title = { Text("Paste Google OAuth Code", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "After approving in your browser, copy the authorization code from the address bar or page and paste it here:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = authCodeInput,
                        onValueChange = { authCodeInput = it },
                        label = { Text("Authorization Code (starts with 4/...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (authCodeInput.isNotBlank()) {
                            saveAll()
                            viewModel.handleOAuthAuthorizationCode(authCodeInput.trim())
                        }
                        showAuthCodeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                ) {
                    Text("Submit Code")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAuthCodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    label: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("GEMINI", "YOU_COM", "OPENAI", "CUSTOM")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

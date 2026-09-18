package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.BatteryOptimizationDialog
import com.example.ui.components.StatusIndicatorDot
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.JobsHistoryScreen
import com.example.ui.screens.ManualModeScreen
import com.example.ui.screens.PrivateBrowserScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TelegramLibraryScreen
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.TealAccent
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleOAuthIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "ytautoshorts" && uri.host == "oauth2redirect") {
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                viewModel.handleOAuthAuthorizationCode(code)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // When app goes to recent tab or is put in background, immediately close the incognito browser
        viewModel.onAppSentToBackground()
    }
}

sealed class NavDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : NavDestination("dashboard", "Home", Icons.Default.Dashboard)
    data object Manual : NavDestination("manual", "Studio", Icons.Default.VideoLibrary)
    data object Telegram : NavDestination("telegram", "Telegram", Icons.Default.Send)
    data object Jobs : NavDestination("jobs", "History", Icons.Default.History)
    data object Settings : NavDestination("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavDestination.Dashboard.route

    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    val isBrowserOpen by viewModel.isBrowserOpen.collectAsState()
    val isCalculatorOpen by viewModel.isCalculatorOpen.collectAsState()
    val secretCode by viewModel.secretCode.collectAsState()

    var showBatteryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    val destinations = listOf(
        NavDestination.Dashboard,
        NavDestination.Manual,
        NavDestination.Telegram,
        NavDestination.Jobs,
        NavDestination.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkCanvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ShortsRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", fontSize = 12.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YT Auto Shorts",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    // Calculator Icon Button next to profile
                    IconButton(
                        onClick = { viewModel.openCalculator() },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .testTag("btn_top_bar_calculator")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Calculator",
                            tint = TealAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Quick Profile Pill
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .clickable { navController.navigate("profiles") }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIndicatorDot(isActive = activeProfile?.automationEnabled == true)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeProfile?.name ?: "Profile",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkCanvas,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                destinations.forEach { dest ->
                    val isSelected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(NavDestination.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = dest.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = ShortsRed,
                            indicatorColor = ShortsRed,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_${dest.route}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = NavDestination.Dashboard.route
            ) {
                composable(NavDestination.Dashboard.route) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { route -> navController.navigate(route) },
                        onOpenBatteryDialog = { showBatteryDialog = true }
                    )
                }
                composable(NavDestination.Manual.route) {
                    ManualModeScreen(
                        viewModel = viewModel,
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }
                composable(NavDestination.Telegram.route) {
                    TelegramLibraryScreen(viewModel = viewModel)
                }
                composable(NavDestination.Jobs.route) {
                    JobsHistoryScreen(viewModel = viewModel)
                }
                composable(NavDestination.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenBatteryDialog = { showBatteryDialog = true }
                    )
                }
                composable("profiles") {
                    ProfilesScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenCalculator = { viewModel.openCalculator() }
                    )
                }
                composable("calculator") {
                    CalculatorScreen(
                        secretCode = secretCode,
                        onSecretCodeMatched = {
                            viewModel.openBrowser()
                        },
                        onCloseCalculator = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }

    // Modal Full-Screen Calculator (when opened via header icon or anywhere)
    if (isCalculatorOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCanvas)
        ) {
            CalculatorScreen(
                secretCode = secretCode,
                onSecretCodeMatched = {
                    viewModel.openBrowser()
                },
                onCloseCalculator = {
                    viewModel.closeCalculator()
                }
            )
        }
    }

    // Modal Full-Screen Private Incognito Browser (automatically closed on recent tabs/background)
    if (isBrowserOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCanvas)
        ) {
            PrivateBrowserScreen(
                initialUrl = "https://www.google.com",
                onCloseBrowser = {
                    viewModel.closeBrowser()
                }
            )
        }
    }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showBatteryDialog = false }
        )
    }
}

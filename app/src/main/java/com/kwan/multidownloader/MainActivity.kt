package com.kwan.multidownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.multidownloader.data.settings.Settings
import com.kwan.multidownloader.data.settings.SettingsRepository
import com.kwan.multidownloader.ui.components.AppTopBar
import com.kwan.multidownloader.ui.components.CompactNavigationBar
import com.kwan.multidownloader.ui.components.CompactNavigationBarItem
import com.kwan.multidownloader.ui.components.FirstRunNotice
import com.kwan.multidownloader.ui.components.LocalCompactBars
import com.kwan.multidownloader.ui.downloads.DownloadsScreen
import com.kwan.multidownloader.ui.home.HomeScreen
import com.kwan.multidownloader.ui.settings.LicencesScreen
import com.kwan.multidownloader.ui.settings.SettingsScreen
import com.kwan.multidownloader.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

@Serializable
object HomeRoute

@Serializable
object DownloadsRoute

@Serializable
object SettingsRoute

/** Opened from Settings → About; not a bottom-bar tab. */
@Serializable
object LicencesRoute

private data class TopLevelRoute(val route: Any, val labelRes: Int, val icon: ImageVector, val titleRes: Int = labelRes)

private val topLevelRoutes = listOf(
    TopLevelRoute(HomeRoute, R.string.nav_home, Icons.Filled.Home, titleRes = R.string.app_name),
    TopLevelRoute(DownloadsRoute, R.string.nav_downloads, Icons.Filled.Download),
    TopLevelRoute(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    /** Bumped whenever a notification asks to open the Downloads tab. */
    private var openDownloadsRequests by mutableIntStateOf(0)

    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps the launch theme for Theme.MultiDownloader.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings load, so the theme and first-run notice don't pop in a frame late.
        var settingsLoaded = false
        splash.setKeepOnScreenCondition { !settingsLoaded }
        lifecycleScope.launch {
            settingsRepository.settings.first()
            settingsLoaded = true
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppTheme {
                AppRoot(openDownloadsRequests = openDownloadsRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) openDownloadsRequests++
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }
}

@Composable
private fun AppRoot(openDownloadsRequests: Int, settingsRepository: SettingsRepository = koinInject()) {
    val navController = rememberNavController()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val compact = settings?.compactBars ?: Settings().compactBars
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val onLicences = destination?.hasRoute(LicencesRoute::class) == true
    val current = topLevelRoutes.firstOrNull { item -> destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true }
        ?: topLevelRoutes.first()
    val title = stringResource(if (onLicences) R.string.licences_title else current.titleRes)
    val context = LocalContext.current
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(openDownloadsRequests) {
        if (openDownloadsRequests > 0) navController.navigateTopLevel(DownloadsRoute)
    }

    // First launch: personal-use / copyright notice before anything else (settings loaded, not yet accepted).
    if (settings?.noticeAccepted == false) {
        val scope = rememberCoroutineScope()
        FirstRunNotice(onAccept = { scope.launch { settingsRepository.update { it.copy(noticeAccepted = true) } } })
    }

    CompositionLocalProvider(LocalCompactBars provides compact) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppTopBar(title = title, onBack = if (onLicences) ({ navController.popBackStack() }) else null) },
        bottomBar = { if (!onLicences) BottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeScreen(onEnqueued = {
                    Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_SHORT).show()
                    // Ask for POST_NOTIFICATIONS (33+) the first time a download is queued.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        askedForNotifications = true
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    navController.navigateTopLevel(DownloadsRoute)
                })
            }
            composable<DownloadsRoute> {
                DownloadsScreen()
            }
            composable<SettingsRoute> {
                SettingsScreen(onOpenLicences = { navController.navigate(LicencesRoute) })
            }
            composable<LicencesRoute> {
                LicencesScreen()
            }
        }
    }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val compact = LocalCompactBars.current
    fun isSelected(item: TopLevelRoute) = destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true

    if (compact) {
        CompactNavigationBar {
            topLevelRoutes.forEach { item ->
                CompactNavigationBarItem(
                    selected = isSelected(item),
                    onClick = { navController.navigateTopLevel(item.route) },
                    icon = item.icon,
                    label = stringResource(item.labelRes),
                )
            }
        }
    } else {
        NavigationBar {
            topLevelRoutes.forEach { item ->
                NavigationBarItem(
                    selected = isSelected(item),
                    onClick = { navController.navigateTopLevel(item.route) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(stringResource(item.labelRes)) },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

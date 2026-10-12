package net.ankio.bluetooth.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.widget.Toast
import kotlinx.coroutines.delay
import net.ankio.bluetooth.R
import net.ankio.bluetooth.ui.MainActivity
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.ui.navigation.AppTab
import net.ankio.bluetooth.ui.viewmodel.HomeViewModel
import net.ankio.bluetooth.utils.SpUtils
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*

@Composable
fun MainScreen(activity: MainActivity) {
    val mode = preference("ui_theme") { SpUtils.getInt("ui_theme", 0) }
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        var tab by rememberSaveable { mutableIntStateOf(1) }
        var scanning by rememberSaveable { mutableStateOf(false) }
        val home: HomeViewModel = viewModel()
        val lifecycle = LocalLifecycleOwner.current
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) home.connect()
            }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(home, lifecycle) {
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { home.refreshStatus(); delay(1000) }
            }
        }
        LaunchedEffect(home.message) {
            home.message?.let { Toast.makeText(activity, it, Toast.LENGTH_LONG).show(); home.message = null }
        }
        BackHandler(scanning) { scanning = false }
        // The scan page owns its own Scaffold (title bar + FABs) and replaces the tab shell.
        val tabs = AppTab.entries
        if (scanning) ScanScreen(close = { scanning = false })
        else Scaffold(
            topBar = {
                Text(stringResource(tabs[tab].title),
                    modifier = Modifier.statusBarsPadding().padding(24.dp),
                    fontSize = 28.sp)
            },
            bottomBar = {
                val floating = preference(Keys.FLOATING_TABS) { SpUtils.getBoolean(Keys.FLOATING_TABS) }
                if (floating) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FloatingToolbar(outSidePadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), shadowElevation = 0.dp) {
                        Row(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            tabs.forEachIndexed { index, item ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(28.dp))
                                        .background(if (tab == index) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                        .selectable(selected = tab == index, role = Role.Tab, onClick = { tab = index })) {
                                    Icon(ImageVector.vectorResource(item.icon), null, modifier = Modifier.size(26.dp),
                                        tint = if (tab == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                                    Text(stringResource(item.title), fontSize = 12.sp,
                                        color = if (tab == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                } else NavigationBar {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = ImageVector.vectorResource(item.icon), label = stringResource(item.title))
                    }
                }
            },
            floatingActionButton = {
                if (tab == 1) FloatingActionButton(onClick = { scanning = true }) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_bluetooth_scan),
                        modifier = Modifier.size(28.dp), tint = MiuixTheme.colorScheme.onPrimary,
                        contentDescription = stringResource(R.string.scan_blue))
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                when (tab) {
                    0 -> WebdavScreen(home)
                    1 -> HomeScreen(home)
                    2 -> SettingsScreen(activity)
                }
            }
        }
    }
}

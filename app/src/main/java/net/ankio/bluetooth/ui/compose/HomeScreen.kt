package net.ankio.bluetooth.ui.compose

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.ankio.bluetooth.R
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.ui.viewmodel.HomeViewModel
import net.ankio.bluetooth.utils.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(model: HomeViewModel) {
    val context = LocalContext.current
    val enabled = preference(Keys.ENABLED) { SpUtils.getBoolean(Keys.ENABLED) }
    val webdav = preference(Keys.WEBDAV_ENABLED) { SpUtils.getBoolean(Keys.WEBDAV_ENABLED) }
    val sender = preference(Keys.WEBDAV_SENDER) { SpUtils.getBoolean(Keys.WEBDAV_SENDER) }
    val advertisingState by model.advertising.collectAsStateWithLifecycle()
    val advertising = advertisingState.active
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) model.startAdvertising()
        else model.message = context.getString(R.string.not_open_bluetooth)
    }
    fun start() {
        if (BleAdvertiserManager.isBluetoothEnabled(context)) model.startAdvertising()
        else runCatching { bluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { model.message = it.message }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) start()
        else model.message = context.getString(R.string.no_permission)
    }
    Page {
        val success = model.status == R.string.active_success || model.status == R.string.server_working
        Card(colors = CardDefaults.defaultColors(
            color = if (success) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
            contentColor = if (success) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onError)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(model.status))
                Text(ModuleStatus.activation?.let {
                    stringResource(R.string.about_framework,
                        if (it.framework == ModuleStatus.Framework.LIBXPOSED) "LibXposed" else "Xposed", it.apiVersion)
                } ?: stringResource(R.string.framework_inactive), fontSize = 13.sp)
            }
        }
        SmallTitle(stringResource(R.string.bluetooth_data))
        PreferenceField(Keys.MAC, R.string.mac_data, onChange = model::saveToHistory)
        PreferenceField(Keys.DATA, R.string.broadcast_data, multiLine = true, onChange = model::saveToHistory)
        Card {
            // Stored as a dBm string in [-100, 0]; the slider runs 0..100 like the old SeekBar.
            val rssi = preference(Keys.RSSI) { SpUtils.getString(Keys.RSSI, "-50").toIntOrNull() ?: -50 }
                .coerceIn(-100, 0)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.signal)}: $rssi dBm")
                Slider(value = (rssi + 100).toFloat(), valueRange = 0f..100f, onValueChange = {
                    val value = it.toInt() - 100
                    if (value != rssi) { SpUtils.putString(Keys.RSSI, value.toString()); model.saveToHistory() }
                })
            }
        }
        Card {
            SwitchPreference(title = stringResource(R.string.use_mock), checked = enabled,
                enabled = !(webdav && sender), onCheckedChange = { SpUtils.putBoolean(Keys.ENABLED, it) })
            SwitchPreference(title = stringResource(R.string.ble_advertiser_title), checked = advertising,
                summary = if (advertising) stringResource(R.string.advertising_remaining, model.remaining)
                    else stringResource(R.string.ble_advertiser_desc),
                onCheckedChange = {
                    if (!it) model.stopAdvertising()
                    else {
                        val missing = BleAdvertiserManager.getMissingPermissions(context)
                        if (missing.isEmpty()) start() else permissions.launch(missing)
                    }
                })
        }
    }
}

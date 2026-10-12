package net.ankio.bluetooth.ui.compose

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import net.ankio.bluetooth.R
import net.ankio.bluetooth.ui.viewmodel.ScanViewModel
import net.ankio.bluetooth.utils.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanScreen(close: () -> Unit, model: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var filtering by rememberSaveable { mutableStateOf(false) }
    var restart by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<net.ankio.bluetooth.bluetooth.BleDevice?>(null) }
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) model.start()
        else model.message = context.getString(R.string.not_open_bluetooth)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) {
            if (model.bluetoothEnabled()) model.start()
            else runCatching { bluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { error -> model.message = error.message }
        } else model.message = context.getString(R.string.no_permission)
    }
    fun start() {
        if (!model.supported()) { model.message = context.getString(R.string.unsupported_bluetooth); return }
        permissions.launch(BluetoothPermissions.missing(context, BluetoothPermissions.Operation.SCAN))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.stop() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); model.stop() }
    }
    LaunchedEffect(Unit) { model.resetToScan() }
    LaunchedEffect(model.message) {
        model.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); model.message = null }
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(if (model.showingHistory) R.string.nav_history else R.string.scan_blue),
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(ImageVector.vectorResource(R.drawable.ic_back), stringResource(R.string.back),
                            modifier = Modifier.size(22.dp))
                    }
                },
                actions = {
                    IconButton(onClick = model::showHistory) {
                        Icon(ImageVector.vectorResource(R.drawable.ic_history), stringResource(R.string.nav_history),
                            modifier = Modifier.size(22.dp))
                    }
                })
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { restart = model.isScanning; model.stop(); filtering = true },
                    containerColor = MiuixTheme.colorScheme.surfaceContainer) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_filter), stringResource(R.string.filter),
                        modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface)
                }
                FloatingActionButton(onClick = { if (model.isScanning) model.stop() else start() }) {
                    if (model.isScanning) CircularProgressIndicator(size = 24.dp)
                    else Icon(ImageVector.vectorResource(R.drawable.ic_bluetooth_scan), stringResource(R.string.start_scan),
                        modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (model.devices.isEmpty()) item { Text(stringResource(R.string.ic_empty), Modifier.padding(24.dp)) }
            items(model.devices, key = { it.address }) { device ->
                Card {
                    ArrowPreference(title = device.name ?: "None",
                        summary = "${device.company}\n${device.address}  •  ${device.rssi} dBm\n${device.data}",
                        onClick = { pending = device })
                    if (model.showingHistory) TextButton(stringResource(R.string.delete_history),
                        onClick = { model.delete(device) })
                }
            }
        }
    }
    if (filtering) {
        val dismiss = { filtering = false; if (restart) start() }
        ChoiceDialog(stringResource(R.string.filter), dismiss) {
            PreferenceField(BleConstant.COMPANY, R.string.company_filter)
            val empty = preference(BleConstant.NULL_NAME) { SpUtils.getBoolean(BleConstant.NULL_NAME) }
            SwitchPreference(title = stringResource(R.string.filter_empty), checked = empty,
                onCheckedChange = { SpUtils.putBoolean(BleConstant.NULL_NAME, it) })
            val rssi = preference(BleConstant.RSSI) { SpUtils.getInt(BleConstant.RSSI, 100) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.signal_filter)}: -$rssi dBm", color = MiuixTheme.colorScheme.onSurface)
                Slider(value = rssi.toFloat(), onValueChange = { SpUtils.putInt(BleConstant.RSSI, it.toInt()) },
                    valueRange = 0f..100f)
            }
        }
    }
    pending?.let { device ->
        ChoiceDialog(stringResource(R.string.scan_apply_title), { pending = null }, showClose = false) {
            Text(stringResource(R.string.scan_apply_message), color = MiuixTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(stringResource(R.string.cancel), onClick = { pending = null }, modifier = Modifier.weight(1f))
                TextButton(stringResource(R.string.ok), onClick = { if (model.select(device)) { pending = null; close() } },
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

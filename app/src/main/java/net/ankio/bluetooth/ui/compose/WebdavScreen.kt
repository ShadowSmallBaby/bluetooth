package net.ankio.bluetooth.ui.compose

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import net.ankio.bluetooth.R
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.ui.viewmodel.HomeViewModel
import net.ankio.bluetooth.utils.SpUtils
import net.ankio.bluetooth.utils.BleAdvertiserManager
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceField(key: String, title: Int, default: String = "", password: Boolean = false,
                    multiLine: Boolean = false, onChange: (() -> Unit)? = null) {
    val value = preference(key) { SpUtils.getString(key, default) }
    var visible by rememberSaveable(key) { mutableStateOf(false) }
    TextField(value = value, onValueChange = { SpUtils.putString(key, it); onChange?.invoke() },
        label = stringResource(title), modifier = Modifier.fillMaxWidth(),
        singleLine = !multiLine, minLines = if (multiLine) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!password) null else { {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(if (visible) MiuixIcons.Hide else MiuixIcons.Show,
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
            }
        } })
}

@Composable
fun WebdavScreen(model: HomeViewModel) {
    val context = LocalContext.current
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) model.connect(restartSender = true)
        else model.message = context.getString(R.string.not_open_bluetooth)
    }
    fun connectWithBluetooth() {
        if (!model.isSender() || BleAdvertiserManager.isBluetoothEnabled(context)) model.connect(restartSender = true)
        else runCatching { bluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { model.message = it.message }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) connectWithBluetooth()
        else model.message = context.getString(R.string.no_permission)
    }
    fun connect() {
        val missing = model.senderPermissions()
        if (model.isSender() && missing.isNotEmpty()) permissions.launch(missing)
        else connectWithBluetooth()
    }
    val enabled = preference(Keys.WEBDAV_ENABLED) { SpUtils.getBoolean(Keys.WEBDAV_ENABLED) }
    val sender = preference(Keys.WEBDAV_SENDER) { SpUtils.getBoolean(Keys.WEBDAV_SENDER) }
    val last = preference(Keys.WEBDAV_LAST) { SpUtils.getString(Keys.WEBDAV_LAST) }
    Box(Modifier.fillMaxSize()) {
        Page {
            Card {
                SwitchPreference(title = stringResource(R.string.webdav_enable), checked = enabled,
                    onCheckedChange = { SpUtils.putBoolean(Keys.WEBDAV_ENABLED, it); connect() })
                SwitchPreference(title = stringResource(R.string.as_sender), checked = sender,
                    onCheckedChange = {
                        SpUtils.putBoolean(Keys.WEBDAV_SENDER, it)
                        if (it) SpUtils.putBoolean(Keys.ENABLED, false)
                        connect()
                    })
            }
            PreferenceField(Keys.WEBDAV_SERVER, R.string.webdav_data, "https://dav.jianguoyun.com/dav/")
            PreferenceField(Keys.WEBDAV_USERNAME, R.string.webdav_username)
            PreferenceField(Keys.WEBDAV_PASSWORD, R.string.webdav_password, password = true)
            PreferenceField(Keys.WEBDAV_COMPANY, R.string.company_data)
            PreferenceField(Keys.WEBDAV_MAC, R.string.mac_data)
            Text(if (last.isEmpty()) stringResource(R.string.webdav_no_sync) else stringResource(R.string.last_sync, last),
                modifier = Modifier.padding(8.dp))
        }
        FloatingActionButton(onClick = { if (enabled && !model.syncing) connect() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            if (model.syncing) CircularProgressIndicator(size = 24.dp)
            else Icon(ImageVector.vectorResource(R.drawable.ic_cloud), stringResource(R.string.sync_now),
                modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onPrimary)
        }
    }
}

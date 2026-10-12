package net.ankio.bluetooth.ui.compose

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.ankio.bluetooth.BuildConfig
import net.ankio.bluetooth.R
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import androidx.lifecycle.viewmodel.compose.viewModel
import net.ankio.bluetooth.ui.viewmodel.SettingsViewModel
import net.ankio.bluetooth.ui.MainActivity
import net.ankio.bluetooth.update.UpdateChecker
import net.ankio.bluetooth.utils.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.json.JSONObject
import java.util.Locale

@Composable
fun SettingsScreen(activity: MainActivity, model: SettingsViewModel = viewModel()) {
    var dialog by rememberSaveable { mutableStateOf("") }
    val busy = model.busy
    val revision = model.revision
    Page {
        SmallTitle(stringResource(R.string.theme_group))
        Card {
            val theme = preference("ui_theme") { SpUtils.getInt("ui_theme", 0) }
            val themes = listOf(R.string.lang_follow_system, R.string.always_off, R.string.always_on)
                .map { DropdownItem(stringResource(it)) }
            OverlaySpinnerPreference(items = themes, selectedIndex = theme.coerceIn(themes.indices),
                title = stringResource(R.string.setting_dark_theme),
                onSelectedIndexChange = { SpUtils.putInt("ui_theme", it) })
            val floating = preference(Keys.FLOATING_TABS) { SpUtils.getBoolean(Keys.FLOATING_TABS) }
            SwitchPreference(title = stringResource(R.string.floating_tabs), checked = floating,
                onCheckedChange = { SpUtils.putBoolean(Keys.FLOATING_TABS, it) })
        }
        SmallTitle(stringResource(R.string.general))
        Card {
            val language = preference(Keys.LANGUAGE) { SpUtils.getString(Keys.LANGUAGE, "SYSTEM") }
            val tags = remember { activity.resources.getStringArray(R.array.supported_languages).toList() }
            val followSystem = stringResource(R.string.lang_follow_system)
            val languages = tags.map { tag ->
                DropdownItem(if (tag == "SYSTEM") followSystem
                    else Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(tag)))
            }
            OverlaySpinnerPreference(items = languages, selectedIndex = tags.indexOf(language).coerceAtLeast(0),
                title = stringResource(R.string.setting_lang),
                onSelectedIndexChange = {
                    if (tags[it] != language) { SpUtils.putString(Keys.LANGUAGE, tags[it]); activity.changeLanguage() }
                })
            val hidden = preference(Keys.HIDE_ICON) { SpUtils.getBoolean(Keys.HIDE_ICON) }
            SwitchPreference(title = stringResource(R.string.hide_icon), checked = hidden, onCheckedChange = {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity, BuildConfig.APPLICATION_ID + ".MainActivityLauncher"),
                    if (it) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP)
                SpUtils.putBoolean(Keys.HIDE_ICON, it)
            })
            val log = preference(Keys.HOOK_LOG) { SpUtils.getBoolean(Keys.HOOK_LOG) }
            SwitchPreference(title = stringResource(R.string.hook_log_title), summary = stringResource(R.string.hook_log_desc),
                checked = log, onCheckedChange = { SpUtils.putBoolean(Keys.HOOK_LOG, it) })
            val compatibilityRequired = BleAdvertisingMode.isCompatibilityRequired(Build.VERSION.SDK_INT)
            val compatibility = preference(Keys.BLE_ADVERTISER_COMPATIBILITY) {
                BleAdvertiserManager.isCompatibilityModeEnabled()
            }
            SwitchPreference(title = stringResource(R.string.ble_advertiser_compatibility),
                summary = stringResource(if (compatibilityRequired) R.string.ble_advertiser_compatibility_required
                    else R.string.ble_advertiser_compatibility_desc),
                checked = compatibility, enabled = !compatibilityRequired,
                onCheckedChange = { if (!compatibilityRequired) SpUtils.putBoolean(Keys.BLE_ADVERTISER_COMPATIBILITY, it) })
            val peripheralDebug = preference(Keys.BLE_PERIPHERAL_DEBUG) {
                SpUtils.getBoolean(Keys.BLE_PERIPHERAL_DEBUG, false)
            }
            SwitchPreference(title = stringResource(R.string.ble_peripheral_debug),
                summary = stringResource(R.string.ble_peripheral_debug_desc), checked = peripheralDebug,
                onCheckedChange = { SpUtils.putBoolean(Keys.BLE_PERIPHERAL_DEBUG, it) })
        }
        SmallTitle(stringResource(R.string.hook_rules_title))
        Card {
            val source = revision?.source?.let {
                stringResource(when (it) {
                    "custom" -> R.string.rule_source_custom
                    "previous" -> R.string.rule_source_previous
                    else -> R.string.rule_source_bundled
                })
            }
            ArrowPreference(title = stringResource(R.string.hook_rules_source), enabled = !busy,
                summary = revision?.let { stringResource(R.string.hook_rules_source_summary, it.rules.revision, source ?: "") }
                    ?: stringResource(R.string.hook_rules_status_unknown), onClick = { dialog = "rules" })
            ArrowPreference(title = stringResource(R.string.hook_rules_current), enabled = revision != null,
                summary = stringResource(R.string.hook_rules_current_desc), onClick = { dialog = "json" })
            ArrowPreference(title = stringResource(R.string.hook_rules_rollback), enabled = !busy,
                summary = stringResource(R.string.hook_rules_rollback_desc), onClick = { model.update() })
        }
        SmallTitle(stringResource(R.string.nav_about))
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AndroidView(
                    factory = { ctx -> android.widget.ImageView(ctx).apply { setImageResource(R.mipmap.ic_launcher) } },
                    modifier = Modifier.size(52.dp))
                Column(Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.app_name), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.about_version_rules, BuildConfig.VERSION_NAME, revision?.rules?.revision ?: 0),
                        fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Text(stringResource(R.string.app_desc), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            ArrowPreference(title = stringResource(R.string.check_update_title), enabled = !busy,
                summary = stringResource(if (busy) R.string.update_checking else R.string.check_update_desc),
                onClick = model::checkForUpdates)
            ArrowPreference(title = stringResource(R.string.current_changelog),
                onClick = { dialog = "changelog" })
            ArrowPreference(title = stringResource(R.string.project_home), summary = stringResource(R.string.source_code),
                onClick = { CustomTabsHelper.launchUrlOrCopy(activity, "https://github.com/ShadowSmallBaby/bluetooth") })
        }
    }
    if (dialog == "rules") ChoiceDialog(stringResource(R.string.hook_rules_source), { dialog = "" }, showClose = false) {
        PreferenceField(Keys.RULE_URL, R.string.rules_url)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(stringResource(R.string.cancel), onClick = { dialog = "" }, modifier = Modifier.weight(1f))
            TextButton(stringResource(R.string.save), onClick = {
                val url = SpUtils.getString(Keys.RULE_URL).trim()
                if (url.isNotEmpty()) { dialog = ""; model.update(url) }
            }, modifier = Modifier.weight(1f))
        }
    }
    if (dialog == "json") revision?.let { sel ->
        ChoiceDialog(stringResource(R.string.hook_rules_current), { dialog = "" }) {
            val pretty = remember(sel.json) {
                runCatching { JSONObject(sel.json).toString(2) }.getOrDefault(sel.json)
            }
            Text(pretty)
        }
    }
    if (dialog == "changelog") ChoiceDialog(stringResource(R.string.current_changelog), { dialog = "" }) {
        val changes = remember {
            activity.assets.open("version.json").bufferedReader().use {
                JSONObject(it.readText()).getJSONArray("changes").let { entries ->
                    List(entries.length()) { index -> entries.getString(index) }
                }
            }
        }
        Text(BuildConfig.VERSION_NAME)
        changes.forEach { Text("• $it") }
    }
    model.feedback?.let { feedback ->
        ChoiceDialog(stringResource(feedback.title), model::dismissFeedback) {
            Text(stringResource(feedback.message, *feedback.arguments.toTypedArray()))
        }
    }
    model.available?.let { result ->
        ChoiceDialog(stringResource(R.string.check_update_title), model::dismissUpdate, showClose = false) {
            if (result.appUpdate) {
                Text(stringResource(R.string.update_app_available, result.latestVersionName))
                result.changes.forEach { Text("• $it") }
            }
            if (result.rulesUpdate) {
                // 版本与规则都有更新时，用分割线隔开更新日志与规则提示
                if (result.appUpdate) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.update_rules_available, result.latestRevision))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 左键：版本与规则都有更新时为「更新规则」，其余情况为「关闭」
                if (result.appUpdate && result.rulesUpdate)
                    TextButton(stringResource(R.string.update_rules_now), onClick = { model.dismissUpdate(); model.update(UpdateChecker.RULES_URL) }, modifier = Modifier.weight(1f))
                else
                    TextButton(stringResource(R.string.close), onClick = model::dismissUpdate, modifier = Modifier.weight(1f))
                // 右键：应用有更新时为「新版本」，仅规则有更新时为「更新规则」
                if (result.appUpdate)
                    TextButton(stringResource(R.string.update_new_version), onClick = { CustomTabsHelper.launchUrlOrCopy(activity, UpdateChecker.apkUrl) }, modifier = Modifier.weight(1f))
                else if (result.rulesUpdate)
                    TextButton(stringResource(R.string.update_rules_now), onClick = { model.dismissUpdate(); model.update(UpdateChecker.RULES_URL) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

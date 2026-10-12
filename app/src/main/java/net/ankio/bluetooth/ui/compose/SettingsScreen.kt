package net.ankio.bluetooth.ui.compose

import android.content.ComponentName
import android.content.pm.PackageManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankio.bluetooth.BuildConfig
import net.ankio.bluetooth.R
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.hook.HookBootstrap
import net.ankio.bluetooth.rules.*
import net.ankio.bluetooth.ui.MainActivity
import net.ankio.bluetooth.update.UpdateChecker
import net.ankio.bluetooth.utils.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.json.JSONObject
import java.util.Locale

@Composable
fun SettingsScreen(activity: MainActivity) {
    var dialog by rememberSaveable { mutableStateOf("") }
    val repository = remember { RuleRepository(HookBootstrap.bundledRules(activity.javaClass.classLoader!!), SharedPreferencesRuleStore()) }
    val updater = remember { RuleUpdater(repository) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(runCatching { repository.current() }.getOrNull()) }
    var available by remember { mutableStateOf<UpdateChecker.Result.Ok?>(null) }
    fun update(url: String? = null) {
        if (busy) return
        if (url == null && !repository.hasPrevious()) {
            feedback = activity.getString(R.string.hook_rules_no_previous); return
        }
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { if (url == null) updater.rollback() else updater.updateFrom(url) }
                feedback = when (result) {
                    is RuleUpdater.Result.Updated -> activity.getString(R.string.hook_rules_updated, result.revision)
                    is RuleUpdater.Result.Unchanged -> activity.getString(R.string.hook_rules_unchanged)
                    is RuleUpdater.Result.Failed -> activity.getString(R.string.hook_rules_failed, result.reason)
                }
                revision = runCatching { repository.current() }.getOrNull()
                if (url != null && result !is RuleUpdater.Result.Failed) SpUtils.putString(Keys.RULE_URL, "")
            } finally { busy = false }
        }
    }
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
                summary = stringResource(R.string.hook_rules_rollback_desc), onClick = { update() })
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
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            when (val result = withContext(Dispatchers.IO) { UpdateChecker.check(revision?.rules?.revision ?: 0) }) {
                                is UpdateChecker.Result.Failed -> feedback = activity.getString(R.string.update_failed, result.reason)
                                is UpdateChecker.Result.Ok -> if (result.appUpdate || result.rulesUpdate) available = result
                                    else feedback = activity.getString(R.string.update_none)
                            }
                        } finally { busy = false }
                    }
                })
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
                if (url.isNotEmpty()) { dialog = ""; update(url) }
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
    feedback?.let { ChoiceDialog(stringResource(R.string.hook_rules_title), { feedback = null }) { Text(it) } }
    available?.let { result ->
        ChoiceDialog(stringResource(R.string.update_found), { available = null }) {
            if (result.appUpdate) {
                Text(stringResource(R.string.update_app_available, result.latestVersionName))
                TextButton(stringResource(R.string.update_download_apk), onClick = { CustomTabsHelper.launchUrlOrCopy(activity, UpdateChecker.apkUrl) })
            }
            if (result.rulesUpdate) {
                Text(stringResource(R.string.update_rules_available, result.latestRevision))
                TextButton(stringResource(R.string.update_rules_now), onClick = { available = null; update(UpdateChecker.RULES_URL) })
            }
        }
    }
}

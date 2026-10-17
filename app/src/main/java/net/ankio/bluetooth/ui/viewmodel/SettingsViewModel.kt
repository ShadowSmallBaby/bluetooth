package net.ankio.bluetooth.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankio.bluetooth.R
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.hook.HookBootstrap
import net.ankio.bluetooth.rules.*
import net.ankio.bluetooth.update.UpdateChecker
import net.ankio.bluetooth.utils.SpUtils

data class SettingsFeedback(@StringRes val message: Int, val arguments: List<Any> = emptyList(),
    @StringRes val title: Int = R.string.hook_rules_title)

/** Activity-scoped: tab changes and recreation do not drop a running update or its result. */
class SettingsViewModel : ViewModel() {
    private val repository = RuleRepository(
        HookBootstrap.bundledRules(javaClass.classLoader!!), SharedPreferencesRuleStore())
    private val updater = RuleUpdater(repository)
    var busy by mutableStateOf(false)
        private set
    var revision by mutableStateOf(runCatching { repository.current() }.getOrNull())
        private set
    var feedback by mutableStateOf<SettingsFeedback?>(null)
        private set
    var available by mutableStateOf<UpdateChecker.Result.Ok?>(null)
        private set

    fun dismissFeedback() { feedback = null }
    fun dismissUpdate() { available = null }

    fun update(url: String? = null) {
        if (busy) return
        if (url == null && !repository.hasPrevious()) {
            feedback = SettingsFeedback(R.string.hook_rules_no_previous)
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (url == null) updater.rollback() else updater.updateFrom(url)
                }
                feedback = when (result) {
                    is RuleUpdater.Result.Updated -> SettingsFeedback(R.string.hook_rules_updated, listOf(result.revision))
                    is RuleUpdater.Result.Unchanged -> SettingsFeedback(R.string.hook_rules_unchanged)
                    is RuleUpdater.Result.Failed -> SettingsFeedback(R.string.hook_rules_failed, listOf(result.reason))
                }
                revision = runCatching { repository.current() }.getOrNull()
                if (url != null && result !is RuleUpdater.Result.Failed) SpUtils.putString(Keys.RULE_URL, "")
            } finally { busy = false }
        }
    }

    /**
     * Installs rules picked from a local file. The repository validates the payload before the
     * atomic store write, so a malformed or older file is rejected without touching the active set.
     */
    fun importLocal(uri: Uri, context: Context) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = context.contentResolver.openInputStream(uri)?.use {
                            RuleRepository.readLimited(it)
                        } ?: error(context.getString(R.string.rules_import_failed, uri.toString()))
                        repository.install(json, allowSameRevision = true)
                    }
                }
                feedback = result.fold(
                    onSuccess = { selected ->
                        revision = selected
                        SettingsFeedback(R.string.hook_rules_updated, listOf(selected.rules.revision))
                    },
                    onFailure = { SettingsFeedback(R.string.hook_rules_failed, listOf(it.message ?: it.javaClass.simpleName)) }
                )
            } finally { busy = false }
        }
    }

    fun checkForUpdates() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { UpdateChecker.check(revision?.rules?.revision ?: 0) }) {
                    is UpdateChecker.Result.Failed -> feedback = SettingsFeedback(R.string.update_failed, listOf(result.reason), R.string.check_update_title)
                    is UpdateChecker.Result.Ok -> if (result.appUpdate || result.rulesUpdate) available = result
                        else feedback = SettingsFeedback(R.string.update_none, title = R.string.check_update_title)
                }
            } finally { busy = false }
        }
    }
}

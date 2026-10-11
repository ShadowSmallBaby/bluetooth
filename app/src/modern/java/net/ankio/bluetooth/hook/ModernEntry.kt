package net.ankio.bluetooth.hook
import net.ankio.bluetooth.hook.engine.*

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import net.ankio.bluetooth.BuildConfig
import net.ankio.bluetooth.config.HookSettings
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * LibXposed API 102 entry point. It only adapts the modern framework to [HookRuntime] and reads
 * the configuration snapshot; all simulation logic lives in [SimulationEngine].
 * Legacy frameworks continue using xposed_init with [LegacyBluetoothEntry].
 */
@RequiresApi(Build.VERSION_CODES.O)
class ModernEntry : XposedModule(), HookRuntime {

    private val logger = HookLog { message, error -> log(Log.WARN, TAG, message, error) }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == BuildConfig.APPLICATION_ID) {
            reportModuleActivation(param.classLoader)
            return
        }
        val config = HookConfig({ readSnapshot() }, logger)
        val settings = config.read()
        if (!settings.enabled) return
        val loader = param.classLoader
        val bundled = try {
            HookBootstrap.bundledRules(loader)
        } catch (error: Exception) {
            log(Log.ERROR, TAG, "Cannot load bundled hook rules", error)
            return
        }
        val rules = HookBootstrap.selectRules(settings, bundled)
        val (profile, plan) = HookBootstrap.planFor(param.packageName, rules, loader) ?: return
        SimulationEngine(config, profile, plan, this, logger) { target ->
            HandlerScheduler.forTarget(target, profile)
        }.install()
        log(Log.INFO, TAG, "Modern simulation installed for profile ${profile.id}")
    }

    /**
     * The module app publishes its config snapshot into the shared LibXposed remote preferences;
     * the hooked process reads it back here. Plain app SharedPreferences are NOT bridged across
     * processes, so the snapshot must travel through the framework's remote-preference store.
     */
    private val remotePrefs by lazy { getRemotePreferences(HookSettings.GROUP) }

    private fun readSnapshot(): String? = remotePrefs.getString(HookSettings.SNAPSHOT, null)

    override fun after(member: Member, callback: (Any) -> Unit) {
        hook(member as Executable).intercept { chain ->
            val result = chain.proceed()
            chain.thisObject?.let(callback)
            result
        }
    }

    override fun before(method: Method, callback: (Any) -> Unit) {
        hook(method).intercept { chain ->
            chain.thisObject?.let(callback)
            chain.proceed()
        }
    }

    private fun reportModuleActivation(loader: ClassLoader) {
        try {
            ModuleActivationReporter.report(loader, apiVersion, true)
            log(Log.INFO, TAG, "Modern module activation reported")
        } catch (error: ReflectiveOperationException) {
            log(Log.ERROR, TAG, "Cannot report module activation", error)
        }
    }

    private companion object {
        const val TAG = "BluetoothDebug"
    }
}

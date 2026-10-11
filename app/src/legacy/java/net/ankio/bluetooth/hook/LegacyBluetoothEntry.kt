package net.ankio.bluetooth.hook
import net.ankio.bluetooth.hook.engine.*

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import net.ankio.bluetooth.BuildConfig
import net.ankio.bluetooth.config.HookSettings
import net.ankio.bluetooth.utils.HookLogManager
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * Legacy Xposed (API 82+) entry point. It only adapts the legacy framework to [HookRuntime] and
 * reads the configuration from the module app's shared preferences; the shared [SimulationEngine]
 * performs class resolution, signature matching and injection exactly as the modern entry does.
 */
class LegacyBluetoothEntry : IXposedHookLoadPackage, HookRuntime {
    private val tag = "AnkioのBluetooth"
    private val logger = HookLog { message, error ->
        if (error != null) HookLogManager.e(tag, message, error) else HookLogManager.d(tag, message)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return
        HookLogManager.d(tag, "lpparam.packageName: ${lpparam.packageName}")
        val config = HookConfig({ readSnapshot() }, logger)
        val settings = config.read()
        if (!settings.enabled) return
        val loader = lpparam.classLoader
        val bundled = try {
            HookBootstrap.bundledRules(loader)
        } catch (error: Throwable) {
            HookLogManager.e(tag, "加载内置 Hook 规则失败", error)
            return
        }
        val rules = HookBootstrap.selectRules(settings, bundled)
        val (profile, plan) = HookBootstrap.planFor(lpparam.packageName, rules, loader) ?: return
        try {
            SimulationEngine(config, profile, plan, this, logger) { target ->
                HandlerScheduler.forTarget(target, profile)
            }.install()
            HookLogManager.d(tag, "蓝牙模拟已安装: ${profile.id}")
        } catch (error: Throwable) {
            HookLogManager.e(tag, "Hook 初始化失败", error)
        }
    }

    /** Reads the module app's world-readable "config" preferences across the process boundary. */
    private val sharedPrefs by lazy { XSharedPreferences(BuildConfig.APPLICATION_ID, "config") }

    private fun readSnapshot(): String {
        sharedPrefs.reload()
        return HookSettings.fromPreferences(sharedPrefs.all).encode()
    }

    override fun after(member: Member, callback: (Any) -> Unit) {
        XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.thisObject?.let(callback)
            }
        })
    }

    override fun before(method: Method, callback: (Any) -> Unit) {
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.thisObject?.let(callback)
            }
        })
    }
}

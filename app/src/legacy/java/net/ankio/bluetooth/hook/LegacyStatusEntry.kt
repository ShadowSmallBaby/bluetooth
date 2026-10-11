package net.ankio.bluetooth.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import net.ankio.bluetooth.BuildConfig

/** Reports legacy module activation in the app process. */
class LegacyStatusEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam?.packageName != BuildConfig.APPLICATION_ID) return
        try {
            ModuleActivationReporter.report(lpparam.classLoader, XposedBridge.getXposedVersion(), false)
            XposedBridge.log("BluetoothDebug: Legacy module activation reported")
        } catch (error: ReflectiveOperationException) {
            XposedBridge.log(error)
        }
    }
}



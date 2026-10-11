package net.ankio.bluetooth.hook

/** Updates the app's status instance instead of the module class loader's copy. */
internal object ModuleActivationReporter {
    fun report(loader: ClassLoader, apiVersion: Int, modern: Boolean) {
        loader.loadClass("net.ankio.bluetooth.utils.ModuleStatus")
            .getDeclaredMethod("reportActivation", Integer.TYPE, java.lang.Boolean.TYPE)
            .invoke(null, apiVersion, modern)
    }
}

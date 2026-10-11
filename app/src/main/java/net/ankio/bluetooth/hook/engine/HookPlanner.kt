package net.ankio.bluetooth.hook.engine
import net.ankio.bluetooth.hook.model.*

import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class HookPlan(val profile: HookProfile, val type: Class<*>, val start: Method?, val stops: List<Method>)

object HookPlanner {
    fun plan(profile: HookProfile, loader: ClassLoader): HookPlan? {
        for (name in profile.classes) {
            val type = try { Class.forName(name, false, loader) } catch (_: ClassNotFoundException) { continue }
            if (Modifier.isAbstract(type.modifiers) || type.isInterface) continue
            val start = profile.start.firstNotNullOfOrNull { ScanTargetResolver.findMethod(type, it) }
            if (start == null && !profile.constructorFallback) continue
            val stops = profile.stop.mapNotNull { ScanTargetResolver.findMethod(type, it) }.distinct()
            // A task without a matching teardown hook would survive service shutdown.
            if (stops.isEmpty()) continue
            return HookPlan(profile, type, start, stops)
        }
        return null
    }
}

package net.ankio.bluetooth.hook.engine
import net.ankio.bluetooth.hook.model.*

import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class HookPlan(val profile: HookProfile, val type: Class<*>, val start: Method?, val stops: List<Method>)

/**
 * Picks the class to hook. Class order is a priority hint, not a correctness requirement: a class
 * whose target paths cannot statically reach a scan callback loses to one that can, so a ROM that
 * moved the callback to another class still gets a working plan without editing the rules.
 */
object HookPlanner {
    fun plan(profile: HookProfile, loader: ClassLoader): HookPlan? {
        val candidates = profile.classes.mapNotNull { candidate(profile, loader, it) }
        // A class whose target paths all dead-end would hook a lifecycle entry and then inject
        // nothing, so prefer one that statically reaches a callback. When none does (for example a
        // getter declared to return an interface), keep the previous first-match behaviour.
        return candidates.firstOrNull { reachesAnyTarget(it.type, profile) } ?: candidates.firstOrNull()
    }

    private fun candidate(profile: HookProfile, loader: ClassLoader, name: String): HookPlan? {
        val type = try { Class.forName(name, false, loader) } catch (_: ClassNotFoundException) { return null }
        if (Modifier.isAbstract(type.modifiers) || type.isInterface) return null
        val start = profile.start.firstNotNullOfOrNull { ScanTargetResolver.findMethod(type, it) }
        if (start == null && !profile.constructorFallback) return null
        val stops = profile.stop.mapNotNull { ScanTargetResolver.findMethod(type, it) }.distinct()
        // A task without a matching teardown hook would survive service shutdown.
        if (stops.isEmpty()) return null
        return HookPlan(profile, type, start, stops)
    }

    /**
     * True when at least one configured path, followed through declared types, ends at a class
     * that declares one of the profile's callbacks. The empty path is the class itself.
     */
    private fun reachesAnyTarget(type: Class<*>, profile: HookProfile): Boolean =
        profile.targetPaths.any { path -> declaresCallback(receiverOf(type, path), profile) }

    /** Statically follows [path] using each step's declared return or field type. */
    private fun receiverOf(type: Class<*>, path: List<MemberStep>): Class<*>? =
        path.fold(type as Class<*>?) { current, step ->
            current ?: return null
            when (step.kind) {
                MemberKind.METHOD -> ScanTargetResolver.findMethod(current, MethodSpec(step.name, emptyList()))?.returnType
                MemberKind.FIELD -> ScanTargetResolver.field(current, step.name)?.type
            }
        }

    private fun declaresCallback(type: Class<*>?, profile: HookProfile): Boolean =
        type != null && profile.callbacks.any { ScanTargetResolver.findMethod(type, it.method) != null }
}

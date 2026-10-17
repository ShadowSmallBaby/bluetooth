package net.ankio.bluetooth.hook.engine
import net.ankio.bluetooth.hook.model.*

import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class ScanTarget(val instance: Any, val method: Method, val arguments: List<ScanArgument>) {
    fun inject(payload: ScanPayload) {
        method.isAccessible = true
        method.invoke(instance, *arguments.map(payload::argument).toTypedArray())
    }
}

/** Reflection is confined to the target application's loader and explicitly configured members. */
object ScanTargetResolver {
    fun methods(type: Class<*>): List<Method> = hierarchy(type)
        .flatMap { it.declaredMethods.asSequence() }
        .filterNot { it.isBridge || it.isSynthetic || Modifier.isStatic(it.modifiers) }
        .distinctBy { it.name to it.parameterTypes.toList() }
        .toList()

    fun findMethod(type: Class<*>, spec: MethodSpec): Method? = methods(type).firstOrNull {
        it.name == spec.name && it.parameterTypes.map(::typeName) == spec.parameters &&
            (spec.returns == null || typeName(it.returnType) == spec.returns)
    }

    /** First non-static field named [name] in [type] or any superclass. */
    fun field(type: Class<*>, name: String): java.lang.reflect.Field? = hierarchy(type)
        .flatMap { it.declaredFields.asSequence() }
        .firstOrNull { it.name == name && !Modifier.isStatic(it.modifiers) }

    fun resolve(entry: Any, profile: HookProfile): ScanTarget? {
        for (path in profile.targetPaths) {
            val target = followPath(entry, path) ?: continue
            for (callback in profile.callbacks) {
                val method = findMethod(target.javaClass, callback.method) ?: continue
                return ScanTarget(target, method, callback.arguments)
            }
        }
        return null
    }

    fun followPath(entry: Any, path: List<MemberStep>): Any? {
        var target: Any = entry
        for (step in path) {
            target = try {
                when (step.kind) {
                    MemberKind.METHOD -> findMethod(target.javaClass, MethodSpec(step.name, emptyList()))
                        ?.apply { isAccessible = true }?.invoke(target)
                    MemberKind.FIELD -> field(target.javaClass, step.name)
                        ?.apply { isAccessible = true }?.get(target)
                }
            } catch (_: ReflectiveOperationException) {
                null
            } ?: return null
        }
        return target
    }

    private fun hierarchy(type: Class<*>): Sequence<Class<*>> =
        generateSequence(type) { it.superclass }.takeWhile { it != Any::class.java }

    private fun typeName(type: Class<*>): String =
        if (type.isArray) "${typeName(type.componentType!!)}[]" else type.name
}


package net.ankio.bluetooth.hook.engine

import java.lang.reflect.Member
import java.lang.reflect.Method

/** The only framework-specific operations needed by the simulation engine. */
interface HookRuntime {
    fun after(member: Member, callback: (Any) -> Unit)
    fun before(method: Method, callback: (Any) -> Unit)
}

fun interface HookLog {
    fun write(message: String, error: Throwable?)
}

package net.ankio.bluetooth.hook.engine
import net.ankio.bluetooth.hook.model.*

import android.os.Handler
import android.os.Looper

/**
 * Posts simulation ticks on an Android [Handler] so the engine itself stays Android-agnostic.
 */
class HandlerScheduler(private val handler: Handler) : TickScheduler {
    override fun post(task: Runnable, delayMs: Long) {
        handler.postDelayed(task, delayMs)
    }

    override fun remove(task: Runnable) {
        handler.removeCallbacks(task)
    }

    companion object {
        /**
         * Prefers a handler reachable from the scan target through the rule's handler paths,
         * falling back to the main looper. Running on the target's own handler keeps injection
         * on the thread the framework expects.
         */
        fun forTarget(target: Any, profile: HookProfile): HandlerScheduler {
            val handler = profile.handlerPaths.firstNotNullOfOrNull { path ->
                ScanTargetResolver.followPath(target, path) as? Handler
            } ?: Handler(Looper.getMainLooper())
            return HandlerScheduler(handler)
        }
    }
}

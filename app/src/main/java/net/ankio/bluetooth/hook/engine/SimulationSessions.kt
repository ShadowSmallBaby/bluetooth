package net.ankio.bluetooth.hook.engine

import java.lang.ref.WeakReference

interface TickScheduler {
    fun post(task: Runnable, delayMs: Long)
    fun remove(task: Runnable)
}

/** Cancellation also guards an already dequeued callback from rescheduling itself. */
class PeriodicTask(
    private val scheduler: TickScheduler,
    private val intervalMs: Long,
    private val tick: () -> Boolean
) : Runnable {
    @Volatile
    var cancelled: Boolean = false
        private set

    @Synchronized
    fun start() {
        if (!cancelled) scheduler.post(this, intervalMs)
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        scheduler.remove(this)
    }

    override fun run() {
        if (cancelled) return
        val keepRunning = tick()
        synchronized(this) {
            if (!keepRunning) cancel()
            else if (!cancelled) scheduler.post(this, intervalMs)
        }
    }
}

/** Entry aliases share one task per target identity; neither entries nor targets are kept alive. */
class SimulationSessions {
    private data class Session(
        val target: WeakReference<Any>,
        val owners: MutableList<WeakReference<Any>>,
        val task: PeriodicTask
    )

    private val sessions = mutableListOf<Session>()

    @Synchronized
    fun attach(entry: Any, target: Any, create: (WeakReference<Any>) -> PeriodicTask): Boolean {
        sessions.removeAll {
            if (it.target.get() == null || it.task.cancelled) { it.task.cancel(); true } else false
        }
        val existing = sessions.find { it.target.get() === target }
        if (existing != null) {
            if (existing.owners.none { it.get() === entry }) existing.owners.add(WeakReference(entry))
            return false
        }
        val reference = WeakReference(target)
        val task = create(reference)
        sessions.add(Session(reference, mutableListOf(WeakReference(entry)), task))
        task.start()
        return true
    }

    @Synchronized
    fun detach(entry: Any) {
        sessions.removeAll {
            if (it.target.get() === entry || it.owners.any { owner -> owner.get() === entry }) {
                it.task.cancel()
                true
            } else false
        }
    }
}

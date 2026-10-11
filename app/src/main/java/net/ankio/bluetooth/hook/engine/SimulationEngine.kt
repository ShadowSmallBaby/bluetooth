package net.ankio.bluetooth.hook.engine
import net.ankio.bluetooth.hook.model.*

import java.lang.reflect.Member

/**
 * Framework-neutral simulation driver.
 *
 * It ties the configuration snapshot, a planned hook target, scan injection and task
 * lifecycle together. A framework only supplies three adapters: how to hook ([HookRuntime]),
 * how to schedule periodic ticks ([TickScheduler]) and how to log ([HookLog]). Everything
 * else — class resolution, signature matching and argument mapping — is driven by [profile].
 */
class SimulationEngine(
    private val config: HookConfig,
    private val profile: HookProfile,
    private val plan: HookPlan,
    private val runtime: HookRuntime,
    private val log: HookLog,
    private val scheduler: (target: Any) -> TickScheduler
) {
    private val sessions = SimulationSessions()

    /** Remembers the last payload error so an unchanged misconfiguration is logged only once. */
    @Volatile
    private var lastPayloadError: String? = null

    /** Hooks the lifecycle: each start opens a session, each stop closes the owner's session. */
    fun install() {
        val entries: List<Member> = plan.start?.let { listOf<Member>(it) }
            ?: plan.type.declaredConstructors.toList()
        entries.forEach { member -> runtime.after(member) { entry -> onStart(entry) } }
        plan.stops.forEach { method -> runtime.before(method) { entry -> sessions.detach(entry) } }
    }

    private fun onStart(entry: Any) {
        val target = ScanTargetResolver.resolve(entry, profile)
        if (target == null) {
            log.write("No scan target resolved for ${entry.javaClass.name}", null)
            return
        }
        sessions.attach(entry, target.instance) {
            PeriodicTask(scheduler(target.instance), profile.intervalMs) { emit(target) }
        }
    }

    /**
     * Runs every tick for the life of the scan service. Injection is gated by the live
     * configuration, so toggling the switch or correcting the payload takes effect without a
     * restart. Only a reflective failure — a wrong resolved method — stops the task.
     */
    private fun emit(target: ScanTarget): Boolean {
        val settings = config.read()
        if (!settings.enabled) return true
        val payload = try {
            ScanPayload.parse(settings.mac, settings.rssi, settings.data)
        } catch (error: Exception) {
            if (lastPayloadError != error.message) {
                log.write("Invalid scan payload; waiting for valid configuration", error)
                lastPayloadError = error.message
            }
            return true
        }
        lastPayloadError = null
        return try {
            target.inject(payload)
            true
        } catch (error: ReflectiveOperationException) {
            log.write("Scan injection failed; stopping simulation", error)
            false
        }
    }
}

package net.ankio.bluetooth.service

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ankio.bluetooth.bluetooth.BluetoothData

/** Owns exactly one scan/upload cycle. Cancellation propagates into either operation. */
class SenderSyncLoop(private val scope: CoroutineScope, private val intervalMs: Long) {
    private var job: Job? = null
    private val cycle = Mutex()

    fun start(scan: suspend () -> BluetoothData?, upload: suspend (BluetoothData) -> Unit,
              onError: (Exception) -> Unit) {
        job?.cancel()
        job = scope.launch {
            // All restarts wait for the active operation, even if an intermediate waiter is cancelled.
            cycle.withLock {
                while (isActive) {
                    try {
                        val data = scan()
                        ensureActive()
                        if (data != null) upload(data)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        ensureActive()
                        onError(error)
                    }
                    delay(intervalMs)
                }
            }
        }
    }

    fun stop() { job?.cancel() }
}

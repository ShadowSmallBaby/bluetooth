package net.ankio.bluetooth

import kotlinx.coroutines.*
import net.ankio.bluetooth.bluetooth.BluetoothData
import net.ankio.bluetooth.service.SenderSyncLoop
import org.junit.Assert.*
import org.junit.Test

class SenderSyncLoopTest {
    private val payload = BluetoothData("020106", "AA:BB:CC:DD:EE:FF", "-50")

    @Test fun stoppingWhileScanningCancelsScanWithoutUploading() = runBlocking {
        withTimeout(3000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val started = CompletableDeferred<Unit>()
                val released = CompletableDeferred<Unit>()
                var uploads = 0
                var failures = 0
                val loop = SenderSyncLoop(scope, 10_000)
                loop.start(scan = {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { released.complete(Unit) }
                }, upload = { uploads++ }, onError = { failures++ })
                started.await()
                loop.stop()
                released.await()
                yield()
                assertEquals(0, uploads)
                assertEquals(0, failures)
            } finally { scope.cancel() }
        }
    }

    @Test fun reconnectWaitsForCancelledUploadToUnwind() = runBlocking {
        withTimeout(3000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val releaseUpload = CompletableDeferred<Unit>()
            try {
                val uploading = CompletableDeferred<Unit>()
                val cleaningUp = CompletableDeferred<Unit>()
                val newScan = CompletableDeferred<Unit>()
                val loop = SenderSyncLoop(scope, 10_000)
                loop.start(scan = { payload }, upload = {
                    uploading.complete(Unit)
                    try { awaitCancellation() } finally {
                        cleaningUp.complete(Unit)
                        withContext(NonCancellable) { releaseUpload.await() }
                    }
                }, onError = { throw AssertionError(it) })
                uploading.await()
                loop.start(scan = { newScan.complete(Unit); awaitCancellation() },
                    upload = { fail("Cancelled upload must not restart") }, onError = { throw AssertionError(it) })
                cleaningUp.await()
                yield()
                assertFalse(newScan.isCompleted)
                releaseUpload.complete(Unit)
                newScan.await()
                loop.stop()
            } finally { releaseUpload.complete(Unit); scope.cancel() }
        }
    }

    @Test fun uploadFailureIsReportedAndNextCycleCanRun() = runBlocking {
        withTimeout(3000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val secondScan = CompletableDeferred<Unit>()
                var scans = 0
                var failures = 0
                val loop = SenderSyncLoop(scope, 1)
                loop.start(scan = {
                    if (++scans == 2) { secondScan.complete(Unit); awaitCancellation() }
                    payload
                }, upload = { throw java.io.IOException("offline") }, onError = { failures++ })
                secondScan.await()
                assertEquals(1, failures)
                loop.stop()
            } finally { scope.cancel() }
        }
    }

    @Test fun rapidReconnectsWaitForTheOldestUploadAndOnlyStartLatestScan() = runBlocking {
        withTimeout(3000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val releaseUpload = CompletableDeferred<Unit>()
            try {
                val uploading = CompletableDeferred<Unit>()
                val cleaningUp = CompletableDeferred<Unit>()
                val latestScan = CompletableDeferred<Unit>()
                var supersededScans = 0
                val loop = SenderSyncLoop(scope, 10_000)
                loop.start(scan = { payload }, upload = {
                    uploading.complete(Unit)
                    try { awaitCancellation() } finally {
                        cleaningUp.complete(Unit)
                        withContext(NonCancellable) { releaseUpload.await() }
                    }
                }, onError = { throw AssertionError(it) })
                uploading.await()
                loop.start(scan = { supersededScans++; awaitCancellation() },
                    upload = {}, onError = { throw AssertionError(it) })
                cleaningUp.await()
                yield()
                loop.start(scan = { latestScan.complete(Unit); awaitCancellation() },
                    upload = {}, onError = { throw AssertionError(it) })
                repeat(5) { yield() }
                assertFalse("Latest scan must wait for the original upload", latestScan.isCompleted)
                assertEquals(0, supersededScans)
                releaseUpload.complete(Unit)
                latestScan.await()
                loop.stop()
            } finally { releaseUpload.complete(Unit); scope.cancel() }
        }
    }
}

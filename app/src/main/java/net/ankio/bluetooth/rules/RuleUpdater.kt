package net.ankio.bluetooth.rules

import net.ankio.bluetooth.hook.model.HookRules
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Downloads, validates and installs rule updates on top of a [RuleRepository]. Validation and the
 * atomic store write live in the repository; this class only adds the HTTPS transport and maps
 * outcomes to a UI-friendly [Result]. All methods block and must run off the main thread.
 */
class RuleUpdater(private val repository: RuleRepository) {

    sealed interface Result {
        data class Updated(val revision: Int, val source: String) : Result
        data class Unchanged(val revision: Int) : Result
        data class Failed(val reason: String) : Result
    }

    fun updateFrom(url: String): Result = try {
        val json = download(url)
        val selected = repository.install(json)
        // install() returns the submitted rules only when it actually replaced the active set;
        // an unchanged submission comes back as the current rules carrying their own json.
        if (selected.json == json) {
            Result.Updated(selected.rules.revision, selected.source)
        } else {
            Result.Unchanged(selected.rules.revision)
        }
    } catch (error: Exception) {
        Result.Failed(error.message ?: error.javaClass.simpleName)
    }

    fun rollback(): Result = try {
        val selected = repository.rollback()
        Result.Updated(selected.rules.revision, selected.source)
    } catch (error: Exception) {
        Result.Failed(error.message ?: "无法回退规则")
    }

    companion object {
        private const val TIMEOUT_MS = 15_000

        /** HTTPS only: a plaintext URL or a cross-protocol redirect is rejected before any read. */
        fun download(rawUrl: String): String {
            val url = URL(rawUrl.trim())
            require(url.protocol.equals("https", ignoreCase = true)) { "仅支持 HTTPS 规则地址" }
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                require(code == HttpURLConnection.HTTP_OK) { "服务器返回状态码 $code" }
                require(connection.contentLength <= HookRules.MAX_BYTES) { "规则文件过大" }
                return connection.inputStream.use { RuleRepository.readLimited(it) }
            } finally {
                connection.disconnect()
            }
        }
    }
}

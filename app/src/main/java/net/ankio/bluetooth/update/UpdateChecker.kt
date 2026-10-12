package net.ankio.bluetooth.update

import com.google.gson.JsonParser
import net.ankio.bluetooth.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Checks GitHub's `releases/latest/download/update-manual.json` for a newer APK (by versionCode)
 * and/or newer hook rules (by revision). All network methods block and must run off the main thread.
 */
object UpdateChecker {
    private const val BASE = "https://github.com/ShadowSmallBaby/bluetooth/releases/latest/download"
    const val MANIFEST_URL = "$BASE/update-manual.json"
    const val RULES_URL = "$BASE/hook_rules.json"

    /** APK for the running flavor (modern/legacy), so the user keeps the matching framework path. */
    val apkUrl: String get() = "$BASE/bluetooth-${BuildConfig.FLAVOR}.apk"

    data class Manifest(val appVersionCode: Int, val appVersionName: String, val changes: List<String>, val rulesRevision: Int)

    sealed interface Result {
        data class Ok(
            val appUpdate: Boolean,
            val latestVersionName: String,
            val changes: List<String>,
            val rulesUpdate: Boolean,
            val latestRevision: Int
        ) : Result

        data class Failed(val reason: String) : Result
    }

    private fun fetchManifest(): Manifest {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "服务器返回状态码 ${connection.responseCode}"
            }
            val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val root = JsonParser.parseString(text).asJsonObject
            val app = root.getAsJsonObject("app")
            val rules = root.getAsJsonObject("rules")
            val changes = app.getAsJsonArray("changes")?.map { it.asString } ?: emptyList()
            return Manifest(app.get("versionCode").asInt, app.get("versionName").asString, changes, rules.get("revision").asInt)
        } finally {
            connection.disconnect()
        }
    }

    fun check(currentRulesRevision: Int): Result = try {
        val m = fetchManifest()
        Result.Ok(
            appUpdate = m.appVersionCode > BuildConfig.VERSION_CODE,
            latestVersionName = m.appVersionName,
            changes = m.changes,
            rulesUpdate = m.rulesRevision > currentRulesRevision,
            latestRevision = m.rulesRevision
        )
    } catch (error: Exception) {
        Result.Failed(error.message ?: error.javaClass.simpleName)
    }
}

package net.ankio.bluetooth.hook.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Versioned, data-only compatibility rules. No framework or Android dependencies. */
data class HookRules(val revision: Int, val profiles: List<HookProfile>) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_BYTES = 128 * 1024

        fun parse(json: String): HookRules {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Rule file exceeds 128 KiB" }
            val root = JsonParser.parseString(json).objectValue()
            root.checkKeys("schemaVersion", "revision", "profiles")
            require(root.int("schemaVersion") == SCHEMA_VERSION) { "Unsupported rule schemaVersion" }
            val revision = root.int("revision")
            require(revision > 0) { "revision must be positive" }
            val profiles = root.array("profiles", 1..32).map { parseProfile(it.objectValue()) }
            require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate profile id" }
            return HookRules(revision, profiles)
        }

        private fun parseProfile(json: JsonObject): HookProfile {
            json.checkKeys("id", "packages", "minSdk", "maxSdk", "manufacturers", "classes",
                "start", "stop", "constructorFallback", "targetPaths", "handlerPaths",
                "allowMainThread", "callbacks", "intervalMs")
            val id = json.string("id")
            require(id.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) { "Invalid profile id" }
            val minSdk = json.int("minSdk", 24)
            val maxSdk = json.int("maxSdk", Int.MAX_VALUE)
            require(minSdk >= 24 && maxSdk >= minSdk) { "$id: invalid SDK range" }
            val interval = json.int("intervalMs", 500)
            require(interval in 100..60_000) { "$id: intervalMs must be 100..60000" }
            val start = json.array("start", 0..32).map { parseMethod(it.objectValue()) }
            val stop = json.array("stop", 1..32).map { parseMethod(it.objectValue()) }
            val constructors = json.boolean("constructorFallback", false)
            require(start.isNotEmpty() || constructors) { "$id: no lifecycle entry" }
            return HookProfile(
                id, json.names("packages"), minSdk, maxSdk,
                json.optionalStrings("manufacturers"), json.names("classes"), start, stop, constructors,
                json.array("targetPaths", 1..16).map(::parsePath),
                json.array("handlerPaths", 0..16).map(::parsePath),
                json.boolean("allowMainThread", false),
                json.array("callbacks", 1..32).map { parseCallback(it.objectValue()) }, interval.toLong()
            )
        }

        private fun parsePath(json: JsonElement): List<MemberStep> {
            require(json.isJsonArray && json.asJsonArray.size() <= 8) { "Invalid member path" }
            return json.asJsonArray.map {
                val member = it.objectValue()
                member.checkKeys("kind", "name")
                val kind = when (member.string("kind")) {
                    "method" -> MemberKind.METHOD
                    "field" -> MemberKind.FIELD
                    else -> error("Member kind must be method or field")
                }
                val name = member.string("name")
                require(name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) { "Invalid member name: $name" }
                MemberStep(kind, name)
            }
        }

        private fun parseMethod(json: JsonObject): MethodSpec {
            json.checkKeys("name", "parameters", "returns", "arguments")
            val name = json.string("name")
            require(name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) { "Invalid method name: $name" }
            val parameters = json.array("parameters", 0..32).map { it.stringValue().also(::checkType) }
            val returns = json.get("returns")?.stringValue()?.also { if (it != "void") checkType(it) }
            return MethodSpec(name, parameters, returns)
        }

        private fun parseCallback(json: JsonObject): CallbackRule {
            val method = parseMethod(json)
            require(method.returns == "void") { "Scan callback must return void" }
            val arguments = json.array("arguments", 0..32)
            require(arguments.size == method.parameters.size) { "${method.name}: argument count differs from signature" }
            return CallbackRule(method, arguments.mapIndexed { index, value ->
                val type = method.parameters[index]
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    val field = ScanField.entries.find { it.key == value.asString }
                        ?: error("Unknown scan argument: ${value.asString}")
                    require(field.type == type) { "${field.key} requires ${field.type}, received $type" }
                    ScanArgument.Field(field)
                } else {
                    val constant = value.objectValue()
                    constant.checkKeys("constant")
                    require(constant.has("constant")) { "Missing constant" }
                    ScanArgument.Constant(parseConstant(constant.get("constant"), type))
                }
            })
        }

        private fun parseConstant(value: JsonElement, type: String): Any? {
            if (value.isJsonNull) {
                require(type !in primitiveTypes) { "null is not valid for $type" }
                return null
            }
            require(value.isJsonPrimitive) { "Only scalar constants are supported" }
            val primitive = value.asJsonPrimitive
            return when (type) {
                "boolean" -> { require(primitive.isBoolean); primitive.asBoolean }
                "java.lang.String" -> value.stringValue()
                "int" -> { require(primitive.isNumber); primitive.asBigDecimal.intValueExact() }
                "long" -> { require(primitive.isNumber); primitive.asBigDecimal.longValueExact() }
                "short" -> { require(primitive.isNumber); primitive.asBigDecimal.shortValueExact() }
                "byte" -> { require(primitive.isNumber); primitive.asBigDecimal.byteValueExact() }
                else -> error("Unsupported constant type: $type")
            }
        }

        private val primitiveTypes = setOf("boolean", "byte", "short", "int", "long", "char", "float", "double")
        private fun checkType(type: String) {
            val base = type.removeSuffix("[]")
            require(base in primitiveTypes || base.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*\\.[A-Za-z0-9_$]+"))) {
                "Invalid parameter type: $type"
            }
        }

        private fun JsonElement.objectValue(): JsonObject {
            require(isJsonObject) { "Expected JSON object" }
            return asJsonObject
        }

        private fun JsonElement.stringValue(): String {
            require(isJsonPrimitive && asJsonPrimitive.isString) { "Expected string" }
            return asString.also { require(it.isNotBlank() && it.length <= 256) { "Invalid string" } }
        }

        private fun JsonObject.checkKeys(vararg keys: String) {
            require(keySet().all { it in keys }) { "Unknown fields: ${keySet() - keys.toSet()}" }
        }

        private fun JsonObject.string(key: String) = get(key)?.stringValue() ?: error("Missing $key")
        private fun JsonObject.int(key: String, default: Int? = null): Int {
            val value = get(key) ?: return default ?: error("Missing $key")
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be an integer" }
            return value.asBigDecimal.intValueExact()
        }

        private fun JsonObject.boolean(key: String, default: Boolean): Boolean {
            val value = get(key) ?: return default
            require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$key must be boolean" }
            return value.asBoolean
        }

        private fun JsonObject.array(key: String, size: IntRange): List<JsonElement> {
            val value = get(key) ?: error("Missing $key")
            require(value.isJsonArray && value.asJsonArray.size() in size) { "Invalid $key array size" }
            return value.asJsonArray.toList()
        }

        private fun JsonObject.names(key: String): List<String> = array(key, 1..16).map {
            it.stringValue().also { name ->
                require(name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*\\.[A-Za-z0-9_$]+"))) { "Invalid $key name" }
            }
        }

        private fun JsonObject.optionalStrings(key: String): List<String> =
            if (has(key)) array(key, 0..16).map { it.stringValue() } else emptyList()
    }
}

data class HookProfile(
    val id: String,
    val packages: List<String>,
    val minSdk: Int,
    val maxSdk: Int,
    val manufacturers: List<String>,
    val classes: List<String>,
    val start: List<MethodSpec>,
    val stop: List<MethodSpec>,
    val constructorFallback: Boolean,
    val targetPaths: List<List<MemberStep>>,
    val handlerPaths: List<List<MemberStep>>,
    val allowMainThread: Boolean,
    val callbacks: List<CallbackRule>,
    val intervalMs: Long
) {
    fun appliesTo(packageName: String, sdk: Int, manufacturer: String): Boolean =
        packageName in packages && sdk in minSdk..maxSdk &&
            (manufacturers.isEmpty() || manufacturers.any { it.equals(manufacturer, ignoreCase = true) })
}

data class MethodSpec(val name: String, val parameters: List<String>, val returns: String? = null)
enum class MemberKind { METHOD, FIELD }
data class MemberStep(val kind: MemberKind, val name: String)
data class CallbackRule(val method: MethodSpec, val arguments: List<ScanArgument>)

sealed interface ScanArgument {
    data class Field(val field: ScanField) : ScanArgument
    data class Constant(val value: Any?) : ScanArgument
}

enum class ScanField(val key: String, val type: String) {
    EVENT_TYPE("eventType", "int"), ADDRESS_TYPE("addressType", "int"), MAC("mac", "java.lang.String"),
    PRIMARY_PHY("primaryPhy", "int"), SECONDARY_PHY("secondaryPhy", "int"),
    ADVERTISING_SID("advertisingSid", "int"), TX_POWER("txPower", "int"), RSSI("rssi", "int"),
    PERIODIC_INTERVAL("periodicInterval", "int"), DATA("data", "byte[]"),
    ORIGINAL_ADDRESS("originalAddress", "java.lang.String")
}


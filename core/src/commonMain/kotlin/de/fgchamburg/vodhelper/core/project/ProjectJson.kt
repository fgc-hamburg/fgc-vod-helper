package de.fgchamburg.vodhelper.core.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject

sealed interface ProjectDecodeResult {
    data class Ok(val project: Project) : ProjectDecodeResult
    data object TooNew : ProjectDecodeResult
    data class Unreadable(val reason: String) : ProjectDecodeResult
}

/** The plain-text form of a project. Every field is always written, so the file shows the whole structure. */
object ProjectJson {
    const val FILE_NAME = "project.json"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    fun encode(project: Project): String = json.encodeToString(Project.serializer(), project) + "\n"

    fun decode(text: String): ProjectDecodeResult = try {
        val root: JsonObject = json.parseToJsonElement(text).jsonObject
        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.int
            ?: return ProjectDecodeResult.Unreadable("schemaVersion is missing or not a number")
        if (version > PROJECT_SCHEMA_VERSION) {
            ProjectDecodeResult.TooNew
        } else {
            ProjectDecodeResult.Ok(json.decodeFromJsonElement(Project.serializer(), root))
        }
    } catch (e: Exception) {
        ProjectDecodeResult.Unreadable(e.message ?: e::class.simpleName ?: "unknown error")
    }
}

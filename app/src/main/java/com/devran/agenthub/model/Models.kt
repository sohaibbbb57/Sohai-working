package com.devran.agenthub.model

import org.json.JSONArray
import org.json.JSONObject

/** AI apps used as selectable user-facing brains. Package ids are best-effort defaults and can change. */
enum class BrainProvider(val displayName: String, val packageCandidates: List<String>) {
    GEMINI("Gemini", listOf("com.google.android.apps.bard", "com.google.android.apps.bard.beta")),
    CHATGPT("ChatGPT", listOf("com.openai.chatgpt")),
    KIMI("Kimi", listOf("com.moonshot.kimichat")),
    QWEN("Qwen", listOf("com.alibaba.intl.android.ai", "com.alibaba.android.riflestudio")),
    DEEPSEEK("DeepSeek", listOf("com.deepseek.chat")),
    CLAUDE("Claude", listOf("com.anthropic.claude")),
    GROK("Grok", listOf("ai.x.grok", "ai.grok"))
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val category: String,
    val dangerous: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val enabledByDefault: Boolean = true,
    val inputSchema: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("category", category)
        put("dangerous", dangerous)
        put("requires_confirmation", requiresConfirmation)
        put("input", JSONObject(inputSchema))
    }
}

data class ToolCall(val name: String, val arguments: Map<String, String> = emptyMap()) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action", name)
        put("arguments", JSONObject(arguments))
    }
}

data class ActionResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("success", success)
        put("message", message)
        put("requires_confirmation", requiresConfirmation)
        put("data", JSONObject(data))
    }
}

data class AgentTask(
    val id: String,
    val instruction: String,
    val provider: BrainProvider,
    val createdAt: Long,
    val status: String,
    val step: Int,
    val maxSteps: Int
)

data class LogEntry(val time: Long, val level: String, val message: String)

data class AgentSettings(
    val selectedBrain: BrainProvider = BrainProvider.CLAUDE,
    val darkMode: Boolean = true,
    val accent: Accent = Accent.VIOLET,
    val bridgeEnabled: Boolean = false,
    val maxSteps: Int = 30,
    val requireConfirmationForDangerous: Boolean = true,
    val autoVerifyActions: Boolean = true,
    val backgroundAgent: Boolean = false
)

enum class Accent(val label: String, val seed: Long) {
    VIOLET("Violet", 0x8B7CFF),
    CYAN("Cyan", 0x54D6FF),
    GREEN("Green", 0x72E5A3),
    AMBER("Amber", 0xFFC857),
    PINK("Pink", 0xFF72B6)
}

fun JSONObject.toToolCallOrNull(): ToolCall? = runCatching {
    val name = optString("action").ifBlank { optString("tool") }.trim()
    if (name.isBlank()) return null
    val args = linkedMapOf<String, String>()
    val obj = optJSONObject("arguments") ?: optJSONObject("args") ?: JSONObject()
    obj.keys().forEach { key -> args[key] = obj.optString(key) }
    ToolCall(name, args)
}.getOrNull()

fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) add(optString(i))
}

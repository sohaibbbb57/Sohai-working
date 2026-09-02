package com.devran.agenthub.data

import android.content.Context
import com.devran.agenthub.model.Accent
import com.devran.agenthub.model.AgentSettings
import com.devran.agenthub.model.BrainProvider
import com.devran.agenthub.model.LogEntry
import com.devran.agenthub.model.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject

class AgentStore(context: Context) {
    private val prefs = context.getSharedPreferences("agenthub", Context.MODE_PRIVATE)
    private val logKey = "logs"

    fun loadSettings(): AgentSettings = AgentSettings(
        selectedBrain = runCatching { BrainProvider.valueOf(prefs.getString("brain", BrainProvider.CLAUDE.name)!!) }.getOrDefault(BrainProvider.CLAUDE),
        darkMode = prefs.getBoolean("dark", true),
        accent = runCatching { Accent.valueOf(prefs.getString("accent", Accent.VIOLET.name)!!) }.getOrDefault(Accent.VIOLET),
        bridgeEnabled = prefs.getBoolean("bridge", false),
        maxSteps = prefs.getInt("maxSteps", 30).coerceIn(1, 100),
        requireConfirmationForDangerous = prefs.getBoolean("dangerConfirm", true),
        autoVerifyActions = prefs.getBoolean("autoVerify", true),
        backgroundAgent = prefs.getBoolean("backgroundAgent", false)
    )

    fun saveSettings(s: AgentSettings) {
        prefs.edit()
            .putString("brain", s.selectedBrain.name)
            .putBoolean("dark", s.darkMode)
            .putString("accent", s.accent.name)
            .putBoolean("bridge", s.bridgeEnabled)
            .putInt("maxSteps", s.maxSteps)
            .putBoolean("dangerConfirm", s.requireConfirmationForDangerous)
            .putBoolean("autoVerify", s.autoVerifyActions)
            .putBoolean("backgroundAgent", s.backgroundAgent)
            .apply()
    }

    fun putNote(key: String, value: String) { prefs.edit().putString("note_$key", value).apply() }
    fun getNote(key: String): String? = prefs.getString("note_$key", null)
    fun deleteNote(key: String) { prefs.edit().remove("note_$key").apply() }

    fun customTools(): List<ToolDefinition> {
        val array = runCatching { JSONArray(prefs.getString("custom_tools", "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val input = mutableMapOf<String, String>()
                val schema = o.optJSONObject("input") ?: JSONObject()
                schema.keys().forEach { input[it] = schema.optString(it) }
                add(ToolDefinition(
                    name = o.optString("name"),
                    description = o.optString("description"),
                    category = o.optString("category", "Custom"),
                    inputSchema = input
                ))
            }
        }.filter { it.name.isNotBlank() }
    }

    fun saveCustomTool(tool: ToolDefinition) {
        val array = runCatching { JSONArray(prefs.getString("custom_tools", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until array.length()) if (array.optJSONObject(i)?.optString("name") == tool.name) { array.remove(i); break }
        array.put(JSONObject().apply {
            put("name", tool.name)
            put("description", tool.description)
            put("category", tool.category)
            put("input", JSONObject(tool.inputSchema))
        })
        prefs.edit().putString("custom_tools", array.toString()).apply()
    }

    fun removeCustomTool(name: String) {
        val array = runCatching { JSONArray(prefs.getString("custom_tools", "[]")) }.getOrDefault(JSONArray())
        for (i in array.length() - 1 downTo 0) if (array.optJSONObject(i)?.optString("name") == name) array.remove(i)
        prefs.edit().putString("custom_tools", array.toString()).apply()
    }

    @Synchronized fun appendLog(level: String, message: String) {
        val array = readLogsJson()
        array.put(JSONObject().apply { put("time", System.currentTimeMillis()); put("level", level); put("message", message.take(1200)) })
        while (array.length() > 250) array.remove(0)
        prefs.edit().putString(logKey, array.toString()).apply()
    }

    fun logs(): List<LogEntry> {
        val array = readLogsJson()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(LogEntry(item.optLong("time"), item.optString("level"), item.optString("message")))
            }
        }.asReversed()
    }

    fun clearLogs() { prefs.edit().remove(logKey).apply() }
    private fun readLogsJson(): JSONArray = runCatching { JSONArray(prefs.getString(logKey, "[]")) }.getOrDefault(JSONArray())
}

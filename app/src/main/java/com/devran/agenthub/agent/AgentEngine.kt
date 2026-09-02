package com.devran.agenthub.agent

import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.devran.agenthub.automation.AgentAccessibilityService
import com.devran.agenthub.bridge.BrainBridge
import com.devran.agenthub.data.AgentStore
import com.devran.agenthub.model.ActionResult
import com.devran.agenthub.model.AgentSettings
import com.devran.agenthub.model.BrainProvider
import com.devran.agenthub.model.ToolCall
import com.devran.agenthub.model.toToolCallOrNull
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import org.json.JSONObject

class AgentEngine(context: Context) {
    val appContext = context.applicationContext
    val store = AgentStore(appContext)
    val registry = ToolRegistry()
    val bridge = BrainBridge(appContext)
    val systemPrompt: String get() = SystemPrompt.build(registry.all())

    init { store.customTools().forEach(registry::register) }

    @Volatile var stopped: Boolean = false
        private set
    @Volatile var paused: Boolean = false
        private set
    @Volatile var currentTask: String = ""
        private set
    @Volatile var currentStep: Int = 0
        private set

    private val enabledOverrides = mutableMapOf<String, Boolean>()
    var settings: AgentSettings = store.loadSettings()
        private set

    fun reloadSettings() { settings = store.loadSettings() }

    fun addCustomTool(tool: com.devran.agenthub.model.ToolDefinition) { registry.register(tool); store.saveCustomTool(tool); store.appendLog("TOOL", "Registered custom tool ${tool.name}") }
    fun removeCustomTool(name: String) { registry.remove(name); store.removeCustomTool(name); store.appendLog("TOOL", "Removed custom tool $name") }

    fun updateSettings(newSettings: AgentSettings) {
        settings = newSettings
        store.saveSettings(newSettings)
    }

    fun startTask(instruction: String, provider: BrainProvider = settings.selectedBrain): String {
        stopped = false
        paused = false
        currentTask = instruction.trim()
        currentStep = 0
        val id = UUID.randomUUID().toString().take(8)
        store.appendLog("TASK", "Started $id using ${provider.displayName}: ${instruction.take(180)}")
        return id
    }

    fun nextStep() { currentStep++ }
    fun stopAll() { stopped = true; paused = false; store.appendLog("STOP", "Emergency stop") }
    fun pause() { paused = true; store.appendLog("PAUSE", "Agent paused") }
    fun resume() { stopped = false; paused = false; store.appendLog("RESUME", "Agent resumed") }
    fun resetTask() { currentTask = ""; currentStep = 0 }

    fun parseToolCall(raw: String): ToolCall? {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val direct = runCatching { JSONObject(cleaned).toToolCallOrNull() }.getOrNull()
        if (direct != null) return direct
        val start = cleaned.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\' && inString) { escaped = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '{') depth++
            if (c == '}') {
                depth--
                if (depth == 0) {
                    val candidate = cleaned.substring(start, i + 1)
                    return runCatching { JSONObject(candidate).toToolCallOrNull() }.getOrNull()
                }
            }
        }
        return null
    }

    fun executeJson(raw: String, confirmed: Boolean = false): ActionResult {
        val call = parseToolCall(raw) ?: return ActionResult(false, "Invalid JSON action.")
        return execute(call, confirmed)
    }

    fun execute(call: ToolCall, confirmed: Boolean = false): ActionResult {
        if (stopped) return ActionResult(false, "Agent stopped.")
        if (paused && call.name !in setOf("resume_agent", "stop_agent", "task_status")) return ActionResult(false, "Agent paused.")
        if (currentStep >= settings.maxSteps && call.name !in setOf("task_status", "stop_agent")) return ActionResult(false, "Step limit reached (${settings.maxSteps}).")

        val definition = registry.get(call.name) ?: return ActionResult(false, "Unknown tool: ${call.name}")
        if (enabledOverrides[call.name] == false) return ActionResult(false, "Tool disabled: ${call.name}")
        if (definition.requiresConfirmation && settings.requireConfirmationForDangerous && !confirmed) {
            store.appendLog("CONFIRM", "Confirmation required for ${call.name}")
            return ActionResult(false, "Confirmation required for ${call.name}.", requiresConfirmation = true)
        }

        currentStep++
        val result = runCatching { executeInternal(call) }.getOrElse { ActionResult(false, it.message ?: "Tool failed.") }
        store.appendLog(if (result.success) "OK" else "ERR", "${call.name}: ${result.message}")
        return result
    }

    private fun executeInternal(call: ToolCall): ActionResult {
        val svc = AgentAccessibilityService.instance.get()
        fun arg(name: String) = call.arguments[name].orEmpty()
        fun num(name: String, default: Float = 0f) = arg(name).toFloatOrNull() ?: default
        fun int(name: String, default: Int = 0) = arg(name).toIntOrNull() ?: default

        return when (call.name) {
            "screenshot" -> ActionResult(true, "Latest MediaProjection frame is ${if (com.devran.agenthub.screen.ScreenCaptureService.latestFrame != null) "available" else "not available"}.")
            "screen_size" -> ActionResult(true, "Display metrics", mapOf(
                "width" to appContext.resources.displayMetrics.widthPixels.toString(),
                "height" to appContext.resources.displayMetrics.heightPixels.toString(),
                "density" to appContext.resources.displayMetrics.densityDpi.toString()
            ))
            "current_app" -> ActionResult(true, AgentAccessibilityService.lastPackage ?: "unknown")
            "ui_tree" -> if (svc != null) ActionResult(true, svc.dumpTree()) else ActionResult(false, "Accessibility service is disabled.")
            "find_text" -> {
                val node = svc?.findText(arg("text"))
                ActionResult(node != null, svc?.nodeInfo(node) ?: "Accessibility service disabled.")
            }
            "find_description" -> {
                val node = svc?.findByDescription(arg("text"))
                ActionResult(node != null, svc?.nodeInfo(node) ?: "Accessibility service disabled.")
            }
            "find_element" -> {
                val node = svc?.findElement(arg("query"))
                ActionResult(node != null, svc?.nodeInfo(node) ?: "Accessibility service disabled.")
            }
            "element_bounds" -> {
                val node = svc?.findElement(arg("query"))
                ActionResult(node != null, svc?.nodeInfo(node) ?: "Accessibility service disabled.")
            }
            "focused_element" -> {
                val node = svc?.root()?.let { root -> findFocused(root) }
                ActionResult(node != null, svc?.nodeInfo(node) ?: "Not found")
            }
            "screen_changed" -> ActionResult(true, "last_ui_change_ms=${AgentAccessibilityService.lastWindowContentAt}")
            "tap" -> boolResult(svc?.tap(num("x"), num("y")), "Tap")
            "double_tap" -> boolResult(svc?.doubleTap(num("x"), num("y")), "Double tap")
            "long_press" -> boolResult(svc?.longPress(num("x"), num("y"), int("duration_ms", 650).toLong()), "Long press")
            "swipe" -> boolResult(svc?.swipe(num("x1"), num("y1"), num("x2"), num("y2"), int("duration_ms", 400).toLong()), "Swipe")
            "scroll" -> if (svc != null) boolResult(svc.scroll(svc.root(), arg("direction").lowercase() in setOf("down", "right")), "Scroll") else ActionResult(false, "Accessibility service is disabled.")
            "type_text" -> boolResult(svc?.setText(arg("text")), "Type text")
            "clear_text" -> boolResult(svc?.clearFocusedText(), "Clear text")
            "paste_text" -> {
                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AgentHub", arg("text")))
                boolResult(svc?.setText(arg("text")), "Paste text")
            }
            "back" -> boolResult(svc?.globalBack(), "Back")
            "home" -> boolResult(svc?.globalHome(), "Home")
            "recent_apps" -> boolResult(svc?.globalRecents(), "Recent apps")
            "press_enter" -> if (svc != null) boolResult(svc.imeEnter(), "IME Enter") else ActionResult(false, "Accessibility service is disabled.")
            "tap_element" -> {
                val node = svc?.findElement(arg("query")); boolResult(svc?.click(node), "Tap element")
            }
            "long_press_element" -> {
                val node = svc?.findElement(arg("query")); boolResult(svc?.longClick(node), "Long press element")
            }
            "focus_element" -> boolResult(svc?.focus(svc.findElement(arg("query"))), "Focus element")
            "set_element_text" -> {
                val node = svc?.findElement(arg("query")); boolResult(svc?.setText(arg("text"), node), "Set element text")
            }
            "scroll_element" -> {
                val node = svc?.findElement(arg("query")); boolResult(svc?.scroll(node, arg("direction").lowercase() in setOf("down", "right")), "Scroll element")
            }
            "expand_element" -> nodeAction(svc?.findElement(arg("query")), android.view.accessibility.AccessibilityNodeInfo.ACTION_EXPAND, "Expand")
            "collapse_element" -> nodeAction(svc?.findElement(arg("query")), android.view.accessibility.AccessibilityNodeInfo.ACTION_COLLAPSE, "Collapse")

            "launch_app" -> launchPackage(arg("package"))
            "launch_brain" -> bridge.launch(settings.selectedBrain).fold({ ActionResult(true, "Launched ${settings.selectedBrain.displayName}", mapOf("package" to it)) }, { ActionResult(false, it.message ?: "Cannot launch brain") })
            "switch_to_app" -> launchPackage(arg("package"))
            "open_app_settings", "open_app_details" -> openAppDetails(arg("package"))
            "app_info" -> appInfo(arg("package"))
            "installed_apps" -> installedApps()
            "open_accessibility_settings" -> { appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ActionResult(true, "Accessibility settings opened") }

            "battery" -> batteryInfo()
            "device_info" -> ActionResult(true, "Device info", mapOf("model" to Build.MODEL, "manufacturer" to Build.MANUFACTURER, "android" to Build.VERSION.RELEASE, "sdk" to Build.VERSION.SDK_INT.toString()))
            "orientation" -> ActionResult(true, when (appContext.resources.configuration.orientation) { Configuration.ORIENTATION_LANDSCAPE -> "landscape"; Configuration.ORIENTATION_PORTRAIT -> "portrait"; else -> "undefined" })
            "volume" -> {
                val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                ActionResult(true, "Media volume $cur/$max", mapOf("current" to cur.toString(), "max" to max.toString()))
            }
            "brightness" -> { appContext.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ActionResult(true, "Display settings opened") }
            "network_state" -> networkInfo()
            "time_now" -> ActionResult(true, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            "timezone" -> ActionResult(true, java.util.TimeZone.getDefault().id)
            "locale" -> ActionResult(true, java.util.Locale.getDefault().toLanguageTag())
            "clipboard_get" -> {
                val clip = (appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                ActionResult(true, clip?.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty())
            }
            "clipboard_set" -> {
                (appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AgentHub", arg("text")))
                ActionResult(true, "Clipboard updated")
            }

            "list_files" -> ActionResult(true, appContext.filesDir.walkTopDown().take(300).joinToString("\n") { it.relativeTo(appContext.filesDir).path })
            "read_private_file" -> readPrivate(arg("path"))
            "write_private_file" -> writePrivate(arg("path"), arg("content"))
            "delete_private_file" -> deletePrivate(arg("path"))
            "export_logs" -> exportLogs()
            "clear_logs" -> { store.clearLogs(); ActionResult(true, "Logs cleared") }

            "open_url" -> { val u = arg("url"); appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ActionResult(true, "Opened $u") }
            "share_text" -> { val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, arg("text")); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; appContext.startActivity(Intent.createChooser(share, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ActionResult(true, "Share UI opened") }
            "open_web_search" -> { val q = Uri.encode(arg("query")); appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ActionResult(true, "Search opened") }

            "task_status" -> ActionResult(true, "task=$currentTask; step=$currentStep/${settings.maxSteps}; paused=$paused; stopped=$stopped")
            "pause_agent" -> { pause(); ActionResult(true, "Paused") }
            "resume_agent" -> { resume(); ActionResult(true, "Resumed") }
            "stop_agent" -> { stopAll(); ActionResult(true, "Stopped") }
            "request_confirmation" -> ActionResult(false, "Confirmation must be handled by the host UI.", requiresConfirmation = true)
            "set_max_steps" -> { val max = int("max_steps", settings.maxSteps).coerceIn(1, 100); updateSettings(settings.copy(maxSteps = max)); ActionResult(true, "Max steps set to $max") }
            "save_note" -> { store.putNote(arg("key"), arg("value")); ActionResult(true, "Note saved") }
            "read_note" -> ActionResult(true, store.getNote(arg("key")).orEmpty())
            "delete_note" -> { store.deleteNote(arg("key")); ActionResult(true, "Note deleted") }
            "list_tools" -> ActionResult(true, registry.all().joinToString("\n") { it.name })
            "tool_status" -> ActionResult(true, registry.all().groupingBy { it.category }.eachCount().entries.joinToString("\n") { "${it.key}: ${it.value}" })
            "set_tool_enabled" -> { enabledOverrides[arg("name")] = arg("enabled").toBoolean(); ActionResult(true, "${arg("name")} enabled=${enabledOverrides[arg("name")]}" ) }
            "log_event" -> { store.appendLog("EVENT", arg("message")); ActionResult(true, "Logged") }

            "prepare_brain_prompt" -> ActionResult(true, bridge.buildEnvelope(settings.selectedBrain, arg("task"), systemPrompt))
            "submit_brain_prompt" -> bridge.buildEnvelope(settings.selectedBrain, arg("task"), systemPrompt).let { bridge.submitVisibleText(it).fold({ ActionResult(true, it) }, { ActionResult(false, it.message ?: "Unable to enter prompt") }) }
            "read_brain_response" -> ActionResult(true, bridge.readVisibleBrainUi())
            "send_brain_message" -> bridge.submitVisibleText(arg("text")).fold({ bridge.clickLikelySendButton().fold({ ActionResult(true, "$it ${it}") }, { ActionResult(true, it) }) }, { ActionResult(false, it.message ?: "Unable to enter message") })
            "verify_brain_open" -> {
                val pkg = AgentAccessibilityService.lastPackage.orEmpty()
                val installed = bridge.installedPackage(settings.selectedBrain).orEmpty()
                ActionResult(installed.isNotBlank() && pkg == installed, "foreground=$pkg expected=$installed")
            }

            "dump_action_log" -> ActionResult(true, store.logs().take(80).joinToString("\n") { "${it.level} ${it.message}" })
            "clear_task_history" -> { store.appendLog("TASK", "Task history clear requested"); ActionResult(true, "Current transient task state cleared"); resetTask() }
            "done" -> { val summary = arg("summary"); store.appendLog("DONE", summary); resetTask(); ActionResult(true, summary) }
            "blocked" -> { val reason = arg("reason"); store.appendLog("BLOCKED", reason); ActionResult(false, reason) }
            else -> ActionResult(false, "Tool ${call.name} is registered but not implemented in this build.")
        }
    }

    private fun boolResult(ok: Boolean?, label: String): ActionResult = if (ok == true) ActionResult(true, "$label accepted/completed") else ActionResult(false, "$label failed or Accessibility is disabled.")
    private fun nodeAction(node: android.view.accessibility.AccessibilityNodeInfo?, action: Int, label: String): ActionResult = boolResult(node?.performAction(action), label)

    private fun launchPackage(pkg: String): ActionResult {
        val intent = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return ActionResult(false, "No launch intent for $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return ActionResult(true, "Launched $pkg")
    }

    private fun openAppDetails(pkg: String): ActionResult {
        appContext.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult(true, "Opened app settings for $pkg")
    }

    private fun appInfo(pkg: String): ActionResult = runCatching {
        val info = appContext.packageManager.getApplicationInfo(pkg, 0)
        ActionResult(true, appContext.packageManager.getApplicationLabel(info).toString(), mapOf("package" to pkg, "sourceDir" to info.sourceDir))
    }.getOrElse { ActionResult(false, "Package not found: $pkg") }

    private fun installedApps(): ActionResult = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = appContext.packageManager.queryIntentActivities(intent, 0).sortedBy { it.loadLabel(appContext.packageManager).toString().lowercase() }
        ActionResult(true, list.take(300).joinToString("\n") { "${it.loadLabel(appContext.packageManager)} = ${it.activityInfo.packageName}" })
    }.getOrElse { ActionResult(false, it.message ?: "Cannot list apps") }

    private fun batteryInfo(): ActionResult {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return ActionResult(true, "$pct% ${if (charging) "charging" else "not charging"}", mapOf("percent" to pct.toString(), "charging" to charging.toString()))
    }

    private fun networkInfo(): ActionResult {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return ActionResult(true, "offline")
        val caps = cm.getNetworkCapabilities(network) ?: return ActionResult(true, "offline")
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return ActionResult(true, type, mapOf("validated" to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString()))
    }

    private fun safePrivateFile(path: String): File? {
        if (path.isBlank() || path.contains("..") || path.startsWith("/") || path.contains('\u0000')) return null
        val base = appContext.filesDir.canonicalFile
        val target = File(base, path).canonicalFile
        return if (target.path == base.path || target.path.startsWith(base.path + File.separator)) target else null
    }

    private fun readPrivate(path: String): ActionResult {
        val file = safePrivateFile(path) ?: return ActionResult(false, "Invalid private path")
        return if (!file.exists()) ActionResult(false, "File not found") else ActionResult(true, file.readText().take(100_000))
    }

    private fun writePrivate(path: String, content: String): ActionResult {
        val file = safePrivateFile(path) ?: return ActionResult(false, "Invalid private path")
        file.parentFile?.mkdirs()
        file.writeText(content.take(500_000))
        return ActionResult(true, "Wrote ${file.relativeTo(appContext.filesDir).path}")
    }

    private fun deletePrivate(path: String): ActionResult {
        val file = safePrivateFile(path) ?: return ActionResult(false, "Invalid private path")
        return if (file.delete()) ActionResult(true, "Deleted ${file.relativeTo(appContext.filesDir).path}") else ActionResult(false, "Delete failed")
    }

    private fun exportLogs(): ActionResult {
        val file = File(appContext.filesDir, "agenthub-log-${System.currentTimeMillis()}.txt")
        file.writeText(store.logs().asReversed().joinToString("\n") { "${it.time} [${it.level}] ${it.message}" })
        return ActionResult(true, file.name)
    }

    private fun findFocused(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findFocused(it) }?.let { return it }
        return null
    }
}

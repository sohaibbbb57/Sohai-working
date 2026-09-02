package com.devran.agenthub.agent

import com.devran.agenthub.model.ToolDefinition

class ToolRegistry {
    private val tools = LinkedHashMap<String, ToolDefinition>()

    init { registerCoreTools() }

    fun register(tool: ToolDefinition) { tools[tool.name] = tool }
    fun remove(name: String) { tools.remove(name) }
    fun get(name: String): ToolDefinition? = tools[name]
    fun all(): List<ToolDefinition> = tools.values.toList()
    fun count(): Int = tools.size

    private fun t(name: String, description: String, category: String, dangerous: Boolean = false, confirm: Boolean = false, vararg schema: Pair<String, String>) =
        register(ToolDefinition(name, description, category, dangerous, confirm, inputSchema = schema.toMap()))

    private fun registerCoreTools() {
        // SCREEN + OBSERVATION
        t("screenshot", "Capture the latest user-authorized device screen frame.", "Screen")
        t("screen_size", "Return screen width, height and density.", "Screen")
        t("current_app", "Return the foreground application package when visible to AccessibilityService.", "Screen")
        t("ui_tree", "Dump the current visible accessibility UI hierarchy.", "Screen")
        t("find_text", "Find a visible UI node by text or partial text.", "Screen", schema = *arrayOf("text" to "string"))
        t("find_description", "Find a visible UI node by content description.", "Screen", schema = *arrayOf("text" to "string"))
        t("find_element", "Find the first useful visible element by text, id, class or description.", "Screen", schema = *arrayOf("query" to "string"))
        t("element_bounds", "Return screen bounds for a matched element.", "Screen", schema = *arrayOf("query" to "string"))
        t("focused_element", "Return information about the currently focused editable/UI node.", "Screen")
        t("screen_changed", "Check whether the accessibility UI changed since the last observation.", "Screen")

        // INPUT
        t("tap", "Tap an absolute screen coordinate.", "Input", schema = *arrayOf("x" to "number", "y" to "number"))
        t("double_tap", "Double tap an absolute screen coordinate.", "Input", schema = *arrayOf("x" to "number", "y" to "number"))
        t("long_press", "Long press an absolute screen coordinate.", "Input", schema = *arrayOf("x" to "number", "y" to "number", "duration_ms" to "integer"))
        t("swipe", "Swipe between two screen coordinates.", "Input", schema = *arrayOf("x1" to "number", "y1" to "number", "x2" to "number", "y2" to "number", "duration_ms" to "integer"))
        t("scroll", "Scroll the currently focused/list UI forward or backward.", "Input", schema = *arrayOf("direction" to "up|down|left|right"))
        t("type_text", "Set text in the focused or first editable UI field.", "Input", schema = *arrayOf("text" to "string"))
        t("clear_text", "Clear the focused editable field.", "Input")
        t("paste_text", "Set clipboard text and paste where supported.", "Input", schema = *arrayOf("text" to "string"))
        t("back", "Navigate back in the foreground application.", "Input")
        t("home", "Go to the Android home screen.", "Input")
        t("recent_apps", "Open Android recent apps.", "Input")
        t("press_enter", "Press Enter in a focused text field where supported.", "Input")

        // ELEMENT ACTIONS
        t("tap_element", "Click a visible element matched by text/description/id.", "Element", schema = *arrayOf("query" to "string"))
        t("long_press_element", "Long press a matched visible element.", "Element", schema = *arrayOf("query" to "string"))
        t("focus_element", "Focus a matched editable/control element.", "Element", schema = *arrayOf("query" to "string"))
        t("set_element_text", "Set text on a matched editable element.", "Element", schema = *arrayOf("query" to "string", "text" to "string"))
        t("scroll_element", "Scroll a matched scrollable element.", "Element", schema = *arrayOf("query" to "string", "direction" to "up|down"))
        t("expand_element", "Expand an expandable matched element.", "Element", schema = *arrayOf("query" to "string"))
        t("collapse_element", "Collapse a collapsible matched element.", "Element", schema = *arrayOf("query" to "string"))

        // APPS
        t("launch_app", "Launch an installed app by package name.", "Apps", schema = *arrayOf("package" to "string"))
        t("launch_brain", "Launch the currently selected AI brain application.", "Apps")
        t("open_app_settings", "Open Android settings for an app package.", "Apps", schema = *arrayOf("package" to "string"))
        t("app_info", "Return basic metadata for an installed app package.", "Apps", schema = *arrayOf("package" to "string"))
        t("installed_apps", "List visible launchable applications.", "Apps")
        t("switch_to_app", "Bring an existing app task to the foreground where Android permits.", "Apps", schema = *arrayOf("package" to "string"))
        t("open_accessibility_settings", "Open Android Accessibility settings.", "Apps")
        t("open_app_details", "Open Android App Details settings for a package.", "Apps", schema = *arrayOf("package" to "string"))

        // DEVICE
        t("battery", "Return battery percentage and charging state.", "Device")
        t("device_info", "Return device model, Android release and SDK.", "Device")
        t("orientation", "Return current display orientation.", "Device")
        t("volume", "Return current stream volume where readable.", "Device")
        t("brightness", "Open brightness settings; reading system brightness may be restricted.", "Device")
        t("network_state", "Return coarse network connectivity state.", "Device")
        t("time_now", "Return current device time.", "Device")
        t("timezone", "Return the device timezone.", "Device")
        t("locale", "Return the default device locale.", "Device")
        t("clipboard_get", "Read clipboard text when the platform allows it.", "Device")
        t("clipboard_set", "Set clipboard text.", "Device", schema = *arrayOf("text" to "string"))

        // FILES, intentionally conservative for MVP
        t("list_files", "List files under the app's private storage directory.", "Files")
        t("read_private_file", "Read a small text file inside AgentHub private storage.", "Files", schema = *arrayOf("path" to "string"))
        t("write_private_file", "Write a text file inside AgentHub private storage.", "Files", schema = *arrayOf("path" to "string", "content" to "string"))
        t("delete_private_file", "Delete a file inside AgentHub private storage.", "Files", dangerous = true, confirm = true, schema = *arrayOf("path" to "string"))
        t("export_logs", "Export the local action log to app-private storage.", "Files")
        t("clear_logs", "Clear local AgentHub logs.", "Files", dangerous = true, confirm = true)

        // NETWORK
        t("open_url", "Open a URL using the user's default browser.", "Web", schema = *arrayOf("url" to "string"))
        t("share_text", "Open Android share UI with text prepared for the user.", "Web", schema = *arrayOf("text" to "string"))
        t("open_web_search", "Open a browser search for a text query.", "Web", schema = *arrayOf("query" to "string"))

        // AGENT CONTROL
        t("task_status", "Return the current agent task state.", "Agent")
        t("pause_agent", "Pause the current agent loop.", "Agent")
        t("resume_agent", "Resume a paused agent loop.", "Agent")
        t("stop_agent", "Stop all agent operations immediately.", "Agent")
        t("request_confirmation", "Ask the user to confirm a gated action.", "Agent")
        t("set_max_steps", "Change the agent step cap for the current session.", "Agent", dangerous = false, schema = *arrayOf("max_steps" to "integer"))
        t("save_note", "Save a short local agent note.", "Agent", schema = *arrayOf("key" to "string", "value" to "string"))
        t("read_note", "Read a saved local agent note.", "Agent", schema = *arrayOf("key" to "string"))
        t("delete_note", "Delete a saved local agent note.", "Agent", dangerous = true, confirm = true, schema = *arrayOf("key" to "string"))
        t("list_tools", "List all currently registered tools.", "Agent")
        t("tool_status", "Return tool counts by category and enabled state.", "Agent")
        t("set_tool_enabled", "Enable/disable a registered tool for the agent.", "Agent", schema = *arrayOf("name" to "string", "enabled" to "boolean"))
        t("log_event", "Append a structured event to the local agent log.", "Agent", schema = *arrayOf("message" to "string"))

        // BRIDGE
        t("prepare_brain_prompt", "Build a visible agent envelope for the selected AI app.", "Brain Bridge", schema = *arrayOf("task" to "string"))
        t("submit_brain_prompt", "Enter an agent envelope into a visible, focused AI app field. User-authorized Accessibility is required.", "Brain Bridge", schema = *arrayOf("task" to "string"))
        t("read_brain_response", "Read the visible AI app UI tree for text that may contain a response.", "Brain Bridge")
        t("send_brain_message", "Submit prepared text via the selected AI app's visible UI controls where detected.", "Brain Bridge", schema = *arrayOf("text" to "string"))
        t("verify_brain_open", "Verify the selected AI brain package is the foreground package.", "Brain Bridge")

        // DEBUG
        t("dump_action_log", "Return recent action log entries.", "Debug")
        t("clear_task_history", "Clear local task history.", "Debug", dangerous = true, confirm = true)
    }
}

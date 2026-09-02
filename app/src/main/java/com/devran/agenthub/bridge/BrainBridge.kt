package com.devran.agenthub.bridge

import android.content.Context
import android.content.Intent
import com.devran.agenthub.automation.AgentAccessibilityService
import com.devran.agenthub.model.BrainProvider
import org.json.JSONObject

class BrainBridge(private val context: Context) {
    fun installedPackage(provider: BrainProvider): String? = provider.packageCandidates.firstOrNull {
        runCatching { context.packageManager.getLaunchIntentForPackage(it) != null }.getOrDefault(false)
    }

    fun launch(provider: BrainProvider): Result<String> {
        val pkg = installedPackage(provider) ?: return Result.failure(IllegalStateException("${provider.displayName} is not installed or has no launch intent."))
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return Result.failure(IllegalStateException("No launch intent."))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Result.success(pkg)
    }

    /** This is a visible UI envelope, not a modification of another app's hidden system prompt. */
    fun buildEnvelope(provider: BrainProvider, task: String, systemPrompt: String): String = buildString {
        appendLine("AGENTHUB TASK ENVELOPE")
        appendLine("BRAIN: ${provider.displayName}")
        appendLine("Return exactly one JSON action object at a time.")
        appendLine("Do not use markdown fences around JSON.")
        appendLine("ACTION SCHEMA: {\"action\":\"tool_name\",\"arguments\":{...}}")
        appendLine("When done: {\"action\":\"done\",\"arguments\":{\"summary\":\"...\"}}")
        appendLine("HOST SYSTEM CONTRACT:")
        appendLine(systemPrompt.take(14000))
        appendLine("USER TASK:")
        appendLine(task.trim())
        appendLine("END ENVELOPE")
    }

    fun submitVisibleText(text: String): Result<String> {
        val svc = AgentAccessibilityService.instance.get() ?: return Result.failure(IllegalStateException("Accessibility service is not enabled."))
        val ok = svc.setText(text)
        return if (ok) Result.success("Text entered into the focused editable field.")
        else Result.failure(IllegalStateException("No editable field could be found in the active window."))
    }

    fun clickLikelySendButton(): Result<String> {
        val svc = AgentAccessibilityService.instance.get() ?: return Result.failure(IllegalStateException("Accessibility service is not enabled."))
        val candidates = listOf("Send", "send", "Submit", "submit", "Ask", "ask")
        for (c in candidates) {
            val node = svc.findElement(c) ?: continue
            if (svc.click(node)) return Result.success("Clicked visible '$c' control.")
        }
        return Result.failure(IllegalStateException("Could not identify a visible send/submit control."))
    }

    fun readVisibleBrainUi(): String = AgentAccessibilityService.instance.get()?.dumpTree(20000) ?: "Accessibility service disabled."

    fun actionJson(action: String, args: Map<String, String> = emptyMap()): String = JSONObject().apply {
        put("action", action)
        put("arguments", JSONObject(args))
    }.toString()
}

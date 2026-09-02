package com.devran.agenthub.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AgentAccessibilityService : AccessibilityService() {
    companion object {
        val instance = AtomicReference<AgentAccessibilityService?>(null)
        @Volatile var lastPackage: String? = null
        @Volatile var lastEventAt: Long = 0L
        @Volatile var lastWindowContentAt: Long = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance.set(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        lastPackage = event.packageName?.toString()
        lastEventAt = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastWindowContentAt = lastEventAt
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance.compareAndSet(this, null)
        super.onDestroy()
    }

    fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    fun findText(text: String): AccessibilityNodeInfo? = root()?.findAccessibilityNodeInfosByText(text).orEmpty()
        .firstOrNull { visible(it) }

    fun findByDescription(text: String): AccessibilityNodeInfo? = root()?.let { findRecursive(it) { n -> visible(n) && n.contentDescription?.toString()?.contains(text, true) == true } }

    fun findElement(query: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        val normalized = query.trim()
        return findRecursive(r) { n ->
            if (!visible(n)) return@findRecursive false
            val id = n.viewIdResourceName.orEmpty()
            val text = n.text?.toString().orEmpty()
            val desc = n.contentDescription?.toString().orEmpty()
            val cls = n.className?.toString().orEmpty()
            listOf(text, desc, id, cls).any { it.contains(normalized, ignoreCase = true) }
        }
    }

    fun nodeInfo(node: AccessibilityNodeInfo?): String = node?.let {
        val rect = Rect().also(it::getBoundsInScreen)
        "text=${it.text}; desc=${it.contentDescription}; id=${it.viewIdResourceName}; class=${it.className}; clickable=${it.isClickable}; editable=${it.isEditable}; bounds=${rect.flattenToString()}"
    } ?: "not_found"

    fun click(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        repeat(6) {
            if (current == null) return false
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            current = current.parent
        }
        return false
    }

    fun longClick(node: AccessibilityNodeInfo?, durationMs: Long = 650L): Boolean {
        val rect = Rect().also { node?.getBoundsInScreen(it) }
        if (rect.isEmpty) return false
        return longPress(rect.exactCenterX(), rect.exactCenterY(), durationMs)
    }

    fun setText(text: String, target: AccessibilityNodeInfo? = null): Boolean {
        val node = target ?: root()?.let { findRecursive(it) { n -> visible(n) && n.isEditable && n.isFocused } }
            ?: root()?.let { findRecursive(it) { n -> visible(n) && n.isEditable } }
            ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clearFocusedText(): Boolean = setText("")

    fun imeEnter(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 30) return false
        val node = root()?.let { findFocusedEditable(it) } ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
    }

    fun focus(node: AccessibilityNodeInfo?): Boolean = node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) == true || node?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) == true

    fun scroll(node: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (node == null) return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node.performAction(action)) return true
        var p = node.parent
        repeat(5) {
            if (p == null) return false
            if (p.performAction(action)) return true
            p = p.parent
        }
        return false
    }

    fun tap(x: Float, y: Float): Boolean = dispatchGestureSync(gestureLine(x, y, x, y, 50L))

    fun doubleTap(x: Float, y: Float): Boolean = tap(x, y) && runCatching {
        Thread.sleep(80)
        tap(x, y)
    }.getOrDefault(false)

    fun longPress(x: Float, y: Float, durationMs: Long = 650L): Boolean = dispatchGestureSync(gestureLine(x, y, x, y, durationMs.coerceIn(250, 2000)))

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 400L): Boolean = dispatchGestureSync(gestureLine(x1, y1, x2, y2, durationMs.coerceIn(100, 5000)))

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun globalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun dumpTree(maxChars: Int = 18000): String {
        val out = StringBuilder()
        root()?.let { dump(it, out, 0, maxChars) }
        return out.toString().take(maxChars)
    }

    private fun gestureLine(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): GestureDescription {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
    }

    private fun dispatchGestureSync(gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        var completed = false
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { completed = true; latch.countDown() }
            override fun onCancelled(gestureDescription: GestureDescription?) { completed = false; latch.countDown() }
        }, null)
        if (!accepted) return false
        latch.await(6, TimeUnit.SECONDS)
        return completed
    }

    private fun dump(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int, max: Int) {
        if (out.length >= max) return
        repeat(depth.coerceAtMost(20)) { out.append("  ") }
        val r = Rect().also { node.getBoundsInScreen(it) }
        out.append("class=").append(node.className)
            .append(" text=").append(node.text)
            .append(" desc=").append(node.contentDescription)
            .append(" id=").append(node.viewIdResourceName)
            .append(" clickable=").append(node.isClickable)
            .append(" editable=").append(node.isEditable)
            .append(" enabled=").append(node.isEnabled)
            .append(" bounds=").append(r.flattenToString()).append('\n')
        for (i in 0 until node.childCount) node.getChild(i)?.let { dump(it, out, depth + 1, max) }
    }

    private fun visible(n: AccessibilityNodeInfo): Boolean = n.isVisibleToUser && n.isEnabled

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findFocusedEditable(it) }?.let { return it }
        return null
    }

    private fun findRecursive(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findRecursive(child, predicate)
            if (result != null) return result
        }
        return null
    }
}

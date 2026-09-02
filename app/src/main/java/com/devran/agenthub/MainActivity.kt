package com.devran.agenthub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.devran.agenthub.agent.AgentEngine
import com.devran.agenthub.agent.AgentForegroundService
import com.devran.agenthub.data.AgentStore
import com.devran.agenthub.model.Accent
import com.devran.agenthub.model.ActionResult
import com.devran.agenthub.model.AgentSettings
import com.devran.agenthub.model.BrainProvider
import com.devran.agenthub.model.ToolDefinition
import com.devran.agenthub.screen.ScreenCaptureService
import com.devran.agenthub.ui.AgentHubTheme
import com.devran.agenthub.util.RuntimeChecks
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgentHubApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHubApp() {
    val context = LocalContext.current
    val engine = remember { AgentEngine(context) }
    var settings by remember { mutableStateOf(engine.settings) }
    var tab by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Ready") }
    var output by remember { mutableStateOf("") }
    var task by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var logTick by remember { mutableIntStateOf(0) }

    fun setSettings(s: AgentSettings) {
        settings = s
        engine.updateSettings(s)
    }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, intent)
            status = "Screen capture active"
        } else status = "Screen capture permission denied"
    }

    AgentHubTheme(darkTheme = settings.darkMode, accent = settings.accent) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AgentHub", fontWeight = FontWeight.ExtraBold)
                            Text("Device agent control center", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        AssistChip(onClick = {}, label = { Text(if (RuntimeChecks.isAccessibilityEnabled(context)) "ACCESS ON" else "ACCESS OFF") }, leadingIcon = null)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val titles = listOf("Agent", "Tools", "Activity", "Settings")
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                    titles.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
                }
                when (tab) {
                    0 -> AgentScreen(
                        context, engine, settings, task, { task = it }, status, { status = it }, output, { output = it },
                        onSettings = ::setSettings,
                        onCapture = { val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; captureLauncher.launch(mgr.createScreenCaptureIntent()) },
                        onConfirm = { confirmation = it },
                        onResult = { r -> output = r.message; status = if (r.success) "Success" else "Blocked/failed"; logTick++ }
                    )
                    1 -> ToolsScreen(engine, selectedTool, { selectedTool = it }, settings)
                    2 -> ActivityScreen(engine, logTick, onClear = { engine.store.clearLogs(); logTick++ })
                    3 -> SettingsScreen(context, engine, settings, ::setSettings, { status = it })
                }
            }
        }
    }

    if (confirmation != null) {
        val raw = confirmation!!
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Confirmation required") },
            text = { Text("AgentHub is asking to run a gated action:\n\n$raw") },
            confirmButton = {
                Button(onClick = {
                    confirmation = null
                    val r = engine.executeJson(raw, confirmed = true)
                    output = r.message
                    status = if (r.success) "Confirmed action complete" else "Confirmed action failed"
                    logTick++
                }) { Text("Allow once") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AgentScreen(
    context: Context,
    engine: AgentEngine,
    settings: AgentSettings,
    task: String,
    onTask: (String) -> Unit,
    status: String,
    onStatus: (String) -> Unit,
    output: String,
    onOutput: (String) -> Unit,
    onSettings: (AgentSettings) -> Unit,
    onCapture: () -> Unit,
    onConfirm: (String) -> Unit,
    onResult: (ActionResult) -> Unit
) {
    var brainMenu by remember { mutableStateOf(false) }
    var selectedPackage by remember(settings.selectedBrain) { mutableStateOf<String?>(null) }
    var bridgeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(settings.selectedBrain) {
        selectedPackage = engine.bridge.installedPackage(settings.selectedBrain)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { brainMenu = true }) { Text("Brain: ${settings.selectedBrain.displayName}") }
                    DropdownMenu(expanded = brainMenu, onDismissRequest = { brainMenu = false }) {
                        BrainProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                trailingIcon = { Text(if (engine.bridge.installedPackage(provider) != null) "●" else "○") },
                                onClick = { onSettings(settings.copy(selectedBrain = provider)); brainMenu = false }
                            )
                        }
                    }
                }
                AssistChip(onClick = {}, label = { Text(if (selectedPackage != null) "Installed" else "Not detected") })
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Agent task", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Write the user's goal. AgentHub builds a structured tool contract around it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(task, onTask, modifier = Modifier.fillMaxWidth().height(150.dp), placeholder = { Text("e.g. Open Settings and find Wi-Fi") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (task.isBlank()) { onStatus("Enter a task first"); return@Button }
                            val id = engine.startTask(task)
                            onStatus("Task $id prepared")
                            onOutput(engine.systemPrompt.take(18000))
                        }, Modifier.weight(1f)) { Text("Prepare Agent") }
                        OutlinedButton(onClick = { engine.stopAll(); onStatus("Emergency stop") }, Modifier.weight(1f)) { Text("STOP") }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Device access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AccessRow("Accessibility", RuntimeChecks.isAccessibilityEnabled(context), "UI inspection + interaction") {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    AccessRow("Screen capture", ScreenCaptureService.active, "Latest screen frame") { onCapture() }
                    AccessRow("Background runtime", AgentForegroundService.active, "Persistent foreground runtime") {
                        val wasActive = AgentForegroundService.active
                        val i = Intent(context, AgentForegroundService::class.java).apply { action = if (wasActive) AgentForegroundService.ACTION_STOP else AgentForegroundService.ACTION_START }
                        if (wasActive) context.stopService(i) else ContextCompat.startForegroundService(context, i)
                        onSettings(settings.copy(backgroundAgent = !wasActive))
                        onStatus(if (wasActive) "Background runtime stopped" else "Background runtime started")
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("AI-app bridge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Visible UI bridge. It does not alter another app's hidden system prompt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.bridgeEnabled, onCheckedChange = { onSettings(settings.copy(bridgeEnabled = it)) })
                    }
                    if (settings.bridgeEnabled) {
                        Button(onClick = {
                            val r = engine.bridge.launch(settings.selectedBrain)
                            r.onSuccess { onStatus("${settings.selectedBrain.displayName} launched") }.onFailure { onStatus(it.message ?: "Cannot launch brain") }
                        }, Modifier.fillMaxWidth()) { Text("1 · Open ${settings.selectedBrain.displayName}") }
                        Button(onClick = {
                            if (task.isBlank()) onStatus("Enter a task first") else {
                                val envelope = engine.bridge.buildEnvelope(settings.selectedBrain, task, engine.systemPrompt)
                                onOutput(envelope)
                                val r = engine.bridge.submitVisibleText(envelope)
                                onResult(r.fold({ ActionResult(true, it) }, { ActionResult(false, it.message ?: "Unable to enter prompt") }))
                            }
                        }, Modifier.fillMaxWidth()) { Text("2 · Insert Agent Prompt") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val r = engine.bridge.clickLikelySendButton()
                                onResult(r.fold({ ActionResult(true, it) }, { ActionResult(false, it.message ?: "Send not found") }))
                            }, Modifier.weight(1f)) { Text("3 · Send") }
                            OutlinedButton(onClick = { onOutput(engine.bridge.readVisibleBrainUi()); onStatus("Visible UI snapshot captured") }, Modifier.weight(1f)) { Text("Read UI") }
                        }
                    }
                }
            }
        }
        item {
            StatusCard(status, output)
        }
        item {
            Text("System prompt preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Text(engine.systemPrompt, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccessRow(label: String, active: Boolean, detail: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (active) "●" else "○", color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(if (active) "ON" else "SET UP", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatusCard(status: String, output: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("STATUS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.labelLarge)
            }
            if (output.isNotBlank()) Text(output, maxLines = 18, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolsScreen(engine: AgentEngine, selected: String?, onSelect: (String?) -> Unit, settings: AgentSettings) {
    var query by remember { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }
    val tools = engine.registry.all().filter { it.name.contains(query, true) || it.description.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Search 50+ tools") })
            AssistChip(onClick = {}, label = { Text("${engine.registry.count()} total") })
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addDialog = true }, Modifier.weight(1f)) { Text("+ Add Tool") }
            OutlinedButton(onClick = { query = "Custom" }, Modifier.weight(1f)) { Text("Show Custom") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tools) { tool ->
                val isSelected = selected == tool.name
                Card(Modifier.fillMaxWidth().clickable { onSelect(if (isSelected) null else tool.name) }, colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tool.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(onClick = {}, label = { Text(tool.category) })
                                if (tool.dangerous) AssistChip(onClick = {}, label = { Text("CONFIRM") })
                                if (tool.category == "Custom") TextButton(onClick = { engine.removeCustomTool(tool.name); onSelect(null) }) { Text("Remove") }
                            }
                        }
                        Text(tool.description, style = MaterialTheme.typography.bodySmall)
                        if (isSelected && tool.inputSchema.isNotEmpty()) Text("Input: ${tool.inputSchema.entries.joinToString { "${it.key}: ${it.value}" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Custom") }
        var inputs by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Add custom tool") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This registers a tool manifest. Custom executors require code/plugin integration; the registry and system prompt will expose the definition.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text("Tool name") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Description") })
                    OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                    OutlinedTextField(inputs, { inputs = it }, label = { Text("Inputs: query:string, limit:integer") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clean = name.trim().replace(Regex("[^A-Za-z0-9_]"), "_")
                    if (clean.isNotBlank() && engine.registry.get(clean) == null) {
                        val schema = inputs.split(',').mapNotNull { part ->
                            val bits = part.trim().split(':', limit = 2)
                            if (bits.firstOrNull()?.isNotBlank() == true) bits[0].trim() to (bits.getOrNull(1)?.trim().orEmpty().ifBlank { "string" }) else null
                        }.toMap()
                        engine.addCustomTool(ToolDefinition(clean, description.trim().ifBlank { "Custom AgentHub tool" }, category.trim().ifBlank { "Custom" }, inputSchema = schema))
                        addDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ActivityScreen(engine: AgentEngine, tick: Int, onClear: () -> Unit) {
    val logs = engine.store.logs()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${logs.size} retained events", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(logs) { log ->
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.time))
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(time, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    Text(log.level, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text(log.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(context: Context, engine: AgentEngine, settings: AgentSettings, onSettings: (AgentSettings) -> Unit, onStatus: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SettingSwitch("Dark mode", "Use the dark AgentHub workspace.", settings.darkMode) { onSettings(settings.copy(darkMode = it)) }
        SettingSwitch("Auto-verify actions", "Encourage a fresh observation after important actions.", settings.autoVerifyActions) { onSettings(settings.copy(autoVerifyActions = it)) }
        SettingSwitch("Confirm dangerous tools", "Require host UI confirmation for gated operations.", settings.requireConfirmationForDangerous) { onSettings(settings.copy(requireConfirmationForDangerous = it)) }
        SettingSwitch("Background runtime", "Keep the visible foreground agent runtime active.", settings.backgroundAgent) {
            val i = Intent(context, AgentForegroundService::class.java).apply { action = if (it) AgentForegroundService.ACTION_START else AgentForegroundService.ACTION_STOP }
            if (it) ContextCompat.startForegroundService(context, i) else context.stopService(i)
            onSettings(settings.copy(backgroundAgent = it))
        }
        Text("Accent", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Accent.entries.forEach { accent -> FilterChip(selected = settings.accent == accent, onClick = { onSettings(settings.copy(accent = accent)) }, label = { Text(accent.label) }) }
        }
        Text("Max agent steps: ${settings.maxSteps}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 50, 100).forEach { n -> FilterChip(selected = settings.maxSteps == n, onClick = { onSettings(settings.copy(maxSteps = n)) }, label = { Text(n.toString()) }) }
        }
        Divider()
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); onStatus("Accessibility settings opened") }, Modifier.fillMaxWidth()) { Text("Open Accessibility Settings") }
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))); onStatus("App info opened") }, Modifier.fillMaxWidth()) { Text("Open Android App Info") }
        Text("AI-app bridge uses visible UI automation only. It cannot and should not rewrite hidden system prompts belonging to third-party apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

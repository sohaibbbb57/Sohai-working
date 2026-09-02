# AgentHub 1.0.0

Native Android/Kotlin agent hub designed for phone-first development and cloud APK builds.

## What this build contains

- Native Kotlin + Jetpack Compose UI.
- Seven selectable AI brain entries: Gemini, ChatGPT, Kimi, Qwen, DeepSeek, Claude, Grok.
- 77 registered tools across Screen, Input, Element, Apps, Device, Files, Web, Agent, Brain Bridge and Debug categories.
- Runtime tool registry with persistent custom tool manifests.
- Long modular AgentHub system prompt generated from the live tool registry.
- Robust JSON action parsing, including fenced JSON and JSON embedded in extra model text.
- Confirmation firewall for destructive/gated tools.
- Emergency stop, pause/resume, max-step guard, task state and local action log.
- AccessibilityService for user-authorized screen/UI tree inspection, semantic element search, clicks, typing, swipes, scrolling, global navigation and IME actions.
- MediaProjection foreground service for user-authorized screen frame capture.
- Foreground runtime service for long-running agent sessions. Android permissions and platform restrictions still apply.
- Visible AI-app bridge: launches the selected AI app, builds an agent envelope and can place it in the currently available editable UI field, then tries to detect a visible Send/Ask/Submit control.
- No OpenRouter key is required.
- GitHub Actions workflow for cloud release APK build.

## Important AI-app bridge boundary

AgentHub does not and cannot legitimately rewrite another app's hidden internal system prompt. The bridge instead inserts a visible, structured AgentHub envelope into the selected AI application's normal composer. Third-party UI selectors can change, so the bridge keeps provider packages as candidates and uses generic accessibility matching rather than pretending to have a stable private API.

## Build in GitHub Actions

1. Create a GitHub repository and push this folder.
2. Open **Actions**.
3. Run **Build AgentHub APK** manually, or push to `main`.
4. Download the `AgentHub-release` artifact from the workflow run.

The project uses AGP 9.2.0 with Gradle 9.4.1 and compileSdk 37. Compose BOM 2026.08.00 is used in the app module.

## First-run checklist

1. Install AgentHub APK.
2. Open AgentHub > Settings/Agent.
3. Enable the AgentHub Accessibility service in Android Settings.
4. Grant MediaProjection screen capture when screen frames are needed.
5. Enable the AI-app bridge only when you want to automate the visible AI application UI.
6. Select a brain and install that official app separately if it is missing.
7. Start a task, prepare the envelope, open the brain, insert the envelope and send it.

## Tool extension

Tools are registered by `ToolRegistry.kt`. Custom definitions added from the UI persist in app-private preferences and are exposed to the system prompt. To give a custom tool real device behavior, implement its executor in `AgentEngine.executeInternal()` or add a dedicated plugin adapter.

## Security model

The host application, not the AI, decides whether a dangerous tool is allowed. No code attempts to bypass Android sandboxing, permission dialogs, Accessibility restrictions or MediaProjection consent.

## Verification note

This environment did not have a usable Gradle binary/network cache, so the release APK could not be compiled locally here. The source tree and workflow are prepared for the cloud build path above; provider UI behavior must also be tested on a real Android device because third-party UIs change.

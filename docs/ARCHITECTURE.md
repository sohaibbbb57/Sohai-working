# Architecture

```text
User
  |
  v
Compose UI
  |
  +--> AgentEngine -----> ToolRegistry
  |          |                 |
  |          +--> SystemPrompt+--> 77 tool definitions
  |          |
  |          +--> Permission/confirmation gate
  |          |
  |          +--> Android executors
  |                 +--> AccessibilityService
  |                 +--> MediaProjection service
  |                 +--> Foreground runtime service
  |
  +--> BrainBridge
          +--> launch selected AI app
          +--> create visible AgentHub envelope
          +--> enter visible text through Accessibility
          +--> detect visible Send/Ask/Submit control
```

The agent loop is intentionally host-controlled: observe -> action -> result -> verify -> next action. The AI model is a brain, not the permission authority.

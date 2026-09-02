# Agent Action Contract

AgentHub accepts exactly one action object at a time.

```json
{"action":"tap","arguments":{"x":"540","y":"1200"}}
```

Semantic UI interaction is preferred:

```json
{"action":"tap_element","arguments":{"query":"Settings"}}
```

Text entry:

```json
{"action":"set_element_text","arguments":{"query":"Search","text":"Minecraft"}}
```

Completion:

```json
{"action":"done","arguments":{"summary":"Task completed."}}
```

Blocked:

```json
{"action":"blocked","arguments":{"reason":"Accessibility service is disabled."}}
```

The host validates the tool name, enabled state, step budget, and confirmation requirements before execution.

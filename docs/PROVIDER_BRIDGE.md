# AI Provider Bridge

There is no OpenRouter dependency in this project.

The seven provider entries are normal Android applications selected as user-facing brains. The bridge uses visible UI automation rather than a private API:

1. Launch the selected app.
2. Build a visible AgentHub task envelope containing the local agent contract and user task.
3. Insert that envelope into the available editable field through the user-authorized AccessibilityService.
4. Detect a likely visible Send/Ask/Submit control.
5. Submit only when the user invokes that step.
6. Read the visible accessibility tree to inspect the app response.

A third-party app can redesign its UI at any time. Provider-specific selectors should therefore be isolated in future adapter classes.

# Tool Authoring

A tool has:

- name
- description
- category
- input schema
- dangerous flag
- confirmation requirement

Add a manifest from the Tools tab to persist a custom definition. The definition automatically appears in the generated system prompt.

To make the custom definition executable, add a matching branch in `AgentEngine.executeInternal()` or create a dedicated executor/service and route to it from the engine.

Keep file operations app-private unless a future feature explicitly uses the Android Storage Access Framework with user-picked URIs.

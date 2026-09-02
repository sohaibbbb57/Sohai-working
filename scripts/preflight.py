from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = [
    root/'build.gradle.kts', root/'settings.gradle.kts', root/'app/build.gradle.kts',
    root/'app/src/main/AndroidManifest.xml', root/'app/src/main/java/com/devran/agenthub/MainActivity.kt',
    root/'app/src/main/java/com/devran/agenthub/agent/AgentEngine.kt',
    root/'app/src/main/java/com/devran/agenthub/automation/AgentAccessibilityService.kt',
    root/'app/src/main/java/com/devran/agenthub/screen/ScreenCaptureService.kt',
    root/'.github/workflows/build.yml'
]
missing = [str(p) for p in required if not p.exists()]
if missing:
    print('MISSING:', '\n'.join(missing)); sys.exit(1)

xml_files = list((root/'app/src/main/res').rglob('*.xml'))
for f in xml_files:
    ET.parse(f)

registry = (root/'app/src/main/java/com/devran/agenthub/agent/ToolRegistry.kt').read_text()
engine = (root/'app/src/main/java/com/devran/agenthub/agent/AgentEngine.kt').read_text()
names = set(re.findall(r't\("([A-Za-z0-9_]+)"', registry))
body = engine.split('return when (call.name)', 1)[1]
when_names = set()
for line in body.splitlines():
    if '->' in line:
        when_names.update(re.findall(r'"([A-Za-z0-9_]+)"', line.split('->',1)[0]))
missing_tools = sorted(names - when_names)
if missing_tools:
    print('TOOLS WITHOUT EXECUTOR:', missing_tools); sys.exit(1)
if len(names) < 50:
    print('TOOL COUNT TOO LOW:', len(names)); sys.exit(1)

text = '\n'.join(p.read_text(errors='ignore') for p in (root/'app/src/main/java').rglob('*.kt'))
if 'openrouter' in text.lower():
    print('Unexpected OpenRouter dependency/reference'); sys.exit(1)

print(f'PASS: {len(names)} registered tools; all have executor branches; {len(xml_files)} XML files parse.')

---
name: implementation-explain
description: Explain the actual implementation after a coding task adds or changes application code, so the user can understand flow, code architecture and manually review it.
---

# Implementation Explain

After completing a coding task that adds or modifies application code, explain the finished implementation using the code as it exists. Keep the explanation proportional to the change and focus on what the user needs to inspect.

Cover:

1. **Implementation summary:** What changed, the problem it solves, and the resulting behavior.
2. **Core components:** The important classes, interfaces, methods, configuration, or modules. For each, describe its responsibility, significant logic or settings, and how it connects to other components. Reference actual file paths and relevant method names. Omit unrelated code and trivial details. For the most important components, go into more depth: clarify metrics and flows in detail.
3. **Execution flow:** Trace the path from the entry point to the result. Include meaningful branches, error handling, and external interactions when present. Use a short text flow or Mermaid diagram if it makes the flow easier to follow.

Base the explanation on the final implementation. Distinguish verified behavior from assumptions, and mention material review risks or limitations when relevant.

## Presentation style

When the user provides or edits a reference section, match its presentation style across the remaining explanation. For architecture and learning notes, prefer a compact outline where each item names a concept and its responsibility with an arrow (for example, `BusinessException → expected business outcome`), then nests examples, fields, and consequences underneath. Keep peer concepts at the same level, use code formatting for identifiers, and split long explanations into reviewable component groups. Do not rewrite user-edited sections unless asked.

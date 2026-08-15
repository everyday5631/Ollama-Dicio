"""The chat prompt format and the tool-calling convention.

Both mirror the Android app (``ChatFormat.kt`` and ``ToolCallParser.kt``) so a given
model behaves the same on phone and desktop.

Small models are unreliable at the OpenAI JSON function-calling schema, so the
convention here is deliberately minimal and forgiving: to call a tool the model emits
**only** a single line of JSON, and otherwise it answers in plain language.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, Literal

Role = Literal["system", "user", "assistant", "tool"]

IM_START = "<|im_start|>"
IM_END = "<|im_end|>"


@dataclass
class Message:
    role: Role
    content: str
    tool_name: str | None = None


@dataclass
class ToolParam:
    name: str
    description: str
    type: str = "string"
    required: bool = True


@dataclass
class ToolDef:
    name: str
    description: str
    params: list[ToolParam] = field(default_factory=list)


@dataclass
class ToolCall:
    name: str
    arguments: dict[str, Any]


def tool_instructions(tools: list[ToolDef]) -> str:
    """The block that teaches the model how to call tools.

    Kept terse and explicit, because small models follow short instructions better.
    """
    if not tools:
        return ""
    lines = [
        "You can call tools to act on the user's computer. ",
        "To call a tool, reply with ONLY one line of JSON and nothing else:\n",
        '{"tool": "<name>", "arguments": {<args>}}\n',
        "If no tool is needed, just answer in plain language. Available tools:\n",
    ]
    out = "".join(lines)
    for tool in tools:
        out += f"- {tool.name}: {tool.description}"
        if tool.params:
            rendered = ", ".join(
                f"{p.name} ({p.type}{'' if p.required else ', optional'}): {p.description}"
                for p in tool.params
            )
            out += f" Arguments: {rendered}"
        out += "\n"
    return out


def build_prompt(messages: list[Message], tools: list[ToolDef]) -> str:
    """Render *messages* into a single ChatML prompt, ending with an open assistant turn."""
    block = tool_instructions(tools)
    injected = not block
    out = []

    for message in messages:
        if message.role == "system" and not injected:
            injected = True
            content = f"{message.content}\n\n{block}"
        elif message.role == "tool":
            content = f"Result of {message.tool_name or 'tool'}: {message.content}"
        else:
            content = message.content
        out.append(f"{IM_START}{message.role}\n{content}{IM_END}\n")

    # if there was no system message to attach the tool block to, prepend one
    if not injected:
        out.insert(0, f"{IM_START}system\n{block}{IM_END}\n")

    out.append(f"{IM_START}assistant\n")
    return "".join(out)


def index_of_complete_json_object(text: str) -> int:
    """Index of the first balanced ``{...}`` block in *text*, or -1 if there is none.

    Used to stop generation early, as soon as a whole tool call has been emitted, rather
    than waiting for the model to run out of tokens. Braces inside strings do not count,
    and backslash escapes are honoured.
    """
    start = -1
    depth = 0
    in_string = False
    escaped = False

    for i, ch in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue

        if ch == '"':
            in_string = True
        elif ch == "{":
            if depth == 0:
                start = i
            depth += 1
        elif ch == "}":
            if depth > 0:
                depth -= 1
                if depth == 0:
                    return start
    return -1


def parse_tool_call(text: str) -> ToolCall | None:
    """Extract a tool call from *text*, or return None if it is a plain answer.

    Tolerates the model wrapping its JSON in prose or a markdown fence, which small
    models do often despite being told not to.
    """
    start = index_of_complete_json_object(text)
    if start < 0:
        return None

    depth = 0
    in_string = False
    escaped = False
    end = -1
    for i in range(start, len(text)):
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        return None

    try:
        obj = json.loads(text[start:end])
    except json.JSONDecodeError:
        return None
    if not isinstance(obj, dict):
        return None

    name = obj.get("tool") or obj.get("name")
    if not isinstance(name, str) or not name:
        return None

    arguments = obj.get("arguments", {})
    if not isinstance(arguments, dict):
        arguments = {}
    return ToolCall(name=name, arguments=arguments)

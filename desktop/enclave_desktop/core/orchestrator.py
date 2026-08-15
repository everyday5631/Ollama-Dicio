"""Turns a user utterance into an answer, dispatching tool calls along the way.

Mirrors ``LlmOrchestrator.kt``: every utterance goes to the model, which either answers
directly or asks for a tool. When a tool is called, its result is fed back so the model
can phrase a final sentence -- with a fallback to the raw tool output if that second
pass fails, so a flaky model never costs the user their answer.
"""

from __future__ import annotations

import logging
from collections.abc import Callable, Iterator
from dataclasses import dataclass, field

from ..tools.builtin import Tool
from .llm import Answer, Call, LlamaEngine, Token
from .protocol import Message

log = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "You are Enclave, a helpful voice assistant running entirely offline on the user's "
    "computer. Answer in one or two short sentences, in the language the user speaks. "
    "Never invent information you do not have."
)

MAX_HISTORY = 8
"""Turns kept in context. Small models degrade quickly with long histories."""


@dataclass
class Turn:
    """One exchange, for display and for context."""

    user: str
    assistant: str = ""
    tool_name: str | None = None


@dataclass
class Orchestrator:
    engine: LlamaEngine
    tools: list[Tool] = field(default_factory=list)
    history: list[Turn] = field(default_factory=list)

    def _tool_by_name(self, name: str) -> Tool | None:
        return next((t for t in self.tools if t.definition.name == name), None)

    def _messages(self, utterance: str) -> list[Message]:
        messages = [Message("system", SYSTEM_PROMPT)]
        for turn in self.history[-MAX_HISTORY:]:
            messages.append(Message("user", turn.user))
            if turn.assistant:
                messages.append(Message("assistant", turn.assistant))
        messages.append(Message("user", utterance))
        return messages

    def ask(self, utterance: str, on_token: Callable[[str], None] | None = None) -> Turn:
        """Answer *utterance*, streaming partial text to *on_token* as it is generated."""
        turn = Turn(user=utterance)
        definitions = [t.definition for t in self.tools]
        messages = self._messages(utterance)

        for event in self.engine.generate(messages, definitions):
            if isinstance(event, Token):
                if on_token:
                    on_token(event.text)
            elif isinstance(event, Answer):
                turn.assistant = event.text
            elif isinstance(event, Call):
                turn.tool_name = event.call.name
                turn.assistant = self._run_tool(event, messages, on_token)

        if not turn.assistant:
            turn.assistant = "Sorry, I did not understand that."
        self.history.append(turn)
        return turn

    def _run_tool(self, event: Call, messages: list[Message],
                  on_token: Callable[[str], None] | None) -> str:
        tool = self._tool_by_name(event.call.name)
        if tool is None:
            log.warning("Model asked for unknown tool %r", event.call.name)
            return f"I do not know how to {event.call.name}."

        try:
            result = tool.execute(event.call.arguments)
        except Exception:
            log.exception("Tool %s failed", event.call.name)
            return f"Something went wrong while running {event.call.name}."

        # give the model the result so it can phrase a natural sentence; if that second
        # pass fails for any reason, the raw tool output is already a usable answer
        follow_up = [*messages, Message("tool", result, tool_name=event.call.name)]
        try:
            phrased = ""
            for follow_event in self.engine.generate(follow_up, []):
                if isinstance(follow_event, Token) and on_token:
                    on_token(follow_event.text)
                elif isinstance(follow_event, Answer):
                    phrased = follow_event.text
            return phrased or result
        except Exception:
            log.exception("Could not phrase tool result; using it verbatim")
            return result


def stream_answer(orchestrator: Orchestrator, utterance: str) -> Iterator[str]:
    """Convenience wrapper yielding tokens, for command-line use."""
    pieces: list[str] = []
    orchestrator.ask(utterance, on_token=pieces.append)
    yield from pieces

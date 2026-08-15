"""Local LLM inference through llama-cpp-python, streaming one token at a time.

This is the desktop equivalent of ``LlamaCppEngine.kt``: the same GGUF files, the same
ChatML prompt, and the same early stop as soon as a complete tool call appears.

``llama_cpp`` is imported lazily so the rest of the app -- and the whole test suite --
works without it. It is the one dependency that may need to compile, and on a machine
with no model downloaded yet it would be dead weight.
"""

from __future__ import annotations

import logging
import threading
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

from .protocol import Message, ToolCall, ToolDef, build_prompt, index_of_complete_json_object, \
    parse_tool_call

log = logging.getLogger(__name__)

N_CTX = 2048
"""Context window. 2048 keeps memory modest; raise for longer conversations."""

N_PREDICT = 384
"""Maximum tokens generated per turn."""


class LlmUnavailable(RuntimeError):
    """Raised when llama-cpp-python is missing or the model cannot be loaded."""


@dataclass
class Token:
    text: str


@dataclass
class Answer:
    text: str


@dataclass
class Call:
    call: ToolCall


Event = Token | Answer | Call


class LlamaEngine:
    """A loaded GGUF model.

    llama.cpp contexts are not thread-safe, so every call into the model is serialised
    behind a lock, exactly as the Android side serialises onto one dedicated thread.
    """

    def __init__(self, model_path: Path, n_ctx: int = N_CTX, n_threads: int | None = None) -> None:
        self.model_path = Path(model_path)
        self.n_ctx = n_ctx
        self.n_threads = n_threads
        self._llama = None
        self._lock = threading.RLock()

    @property
    def loaded(self) -> bool:
        return self._llama is not None

    def load(self) -> None:
        """Load the model into memory. Idempotent, and safe to call from any thread."""
        with self._lock:
            if self._llama is not None:
                return
            try:
                from llama_cpp import Llama
            except ImportError as exc:
                raise LlmUnavailable(
                    "llama-cpp-python is not installed; run "
                    "`pip install llama-cpp-python` to enable local answers"
                ) from exc

            if not self.model_path.exists():
                raise LlmUnavailable(f"Model file not found: {self.model_path}")

            log.info("Loading %s", self.model_path)
            try:
                self._llama = Llama(
                    model_path=str(self.model_path),
                    n_ctx=self.n_ctx,
                    n_threads=self.n_threads,
                    verbose=False,
                )
            except Exception as exc:
                raise LlmUnavailable(f"Could not load {self.model_path}: {exc}") from exc

    def unload(self) -> None:
        """Free the model's memory. The engine can be loaded again afterwards."""
        with self._lock:
            self._llama = None

    def generate(self, messages: list[Message], tools: list[ToolDef]) -> Iterator[Event]:
        """Stream a response.

        Yields :class:`Token` for each piece of text as it arrives, then exactly one
        terminal event: :class:`Call` if the model asked for a tool, otherwise
        :class:`Answer` with the full text.
        """
        self.load()
        prompt = build_prompt(messages, tools)

        with self._lock:
            stream = self._llama(
                prompt,
                max_tokens=N_PREDICT,
                temperature=0.7,
                top_k=40,
                top_p=0.95,
                stop=["<|im_end|>", "<|im_start|>"],
                stream=True,
            )

            accumulated = []
            for chunk in stream:
                piece = chunk["choices"][0]["text"]
                if not piece:
                    continue
                accumulated.append(piece)
                yield Token(piece)

                # stop as soon as a whole tool call has been emitted, rather than
                # letting the model ramble on past it
                if tools and index_of_complete_json_object("".join(accumulated)) >= 0:
                    call = parse_tool_call("".join(accumulated))
                    if call is not None:
                        yield Call(call)
                        return

            text = "".join(accumulated)
            call = parse_tool_call(text) if tools else None
            if call is not None:
                yield Call(call)
            else:
                yield Answer(text.strip())

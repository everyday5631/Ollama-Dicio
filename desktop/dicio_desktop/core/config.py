"""User settings, stored as JSON next to the models.

Deliberately a plain file rather than a registry/dconf abstraction: it is the same on
Linux and Windows, it is inspectable, and it moves with the data directory.
"""

from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass, fields
from pathlib import Path

from .model_store import DEFAULT_LLM, DEFAULT_VOSK_URL, data_dir

log = logging.getLogger(__name__)


@dataclass
class Settings:
    """Everything the user can change."""

    llm_model: str = DEFAULT_LLM
    """An Ollama reference (`tinydolphin`, `qwen2.5:0.5b`) or an https:// URL to a .gguf."""

    vosk_model_url: str = DEFAULT_VOSK_URL
    """Zip of a Vosk model; see https://alphacephei.com/vosk/models for other languages."""

    listen_on_start: bool = False
    """Whether to open the microphone as soon as the app launches."""

    n_threads: int = 0
    """Inference threads; 0 lets llama.cpp decide."""

    @classmethod
    def path(cls) -> Path:
        return data_dir() / "settings.json"

    @classmethod
    def load(cls) -> "Settings":
        path = cls.path()
        if not path.exists():
            return cls()
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            log.warning("Could not read %s; using defaults", path, exc_info=True)
            return cls()
        # ignore unknown keys so a settings file from a newer version still loads
        known = {f.name for f in fields(cls)}
        return cls(**{k: v for k, v in raw.items() if k in known})

    def save(self) -> None:
        path = self.path()
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(asdict(self), indent=2), encoding="utf-8")
        except OSError:
            log.warning("Could not write %s", path, exc_info=True)

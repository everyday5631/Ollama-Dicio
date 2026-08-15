"""Download and cache the models the assistant needs, entirely offline once fetched.

Two kinds of model are managed here:

* the **Vosk STT model**, a zip downloaded from alphacephei.com and unpacked once, and
* the **GGUF LLM**, resolved through :mod:`.ollama_registry` when given an Ollama
  reference, or downloaded directly when given an ``https://`` URL.

Both downloads are resumable: progress is written to a ``.part`` file and continued
with a ``Range`` request, so an interrupted 600 MB pull does not start over. The final
rename is only done once the bytes are all present, so a partial file is never mistaken
for a complete one.
"""

from __future__ import annotations

import logging
import os
import shutil
import urllib.request
import zipfile
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from . import ollama_registry

log = logging.getLogger(__name__)

ProgressCallback = Callable[[int, int], None]
"""Called with ``(downloaded_bytes, total_bytes)``; ``total`` is 0 when unknown."""

CancelCheck = Callable[[], bool]
"""Polled between chunks; returning True aborts the download with :class:`DownloadCancelled`."""


class DownloadCancelled(Exception):
    """Raised when a :data:`CancelCheck` asks for the download to stop.

    The ``.part`` file is deliberately left on disk so the next attempt resumes from
    where this one stopped.
    """

_CHUNK = 1 << 18  # 256 KiB -- small enough that a cancel is noticed promptly

DEFAULT_LLM = "qwen2.5:0.5b"
"""Matches the Android app's default: ~380 MB, multilingual, good tool-following."""

DEFAULT_VOSK_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
"""Small (~40 MB) English model. See https://alphacephei.com/vosk/models for others."""


def data_dir() -> Path:
    """Per-user data directory, following each platform's convention."""
    if os.name == "nt":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    else:
        base = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    path = base / "dicio-desktop"
    path.mkdir(parents=True, exist_ok=True)
    return path


@dataclass
class DownloadResult:
    path: Path
    resumed: bool


def download(url: str, destination: Path, progress: ProgressCallback | None = None,
             timeout: float = 20.0, cancel: CancelCheck | None = None) -> DownloadResult:
    """Download *url* to *destination*, resuming a previous partial download if present.

    *cancel* is polled between chunks so a long download can be abandoned promptly when
    the application is closing, instead of holding shutdown for hundreds of megabytes.
    """
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + ".part")

    already = partial.stat().st_size if partial.exists() else 0
    headers = {"Range": f"bytes={already}-"} if already else {}
    request = urllib.request.Request(url, headers=headers)

    with urllib.request.urlopen(request, timeout=timeout) as response:
        # a server that ignores our Range header replies 200 and restarts from zero,
        # in which case the partial file must be discarded rather than appended to
        resumed = response.status == 206 and already > 0
        if not resumed:
            already = 0

        remaining = response.headers.get("Content-Length")
        total = (int(remaining) + already) if remaining else 0

        mode = "ab" if resumed else "wb"
        with open(partial, mode) as handle:
            downloaded = already
            if progress:
                progress(downloaded, total)
            while chunk := response.read(_CHUNK):
                if cancel is not None and cancel():
                    # leave the .part file in place so the next attempt resumes
                    raise DownloadCancelled(f"Download of {url} cancelled")
                handle.write(chunk)
                downloaded += len(chunk)
                if progress:
                    progress(downloaded, total)

    # only now is the file complete enough to claim the real name
    partial.replace(destination)
    return DownloadResult(path=destination, resumed=resumed)


class ModelStore:
    """Locates, downloads and unpacks the STT and LLM models."""

    def __init__(self, root: Path | None = None) -> None:
        self.root = root or data_dir()
        self.root.mkdir(parents=True, exist_ok=True)

    # ----- Vosk STT -----

    @property
    def vosk_dir(self) -> Path:
        return self.root / "vosk-model"

    def has_vosk(self) -> bool:
        # Vosk needs a directory containing at least the acoustic model
        return (self.vosk_dir / "am").is_dir() or (self.vosk_dir / "am" / "final.mdl").exists()

    def ensure_vosk(self, url: str = DEFAULT_VOSK_URL,
                    progress: ProgressCallback | None = None,
                    cancel: CancelCheck | None = None) -> Path:
        if self.has_vosk():
            return self.vosk_dir

        archive = self.root / "vosk-model.zip"
        log.info("Downloading Vosk model from %s", url)
        download(url, archive, progress, cancel=cancel)

        staging = self.root / "vosk-unpack"
        if staging.exists():
            shutil.rmtree(staging)
        with zipfile.ZipFile(archive) as zf:
            zf.extractall(staging)

        # the archive contains a single top-level directory; hoist it to a stable name
        entries = [p for p in staging.iterdir() if p.is_dir()]
        source = entries[0] if len(entries) == 1 else staging
        if self.vosk_dir.exists():
            shutil.rmtree(self.vosk_dir)
        source.replace(self.vosk_dir)

        shutil.rmtree(staging, ignore_errors=True)
        archive.unlink(missing_ok=True)
        return self.vosk_dir

    # ----- GGUF LLM -----

    @property
    def llm_path(self) -> Path:
        return self.root / "llm-model.gguf"

    @property
    def _llm_marker(self) -> Path:
        return self.root / "llm-model.ref"

    def has_llm(self, model: str) -> bool:
        """Whether the on-disk GGUF corresponds to *model*."""
        if not self.llm_path.exists():
            return False
        try:
            return self._llm_marker.read_text(encoding="utf-8").strip() == model.strip()
        except OSError:
            return False

    def ensure_llm(self, model: str = DEFAULT_LLM,
                   progress: ProgressCallback | None = None,
                   cancel: CancelCheck | None = None) -> Path:
        """Download *model* if the file on disk is missing or for a different model.

        *model* is either an Ollama reference (``tinydolphin``, ``qwen2.5:0.5b``) or a
        direct ``https://`` URL to a ``.gguf``.
        """
        if self.has_llm(model):
            return self.llm_path

        if ollama_registry.is_reference(model):
            resolved = ollama_registry.resolve(model)
            url = resolved.blob_url
            log.info("Resolved %r to %s (%d bytes)", model, url, resolved.size_bytes)
            _warn_if_template_unsupported(model, resolved.template)
        else:
            url = model

        download(url, self.llm_path, progress, cancel=cancel)
        self._llm_marker.write_text(model.strip(), encoding="utf-8")
        return self.llm_path


def _warn_if_template_unsupported(model: str, template: str | None) -> None:
    """Warn when a model does not expect the ChatML prompt that :mod:`.chat_format` emits.

    The download still proceeds -- the model will load and answer, just with a prompt
    shape it was not trained on -- so this only warns.
    """
    if template and "<|im_start|>" not in template:
        log.warning(
            "Model %r declares a non-ChatML prompt template; chat_format emits ChatML, "
            "so answer quality may suffer. Declared template:\n%s", model, template,
        )

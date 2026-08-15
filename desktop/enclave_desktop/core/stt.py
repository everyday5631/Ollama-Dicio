"""Streaming speech-to-text with Vosk, fed from the microphone via sounddevice.

Vosk runs fully offline against a local model directory. Audio is captured on
sounddevice's callback thread and pushed through a queue to a worker thread, so neither
the audio device nor the GUI is ever blocked by recognition work.

The recogniser emits two kinds of result:

* **partial** -- the current best guess, updated many times per second, for live display
* **final** -- emitted when Vosk detects an utterance boundary

Callbacks are invoked from the worker thread; the GUI layer is responsible for hopping
back to the UI thread (:mod:`.gui.main_window` does this with a Qt signal).
"""

from __future__ import annotations

import json
import logging
import queue
import threading
from collections.abc import Callable
from pathlib import Path

log = logging.getLogger(__name__)

SAMPLE_RATE = 16000
"""Vosk's small models are trained at 16 kHz; resampling elsewhere would only add error."""

_BLOCK = 4000


class SttUnavailable(RuntimeError):
    """Raised when Vosk or an audio input device is not usable."""


class VoskListener:
    """Captures microphone audio and streams it through Vosk.

    :param model_dir: directory of an unpacked Vosk model
    :param on_partial: called with the in-progress transcript
    :param on_final: called with a completed utterance (never called with empty text)
    """

    def __init__(
        self,
        model_dir: Path,
        on_partial: Callable[[str], None] | None = None,
        on_final: Callable[[str], None] | None = None,
        device: int | str | None = None,
    ) -> None:
        self.model_dir = Path(model_dir)
        self.on_partial = on_partial
        self.on_final = on_final
        self.device = device

        self._queue: queue.Queue[bytes | None] = queue.Queue()
        self._thread: threading.Thread | None = None
        self._stream = None
        self._running = threading.Event()

    @property
    def running(self) -> bool:
        return self._running.is_set()

    def start(self) -> None:
        """Open the microphone and begin recognising. Idempotent."""
        if self.running:
            return

        try:
            import sounddevice as sd
            from vosk import KaldiRecognizer, Model, SetLogLevel
        except ImportError as exc:  # pragma: no cover - depends on the environment
            raise SttUnavailable(f"Vosk or sounddevice is not installed: {exc}") from exc

        if not self.model_dir.is_dir():
            raise SttUnavailable(f"Vosk model directory not found: {self.model_dir}")

        SetLogLevel(-1)  # Vosk is extremely chatty on stdout otherwise
        model = Model(str(self.model_dir))
        recognizer = KaldiRecognizer(model, SAMPLE_RATE)
        recognizer.SetWords(False)

        self._running.set()
        self._thread = threading.Thread(
            target=self._recognise, args=(recognizer,), name="vosk-recogniser", daemon=True,
        )
        self._thread.start()

        def callback(indata, _frames, _time, status) -> None:
            if status:
                log.debug("Audio input status: %s", status)
            # bytes(), because the underlying buffer is reused by the next callback
            self._queue.put(bytes(indata))

        try:
            self._stream = sd.RawInputStream(
                samplerate=SAMPLE_RATE,
                blocksize=_BLOCK,
                device=self.device,
                dtype="int16",
                channels=1,
                callback=callback,
            )
            self._stream.start()
        except Exception as exc:
            self.stop()
            raise SttUnavailable(f"Could not open an audio input device: {exc}") from exc

        log.info("Listening at %d Hz using %s", SAMPLE_RATE, self.model_dir)

    def stop(self) -> None:
        """Stop recognising and release the microphone. Safe to call when not running."""
        self._running.clear()
        if self._stream is not None:
            try:
                self._stream.stop()
                self._stream.close()
            except Exception:
                log.debug("Error closing audio stream", exc_info=True)
            self._stream = None

        self._queue.put(None)  # wake the worker so it can exit
        if self._thread is not None:
            self._thread.join(timeout=2.0)
            self._thread = None

    def _recognise(self, recognizer) -> None:
        while self._running.is_set():
            chunk = self._queue.get()
            if chunk is None:
                break
            try:
                if recognizer.AcceptWaveform(chunk):
                    text = json.loads(recognizer.Result()).get("text", "").strip()
                    if text and self.on_final:
                        self.on_final(text)
                elif self.on_partial:
                    partial = json.loads(recognizer.PartialResult()).get("partial", "").strip()
                    if partial:
                        self.on_partial(partial)
            except Exception:
                log.exception("Recognition error; continuing")

        # flush whatever was mid-utterance when we were asked to stop
        try:
            text = json.loads(recognizer.FinalResult()).get("text", "").strip()
            if text and self.on_final:
                self.on_final(text)
        except Exception:
            log.debug("Error flushing final result", exc_info=True)

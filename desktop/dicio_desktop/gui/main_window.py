"""The main window: a transcript, a text box, and a listen button.

Threading is the whole story here. Vosk calls back from its recogniser thread and the
LLM generates on a worker thread, but Qt widgets may only be touched from the GUI
thread. Every cross-thread hand-off therefore goes through a Qt signal, which marshals
onto the GUI thread automatically -- there is no direct widget access anywhere off the
main thread.
"""

from __future__ import annotations

import logging
from pathlib import Path

from PySide6.QtCore import Qt, QThread, Signal
from PySide6.QtGui import QFont, QTextCursor
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QDialog, QDialogButtonBox, QFormLayout, QHBoxLayout, QLabel,
    QLineEdit, QMainWindow, QMessageBox, QProgressBar, QPushButton, QTextEdit, QVBoxLayout,
    QWidget,
)

from ..core.config import Settings
from ..core.llm import LlamaEngine, LlmUnavailable
from ..core.model_store import DownloadCancelled, ModelStore
from ..core.orchestrator import Orchestrator
from ..core.stt import SttUnavailable, VoskListener
from ..tools.builtin import default_tools

log = logging.getLogger(__name__)


class SetupWorker(QThread):
    """Downloads the models and loads the LLM, off the GUI thread."""

    progress = Signal(str, int, int)   # label, current, total
    finished_ok = Signal(object)       # LlamaEngine
    failed = Signal(str)

    def __init__(self, settings: Settings, store: ModelStore) -> None:
        super().__init__()
        self.settings = settings
        self.store = store

    def run(self) -> None:
        cancelled = self.isInterruptionRequested
        try:
            if not self.store.has_vosk():
                self.progress.emit("Downloading speech model", 0, 0)
                self.store.ensure_vosk(
                    self.settings.vosk_model_url,
                    lambda c, t: self.progress.emit("Downloading speech model", c, t),
                    cancel=cancelled,
                )

            if not self.store.has_llm(self.settings.llm_model):
                self.progress.emit("Downloading language model", 0, 0)
            self.store.ensure_llm(
                self.settings.llm_model,
                lambda c, t: self.progress.emit("Downloading language model", c, t),
                cancel=cancelled,
            )

            if cancelled():
                return
            self.progress.emit("Loading language model", 0, 0)
            engine = LlamaEngine(
                self.store.llm_path,
                n_threads=self.settings.n_threads or None,
            )
            engine.load()
            if cancelled():
                engine.unload()
                return
            self.finished_ok.emit(engine)
        except DownloadCancelled:
            log.info("Setup cancelled while downloading; partial file kept for resume")
        except LlmUnavailable as exc:
            # the app is still useful for speech-to-text without an LLM
            self.failed.emit(str(exc))
        except Exception as exc:
            log.exception("Setup failed")
            self.failed.emit(str(exc))


class AskWorker(QThread):
    """Runs one turn through the orchestrator."""

    token = Signal(str)
    done = Signal(str, object)  # answer, tool name or None
    failed = Signal(str)

    def __init__(self, orchestrator: Orchestrator, utterance: str) -> None:
        super().__init__()
        self.orchestrator = orchestrator
        self.utterance = utterance

    def run(self) -> None:
        try:
            turn = self.orchestrator.ask(self.utterance, on_token=self.token.emit)
            self.done.emit(turn.assistant, turn.tool_name)
        except Exception as exc:
            log.exception("Generation failed")
            self.failed.emit(str(exc))


class SettingsDialog(QDialog):
    def __init__(self, settings: Settings, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setWindowTitle("Settings")
        self.settings = settings

        self.llm_edit = QLineEdit(settings.llm_model)
        self.llm_edit.setPlaceholderText("qwen2.5:0.5b")
        self.vosk_edit = QLineEdit(settings.vosk_model_url)
        self.listen_check = QCheckBox()
        self.listen_check.setChecked(settings.listen_on_start)

        form = QFormLayout()
        form.addRow("Ollama model or GGUF URL", self.llm_edit)
        form.addRow(QLabel("<i>e.g. tinydolphin, qwen2.5:0.5b, or an https:// .gguf link</i>"))
        form.addRow("Vosk model zip URL", self.vosk_edit)
        form.addRow("Listen on start", self.listen_check)

        buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(buttons)

    def apply_to(self, settings: Settings) -> bool:
        """Copy the edited values back. Returns True if a model change needs a restart."""
        changed = (
            settings.llm_model != self.llm_edit.text().strip()
            or settings.vosk_model_url != self.vosk_edit.text().strip()
        )
        settings.llm_model = self.llm_edit.text().strip() or settings.llm_model
        settings.vosk_model_url = self.vosk_edit.text().strip() or settings.vosk_model_url
        settings.listen_on_start = self.listen_check.isChecked()
        settings.save()
        return changed


class MainWindow(QMainWindow):
    # Vosk calls back from its own thread; these signals marshal onto the GUI thread
    partial_ready = Signal(str)
    final_ready = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Dicio Desktop")
        self.resize(720, 560)

        self.settings = Settings.load()
        self.store = ModelStore()
        self.engine: LlamaEngine | None = None
        self.orchestrator: Orchestrator | None = None
        self.listener: VoskListener | None = None
        self._ask_worker: AskWorker | None = None
        self._setup_worker: SetupWorker | None = None
        self._streaming = False

        self._build_ui()

        self.partial_ready.connect(self._on_partial)
        self.final_ready.connect(self._on_final)

        self._start_setup()

    # ----- UI -----

    def _build_ui(self) -> None:
        self.transcript = QTextEdit(readOnly=True)
        self.transcript.setFont(QFont("monospace", 10))

        self.partial_label = QLabel("")
        self.partial_label.setStyleSheet("color: palette(mid); font-style: italic;")
        self.partial_label.setWordWrap(True)

        self.input = QLineEdit()
        self.input.setPlaceholderText("Type a request, or press Listen and speak…")
        self.input.returnPressed.connect(self._on_submit)

        self.send_button = QPushButton("Send")
        self.send_button.clicked.connect(self._on_submit)

        self.listen_button = QPushButton("Listen")
        self.listen_button.setCheckable(True)
        self.listen_button.toggled.connect(self._on_listen_toggled)
        self.listen_button.setEnabled(False)

        self.settings_button = QPushButton("Settings")
        self.settings_button.clicked.connect(self._on_settings)

        self.progress = QProgressBar()
        self.progress.setVisible(False)

        self.status = QLabel("Starting…")

        row = QHBoxLayout()
        row.addWidget(self.input, 1)
        row.addWidget(self.send_button)
        row.addWidget(self.listen_button)
        row.addWidget(self.settings_button)

        layout = QVBoxLayout()
        layout.addWidget(self.transcript, 1)
        layout.addWidget(self.partial_label)
        layout.addLayout(row)
        layout.addWidget(self.progress)
        layout.addWidget(self.status)

        central = QWidget()
        central.setLayout(layout)
        self.setCentralWidget(central)

    def _append(self, who: str, text: str) -> None:
        self.transcript.moveCursor(QTextCursor.End)
        self.transcript.insertPlainText(f"{who}: {text}\n")
        self.transcript.moveCursor(QTextCursor.End)

    # ----- setup -----

    def _start_setup(self) -> None:
        self.progress.setVisible(True)
        self.progress.setRange(0, 0)
        self._setup_worker = SetupWorker(self.settings, self.store)
        self._setup_worker.progress.connect(self._on_setup_progress)
        self._setup_worker.finished_ok.connect(self._on_setup_done)
        self._setup_worker.failed.connect(self._on_setup_failed)
        self._setup_worker.start()

    def _on_setup_progress(self, label: str, current: int, total: int) -> None:
        self.status.setText(label if not total else
                            f"{label} — {current * 100 // total}%")
        if total:
            self.progress.setRange(0, total)
            self.progress.setValue(current)
        else:
            self.progress.setRange(0, 0)

    def _on_setup_done(self, engine: LlamaEngine) -> None:
        self.engine = engine
        self.orchestrator = Orchestrator(engine=engine, tools=default_tools())
        self.progress.setVisible(False)
        self.status.setText("Ready — everything runs offline on this machine.")
        self.listen_button.setEnabled(self.store.has_vosk())
        if self.settings.listen_on_start and self.store.has_vosk():
            self.listen_button.setChecked(True)

    def _on_setup_failed(self, message: str) -> None:
        self.progress.setVisible(False)
        # speech-to-text still works without the LLM, so stay usable rather than dying
        self.status.setText(f"Language model unavailable: {message}")
        self.listen_button.setEnabled(self.store.has_vosk())

    # ----- speech -----

    def _on_listen_toggled(self, checked: bool) -> None:
        if checked:
            try:
                self.listener = VoskListener(
                    self.store.vosk_dir,
                    on_partial=self.partial_ready.emit,
                    on_final=self.final_ready.emit,
                )
                self.listener.start()
                self.listen_button.setText("Stop")
                self.status.setText("Listening…")
            except SttUnavailable as exc:
                self.listen_button.setChecked(False)
                QMessageBox.warning(self, "Cannot listen", str(exc))
        else:
            if self.listener is not None:
                self.listener.stop()
                self.listener = None
            self.listen_button.setText("Listen")
            self.partial_label.setText("")
            self.status.setText("Ready")

    def _on_partial(self, text: str) -> None:
        self.partial_label.setText(text)

    def _on_final(self, text: str) -> None:
        self.partial_label.setText("")
        self._submit(text)

    # ----- asking -----

    def _on_submit(self) -> None:
        text = self.input.text().strip()
        if text:
            self.input.clear()
            self._submit(text)

    def _submit(self, utterance: str) -> None:
        if self.orchestrator is None:
            self._append("!", "The language model is not loaded.")
            return
        if self._ask_worker is not None and self._ask_worker.isRunning():
            return  # one turn at a time; llama.cpp is single-context anyway

        self._append("You", utterance)
        self.status.setText("Thinking…")
        self._streaming = False

        self._ask_worker = AskWorker(self.orchestrator, utterance)
        self._ask_worker.token.connect(self._on_token)
        self._ask_worker.done.connect(self._on_answer)
        self._ask_worker.failed.connect(self._on_ask_failed)
        self._ask_worker.start()

    def _on_token(self, piece: str) -> None:
        if not self._streaming:
            self._streaming = True
            self.transcript.moveCursor(QTextCursor.End)
            self.transcript.insertPlainText("Dicio: ")
        self.transcript.moveCursor(QTextCursor.End)
        self.transcript.insertPlainText(piece)

    def _on_answer(self, answer: str, tool_name: object) -> None:
        if self._streaming:
            self.transcript.insertPlainText("\n")
        else:
            self._append("Dicio", answer)
        self._streaming = False
        self.status.setText(f"Ready (used {tool_name})" if tool_name else "Ready")

    def _on_ask_failed(self, message: str) -> None:
        self._streaming = False
        self._append("!", message)
        self.status.setText("Ready")

    # ----- settings -----

    def _on_settings(self) -> None:
        dialog = SettingsDialog(self.settings, self)
        if dialog.exec() == QDialog.Accepted and dialog.apply_to(self.settings):
            QMessageBox.information(
                self, "Model changed",
                "The new model will be downloaded and loaded when you restart Dicio.",
            )

    def closeEvent(self, event) -> None:
        # A QThread destroyed while still running aborts the process, so every worker
        # must be asked to stop and then waited for before the window goes away.
        # Downloads poll isInterruptionRequested between chunks, so this returns
        # promptly even mid-download; generation is bounded by N_PREDICT tokens.
        # Not every blocking point is interruptible -- urlopen's connect phase and
        # zipfile.extractall are not -- so a cooperative stop is attempted first and
        # terminate() is the backstop. Terminating a download is safe here: the .part
        # file stays on disk and the next run resumes from it.
        for worker in (self._setup_worker, self._ask_worker):
            if worker is None or not worker.isRunning():
                continue
            worker.requestInterruption()
            if worker.wait(5_000):
                continue
            log.warning("Worker %s did not stop cooperatively; terminating", worker)
            worker.terminate()
            if not worker.wait(3_000):
                log.error("Worker %s could not be terminated", worker)

        if self.listener is not None:
            self.listener.stop()
            self.listener = None
        if self.engine is not None:
            self.engine.unload()
        super().closeEvent(event)


def run() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    app = QApplication.instance() or QApplication([])
    app.setApplicationName("Dicio Desktop")
    window = MainWindow()
    window.show()
    return app.exec()

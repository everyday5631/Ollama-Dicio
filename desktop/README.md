# Dicio Desktop

A **fully offline** voice assistant for **Linux and Windows**, sharing the design — and
the models — of the Android app in this repository.

Nothing leaves the machine. The only network access is the one-time model download.

> **Status: walking skeleton.** The pipeline runs end to end — microphone → speech
> recognition → local LLM → tool call → answer — with four tools. It is a foundation to
> build skills on, not a replacement for the Android app's ~15 skills.

## Why this is not a port

The Android app's skills, its sentence-compiler grammars and `dicio-numbers` are all
Kotlin/Java, so **none of that code can be reused outside the JVM.** What carries over is
the *design*, and two things that genuinely are shared:

* the **same GGUF models**, pulled from the same Ollama registry, and
* the **same tool-calling protocol and ChatML prompt**, so a given model behaves the
  same on the phone and on the desktop.

The LLM-orchestrator architecture is what makes this practical: the model interprets the
request, so no per-language grammar files are needed.

## Install

```bash
cd desktop
python -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python -m dicio_desktop
```

Requires Python 3.10+. On first launch it downloads a Vosk speech model (~40 MB) and a
language model (~380 MB by default); both are cached under the platform data directory
and both downloads resume if interrupted.

`llama-cpp-python` ships prebuilt wheels for common platforms but may fall back to
compiling llama.cpp, which needs a C++ toolchain and CMake. **The app runs without it** —
speech recognition still works, and the status bar says the language model is
unavailable.

On Linux you also need PortAudio for microphone capture
(`sudo dnf install portaudio` / `sudo apt install libportaudio2`).

## Choosing a model

Settings takes an **Ollama reference** or a direct `https://` link to a `.gguf`:

| Reference | Size | Notes |
|-----------|------|-------|
| `qwen2.5:0.5b` | ~380 MB | Default. Multilingual, best tool-following |
| `tinydolphin` | ~610 MB | 1.1B, English-centric, fast |
| `qwen2.5:1.5b` | ~1.0 GB | Best quality; needs more RAM |

`ollama` itself does **not** need to be installed. `core/ollama_registry.py` talks to the
registry directly, the same way `OllamaRegistry.kt` does on Android: fetch the manifest,
take the layer with media type `application/vnd.ollama.image.model`, and download it —
that blob *is* the GGUF file, byte for byte.

For a language other than English, point the Vosk model URL at another model from
<https://alphacephei.com/vosk/models>.

## Layout

```
dicio_desktop/
  core/
    ollama_registry.py   resolve `tinydolphin` -> a GGUF blob URL
    model_store.py       resumable downloads, unpacking, data dir
    stt.py               Vosk streaming recognition from the microphone
    llm.py               llama-cpp-python inference, streamed token by token
    protocol.py          ChatML prompt + tool-call parsing (mirrors the Android app)
    orchestrator.py      utterance -> answer, dispatching tools
    config.py            settings.json
  tools/builtin.py       current_time, calculate, open_application, system_info
  gui/main_window.py     Qt window, and all the thread marshalling
tests/test_core.py       24 tests, no microphone or model needed
```

## Adding a tool

Subclass `Tool`, declare a `ToolDef`, and add it to `default_tools()`. The description
and parameters are what the model sees, so write them for a reader, not a schema.

```python
class VolumeTool(Tool):
    definition = ToolDef(
        name="set_volume",
        description="Set the system output volume.",
        params=[ToolParam("percent", "volume from 0 to 100", type="number")],
    )

    def execute(self, arguments: dict) -> str:
        ...
        return "Volume set to 40%."
```

## Threading

The rule: **Qt widgets are touched only on the GUI thread.** Vosk calls back from its
recogniser thread and the LLM generates on a worker thread, so every hand-off goes
through a Qt signal, which marshals automatically.

Shutdown deserves a note, because getting it wrong crashes the process rather than
raising: a `QThread` destroyed while still running aborts. `closeEvent` therefore asks
each worker to stop, waits, and falls back to `terminate()` — `urlopen`'s connect phase
and `zipfile.extractall` are not interruptible, so a cooperative stop alone is not
enough. Terminating a download is safe: the `.part` file survives and the next run
resumes from it.

## Tests

```bash
python -m unittest discover -s tests -v
```

Covers reference parsing, tool-call extraction, prompt construction and the calculator's
sandbox. Anything needing a microphone or a loaded model is out of scope for them.

## Packaging

```bash
pip install pyinstaller
pyinstaller dicio-desktop.spec
```

Produces `dist/dicio-desktop/` — an `.exe` on Windows, a directory you can wrap in an
AppImage on Linux. Models are **not** bundled; they download on first run, which keeps
the artifact around 80 MB instead of half a gigabyte.

## Known limitations

* **Inference is unverified.** `llama-cpp-python` was not installed in the environment
  this was written in, so the STT → LLM → tool path has not been run end to end against
  a real model. Everything else — registry resolution, resumable download, prompt
  construction, tool-call parsing, tool execution, GUI lifecycle — is tested.
* No wake word. Press **Listen**, or enable "listen on start".
* No text-to-speech; answers are shown, not spoken.
* Four tools, against the Android app's ~15 skills.

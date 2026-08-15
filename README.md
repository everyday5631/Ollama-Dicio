# Enclave

Enclave is a *free and open source* **voice assistant** for **Android**, and now for **Linux and
Windows** too. It listens, thinks and answers **entirely on your device** — there is no account, no
cloud and no telemetry, and you decide which skills may reach the internet at all.

What makes it different from the assistant it grew out of is a **local LLM orchestrator**: a small
quantized model runs on the phone itself and decides what to do with each request, either answering
directly or calling one of the built-in skills.

<p align="center">
    <img width="96" alt="Enclave logo" src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png">
</p>

> **Enclave is a fork of [Dicio](https://github.com/Stypox/dicio-android)**, which is where the
> skills, the speech pipeline and most of the multilanguage support come from. If you are looking
> for the original — with its F-Droid and Play Store releases — go there. This fork adds the
> on-device LLM, the Ollama model manager, a desktop version, and a new name and design.

## Screenshots

Screenshots of the current Enclave interface are not in this repository yet — the ones that were
here showed the pre-rebrand UI, and taking accurate replacements needs a device or emulator running
this build. See [`docs/enclave-design.md`](docs/enclave-design.md) for what each screen is meant to
look like, and open any `@Preview` under `app/src/main/kotlin/org/stypox/dicio/ui/enclave/` in
Android Studio to see them rendered.

## On-device LLM

A small quantized model runs locally and acts as the orchestrator: every request goes to it, and it
either answers or calls a skill through a lightweight tool-calling protocol. See
[`docs/local-llm.md`](docs/local-llm.md) for the architecture.

Models are pulled **straight from the Ollama registry** — no `ollama` install required. Type a
reference into **Settings → Local AI**:

| Reference | Size | Notes |
|-----------|------|-------|
| `gemma3:270m` | ~290 MB | Default. The smallest, which matters on a phone |
| `qwen2.5:0.5b` | ~380 MB | Multilingual, best tool-following of the three |
| `tinydolphin` | ~610 MB | 1.1B, English-centric, fast |

A direct `https://` link to a `.gguf` works too.

### Memory

Enclave keeps an offline, human-readable memory in a Markdown file on the device. It only writes to
it when you **explicitly ask** — "remember that my favourite dish is a greek salad" — and never for
something you merely mentioned in passing. The file is readable *and editable* in settings, and
nothing in it leaves the phone.

## Skills

Enclave answers questions about:
- **search**: looks up information on **DuckDuckGo** — _Search for Enclave_
- **weather**: collects weather information from **OpenWeatherMap** — _What's the weather like?_
- **lyrics**: shows **Genius** lyrics for songs — _What's the song that goes we will we will rock you?_
- **open**: opens an app on your device — _Open NewPipe_
- **calculator**: evaluates basic calculations — _What is four thousand and two times three minus a million divided by three hundred?_
- **telephone**: view and call contacts — _Call Tom_
- **timer**: set, query and cancel timers — _Set a timer for five minutes_
- **current time**: query current time — _What time is it?_
- **navigation**: opens the navigation app at the requested position — _Take me to New York, fifteenth avenue_
- **jokes**: tells you a joke — _Tell me a joke_
- **media**: play, pause, previous, next song
- **translation**: translate from/to any language with **Lingva** — _How do I say Football in German?_
- **wake word control**: turn on/off the wakeword — _Stop listening_
- **notifications**: reads all notifications currently in the status bar — _What are my notifications?_
- **flashlight**: turn on/off the phone flashlight — _Turn on the flashlight_

Each skill's internet access is yours to grant; the ones that need a network are marked as such in
the skills list.

## Speech to text

Enclave uses [Vosk](https://github.com/alphacep/vosk-api/) as its speech to text (`STT`) engine. In
order to be able to run on every phone small models are employed, weighing `~50MB`. The download
from [here](https://alphacephei.com/vosk/models) starts automatically whenever needed, so the app
language can be changed seamlessly.

Available in Czech, Dutch, English, French, German, Greek, Italian, Polish, Russian, Slovenian,
Spanish, Swedish, Turkish and Ukrainian.

## Wake Word

Enclave uses [OpenWakeWord](https://github.com/dscripka/openWakeWord) for wake word support, and by
default it listens for the _Hey Enclave_ keyword. If you would like to use a different keyword, you
can download other `.tflite` models from
[Open Wake Word](https://github.com/dscripka/openWakeWord/releases/tag/v0.5.1) or from
[this collection](https://github.com/fwartner/home-assistant-wakewords-collection). Then head to
`Settings > Input and output methods > Import custom wake word` and select the `.tflite` model you
downloaded. Alternatively, you can train a wake word model for a keyword of your choice by following
this [Jupiter Notebook](https://github.com/dscripka/openWakeWord/blob/main/notebooks/automatic_model_training.ipynb)
with this [configuration](meta/openwakeword_training_config.yml).

## Desktop

A Python/Qt version for **Linux and Windows** lives in [`desktop/`](desktop/). It shares Enclave's
models and its tool-calling protocol, but not its code — the Android skills and grammars are all
JVM, so the desktop version is a reimplementation of the design rather than a port. See
[`desktop/README.md`](desktop/README.md).

Prebuilt archives are attached to
[releases](https://github.com/everyday5631/Ollama-Dicio/releases); running from source is four
commands.

## Building

```bash
git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
./gradlew assembleDebug
```

The native build has two traps worth knowing about — 16 KB page alignment and an API-23 requirement
from `posix_madvise` — both already handled and both documented in
[`docs/local-llm.md`](docs/local-llm.md).

## Contributing

Enclave's upstream code is **not only here**. The repository with the *compiler for sentences*
language files is at
[`dicio-sentences-compiler`](https://github.com/Stypox/dicio-sentences-compiler), the *number parser
and formatter* is at [`dicio-numbers`](https://github.com/Stypox/dicio-numbers) and the code for
evaluating matching algorithms is at [`dicio-evaluation`](https://github.com/Stypox/dicio-evaluation).

When contributing keep in mind that other people may have **needs** and **views different** than
yours, so please *respect* them.

### Translating

Enclave inherits Dicio's translations. To translate the upstream strings, follow the steps listed in
Dicio's documentation: https://dicio.stypox.org/translating.html

### Adding skills

To add a new skill, or improve an existing one, check out the upstream guide:
https://dicio.stypox.org/adding_skill.html

To expose a skill to the local LLM as a callable tool, see the `orchestrator/` section of
[`docs/local-llm.md`](docs/local-llm.md).

## License

GPLv3, inherited from [Dicio](https://github.com/Stypox/dicio-android). See [LICENSE](LICENSE).

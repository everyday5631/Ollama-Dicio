# Enclave — design implementation notes

Implementation of `design_handoff_enclave_assistant` (the "Enclave" rebrand + UI redesign),
covering what was built, what was deliberately left, and where the seams are.

## Rebrand

| What | Where |
|---|---|
| User-facing name → **Enclave** | 464 occurrences across 28 `strings.xml` files (default + 27 locales) |
| Wake word → **"Hey Enclave"** | same, plus the comments in `WakeDevice`/`WakeService` |
| Debug app label | `app/build.gradle.kts` → `Enclave-<branch>` |
| LLM system prompt | `LlmOrchestrator` — the model now introduces itself as Enclave |
| App icon | `drawable/ic_enclave_{foreground,background,monochrome}.xml` + regenerated legacy PNGs |

The replacement was case-sensitive on `Dicio`, which leaves lowercase upstream URLs such as
`github.com/Stypox/dicio-android` intact — those point at the real project and should not be
rewritten.

**Not renamed, deliberately:** the source package (`namespace org.stypox.dicio`) and the
`applicationId` (`lol.everyday5631.nova`). The handoff calls both a separate decision, and changing
the `applicationId` would make the new build install alongside the old one rather than update it.

## Icon

`enclave_icon.svg` was hand-converted to a `VectorDrawable` rather than run through Asset Studio, so
the paths stay readable and the stroke opacities survive as `strokeAlpha`.

Two things worth knowing:

* The art is scaled to **0.62** inside a group. An adaptive icon only guarantees the middle ~66% of
  the canvas is visible; at full size the launcher would crop the cube's corners.
* The legacy (pre-API-26) `ic_launcher.png` files are **generated raster**, composited from the same
  SVG onto the radial background with a squircle mask. `minSdk` is 21, so these are what older
  devices actually show.

The adaptive icon now points at vector drawables, which made
`mipmap-*/ic_launcher_{foreground,background,monochrome}.png` unreferenced; they were removed.
`Drawer.kt` was repointed to `ic_enclave_monochrome`, since it tints the mark flat anyway.

## Type

The handoff asks for Bricolage Grotesque and Public Sans "via downloadable fonts or bundle the
TTFs". **The TTFs are bundled.** Downloadable fonts save ~390 KB of APK but need Google Play
Services and a network round trip on first use — which is a poor fit for an assistant whose whole
premise is that it works offline and talks to nobody. Bundling means a de-Googled phone renders the
brand correctly and the app makes no request.

## Colour

`EnclaveTokens` holds the raw brand values, and the **dark** Material scheme in `Theme.kt` is
remapped onto them so ordinary Material components inherit the brand without every call site
reaching for tokens.

The **light** scheme is untouched: the handoff targets dark only and puts light explicitly out of
scope, so the original Dicio light palette still applies if the user picks the light theme. That is
a known inconsistency, not an oversight — see *Follow-ups*.

## Screens

| Screen | File | State |
|---|---|---|
| 1a Voice hero | `ui/enclave/VoiceScreen.kt` | Built; **not** wired to STT |
| 1d Onboarding | `ui/enclave/OnboardingScreen.kt` | Built; not yet shown on first run |
| 1e Skills | `ui/enclave/SkillsScreen.kt` | Built; rows are a static catalogue |
| 1f Model manager | `ui/enclave/ModelManagerScreen.kt` | Built and **wired to real state** |
| 1g Privacy | `ui/enclave/PrivacyScreen.kt` | Built; flags are screen-local |
| 1b Conversation + tool cards | — | Not built |
| 1c Push-to-talk | — | Not built |

The handoff recommends 1a as the default idle screen with 1b's tool-result cards folded in as the
answer view, and 1c as an alternate mode. This implementation builds 1a and leaves 1b/1c, because
both need the interaction-log rendering that `InteractionComponents.kt` already does differently —
merging those two designs is a bigger change than recreating a screen.

Shared pieces live in `ui/enclave/EnclaveComponents.kt`. The toggle is bespoke rather than a
Material `Switch`: the handoff pins a 42×25dp track with a 21dp knob travelling 17dp, which
Material's switch cannot be configured into.

### What "wired to real state" means

Only the **model manager** reads live data: `LlmModelState` drives the status pill, the download
progress bar and the per-row Active/Selected pills, and the model field writes through
`LocalAiViewModel.setModel`. Download progress is genuinely live, not a timer.

The other screens are presentation with hoisted state — every one takes its data and callbacks as
parameters, so wiring them up later is a change at the call site in `Navigation.kt`, not a rewrite.

## Follow-ups

These are the honest gaps, roughly in order of value:

1. **Voice screen is not connected to STT.** `VoiceScreen(listening, partialTranscript, onMicClick)`
   needs `SttInputDeviceWrapper`'s state; `HomeScreen` remains the working default.
2. **Skill toggles do nothing.** `defaultSkillRows()` is a static list; wiring needs
   `SkillSettingsViewModel` to expose an enabled flag per skill id.
3. **Privacy flags are not persisted.** They live in nav-entry scope, so they reset when you leave
   the screen. Each maps to an existing or new field in `user_settings.proto`.
4. **"Clear all data" is a no-op** behind a confirmation dialog.
5. **Cloud-request counter is hardcoded to 0.** Making it truthful means counting actual skill
   network calls, which nothing currently tracks.
6. **Light theme still uses the old palette.**
7. **Onboarding never shows.** It needs a "seen onboarding" flag and a launch-time branch.

## Verification

The whole app compiles and `assembleDebug` succeeds. Verified in the built APK: label `Enclave`,
the new icon resources, all six TTFs, and **zero** occurrences of "Dicio" in compiled resources.

**The screens have not been seen rendering.** There is no device or emulator in this environment
and no screenshot-test harness, so layout, spacing and the animations are as-specified rather than
as-observed. `@Preview` composables are provided for every screen.

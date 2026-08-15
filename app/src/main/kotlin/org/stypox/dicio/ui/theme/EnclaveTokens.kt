package org.stypox.dicio.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp

/**
 * The Enclave design tokens, transcribed from the design handoff
 * (`design_handoff_enclave_assistant/README.md`).
 *
 * These are the raw brand values. Where a Material 3 slot exists for one it is also mapped into the
 * dark [androidx.compose.material3.ColorScheme] in `Theme.kt`, so ordinary Material components pick
 * the brand up automatically; the tokens here exist for the places the Material palette has no slot
 * for — the accent gradient, the on-device/needs-internet status colours, and the three tiers of
 * muted text.
 *
 * The design targets a **dark theme only**; a light theme is explicitly out of scope for now, so the
 * light scheme in `Theme.kt` is left as it was.
 */
object EnclaveTokens {

    // ----- colour -----

    /** Screen background. */
    val Bg = Color(0xFF0F0A1E)

    /** Recessed panels and the home-screen backdrop. */
    val Bg2 = Color(0xFF160F2B)

    /** Cards, list groups, input fields. */
    val Surface = Color(0xFF1B1436)

    /** Chips, secondary buttons, tags. */
    val SurfaceVariant = Color(0xFF241A44)

    /** Hairline dividers and card borders: white at 9%. */
    val Line = Color(0x17FFFFFF)

    /** Brand purple: CTAs, active toggles, highlights. */
    val Accent = Color(0xFFB14CFF)

    /** Gradient partner / secondary accent. */
    val Accent2 = Color(0xFF7C5CFF)

    /** Deep end of the accent gradient. */
    val AccentDeep = Color(0xFF7C3BE0)

    /** "On-device" / offline / success. */
    val Ok = Color(0xFF56E0A0)

    /** "Needs internet" / online skills. */
    val Warn = Color(0xFFFFB454)

    /** Destructive actions (clear data). */
    val Danger = Color(0xFFFF6B8B)

    /** Primary text. */
    val Text = Color(0xFFF3EEFE)

    /** Secondary text. */
    val TextMuted = Color(0xFFB3A8D6)

    /** Tertiary text, captions, placeholders. */
    val TextDim = Color(0xFF867AA8)

    /** Track colour of an OFF toggle: white at 14%. */
    val ToggleOff = Color(0x24FFFFFF)

    // ----- gradients -----

    /**
     * The 135° accent gradient used on the mic button, primary buttons and download bars.
     *
     * A linear gradient at 135° in CSS runs top-left to bottom-right, which is what
     * [Brush.linearGradient]'s default start/end do for a square box, so no explicit offsets are
     * needed here.
     */
    val AccentGradient = Brush.linearGradient(listOf(Accent, AccentDeep))

    /** The orb on the voice screen: an off-centre radial highlight, per direction 1a. */
    val OrbGradient = Brush.radialGradient(
        0.0f to Color(0xFFD3A2FF),
        0.44f to Accent,
        1.0f to Color(0xFF5F24C8),
    )

    /** Faces of the isometric cube mark. */
    val IconTop = Color(0xFFB768FF)
    val IconRight = Color(0xFF7C3BE0)
    val IconLeft = Color(0xFF571FB0)

    // ----- shape -----

    /** Cards and list groups. */
    val RadiusCard = 16.dp

    /** Input fields. */
    val RadiusField = 13.dp

    /** Primary buttons. */
    val RadiusButton = 16.dp

    /** Primary button height. */
    val ButtonHeight = 52.dp

    /** Mic FAB on the hero screen. */
    val MicHero = 78.dp

    /** Mic button inline in an input row. */
    val MicInline = 44.dp

    /** Push-to-talk button. */
    val MicPushToTalk = 88.dp

    // ----- toggle (used app-wide) -----

    val ToggleTrackWidth = 42.dp
    val ToggleTrackHeight = 25.dp
    val ToggleKnob = 21.dp
    val TogglePadding = 2.dp

    /** Distance the knob travels when switched on. */
    val ToggleTravel = 17.dp

    /** Duration of the toggle's colour and knob animation. */
    const val ToggleAnimationMs = 200

    // ----- motion -----

    /** Orb pulse period. */
    const val OrbPulseMs = 3400

    /** Waveform bar period; bars are staggered within it. */
    const val WaveformMs = 1050

    // ----- elevation -----

    /**
     * The soft purple glow under accented buttons. Cards deliberately use the 9%-white [Line]
     * border instead of a drop shadow.
     */
    val AccentGlow = Shadow(
        color = Color(0x73B14CFF), // rgba(177, 76, 255, 0.45)
        blurRadius = 34f,
    )
}

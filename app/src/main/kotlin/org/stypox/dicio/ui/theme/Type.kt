package org.stypox.dicio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.stypox.dicio.R

/**
 * Enclave typography: **Bricolage Grotesque** for display/headings and **Public Sans** for body and
 * UI, both from Google Fonts (OFL).
 *
 * The TTFs are **bundled** rather than fetched with downloadable fonts. Downloadable fonts would
 * save ~390 KB of APK, but they require Google Play Services and a network round trip on first use,
 * which sits badly with an assistant whose whole premise is that it works offline and talks to
 * nobody. Bundling means the app renders identically on a de-Googled device and makes no request.
 */

/** Display family: tight, heavy, for the wordmark, hero headlines and screen titles. */
val DisplayFamily = FontFamily(
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/** Body family: everything else. */
val BodyFamily = FontFamily(
    Font(R.font.public_sans_regular, FontWeight.Normal),
    Font(R.font.public_sans_medium, FontWeight.Medium),
    Font(R.font.public_sans_semibold, FontWeight.SemiBold),
    Font(R.font.public_sans_bold, FontWeight.Bold),
)

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * The handoff's type ramp mapped onto Material's slots: display/headline take Bricolage with the
 * ~-0.02em tracking the design calls for, everything else takes Public Sans.
 */
val AppTypography = Typography().let { default ->
    Typography(
        displayLarge = default.displayLarge.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp, letterSpacing = (-0.02).em, lineHeightStyle = lineHeightStyle,
        ),
        displayMedium = default.displayMedium.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp, letterSpacing = (-0.02).em, lineHeightStyle = lineHeightStyle,
        ),
        displaySmall = default.displaySmall.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, letterSpacing = (-0.02).em, lineHeightStyle = lineHeightStyle,
        ),
        // hero headlines ~30sp, screen titles ~26sp
        headlineLarge = default.headlineLarge.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, letterSpacing = (-0.02).em, lineHeightStyle = lineHeightStyle,
        ),
        headlineMedium = default.headlineMedium.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
            fontSize = 26.sp, letterSpacing = (-0.02).em, lineHeightStyle = lineHeightStyle,
        ),
        headlineSmall = default.headlineSmall.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, letterSpacing = (-0.02).em, lineHeightStyle = lineHeightStyle,
        ),
        titleLarge = default.titleLarge.copy(
            fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            letterSpacing = (-0.01).em,
        ),
        titleMedium = default.titleMedium.copy(
            fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        ),
        // list titles 13.5-14sp
        titleSmall = default.titleSmall.copy(
            fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        ),
        // body 14-15sp
        bodyLarge = default.bodyLarge.copy(fontFamily = BodyFamily, fontSize = 15.sp),
        bodyMedium = default.bodyMedium.copy(fontFamily = BodyFamily, fontSize = 14.sp),
        bodySmall = default.bodySmall.copy(fontFamily = BodyFamily, fontSize = 12.sp),
        labelLarge = default.labelLarge.copy(
            fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        ),
        labelMedium = default.labelMedium.copy(
            fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp,
        ),
        // captions 11-12sp
        labelSmall = default.labelSmall.copy(
            fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp,
        ),
    )
}

/** Eyebrow labels: 11sp uppercase with wide tracking, e.g. "SETTINGS · LOCAL AI". */
val EyebrowStyle = TextStyle(
    fontFamily = BodyFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = 0.09.em,
)

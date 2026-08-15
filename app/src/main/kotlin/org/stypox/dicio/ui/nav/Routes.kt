package org.stypox.dicio.ui.nav

import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
object MainSettings

@Serializable
object SkillSettings

@Serializable
object LocalAiSettings

/** Enclave screen 1f: the redesigned on-device model manager. */
@Serializable
object ModelManager

/** Enclave screen 1e: skills and plugins, split by internet need. */
@Serializable
object EnclaveSkills

/** Enclave screen 1g: privacy and data controls. */
@Serializable
object PrivacyControls

/**
 * The classic interaction log with its graphical skill outputs, reached from the voice screen's
 * History button. It is no longer the start destination, but nothing it could do was removed.
 */
@Serializable
object History

/** Enclave screen 1d: the privacy-promise onboarding slide. */
@Serializable
object Onboarding

@Serializable
object About

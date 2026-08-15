package org.stypox.dicio.ui.enclave

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.ui.home.HomeScreenViewModel
import org.stypox.dicio.ui.home.InteractionLog

/**
 * Connects [VoiceScreen] to the real speech pipeline.
 *
 * The mic button drives [org.stypox.dicio.di.SttInputDeviceWrapper.onClick], exactly as the classic
 * home screen does, so all of the device's state handling — loading the Vosk model, asking for the
 * microphone permission, starting and stopping — is reused rather than reimplemented.
 *
 * The one addition is that [InputEvent]s are observed on their way past: `Partial` events feed the
 * live transcript under the orb, and every terminal event clears it, so the caption does not keep
 * showing half an utterance after the user has stopped talking. The events are always forwarded to
 * [org.stypox.dicio.eval.SkillEvaluator] regardless, so watching them cannot break evaluation.
 */
@Composable
fun VoiceRoute(
    onHistoryClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeScreenViewModel = hiltViewModel(),
) {
    val sttState by viewModel.sttInputDevice.uiState.collectAsState()
    val interactionLog by viewModel.skillEvaluator.state.collectAsState()

    var partialTranscript by remember { mutableStateOf("") }

    VoiceScreen(
        listening = sttState is SttState.Listening,
        partialTranscript = partialTranscript,
        lastAnswer = interactionLog.lastSpokenAnswer(viewModel.skillContext),
        thinking = sttState is SttState.WaitingForResult,
        onMicClick = {
            viewModel.sttInputDevice.onClick { event ->
                partialTranscript = when (event) {
                    is InputEvent.Partial -> event.utterance
                    // Final, None and Error all end the utterance: drop the partial so the
                    // answer is not shown next to a stale fragment of the question
                    else -> ""
                }
                viewModel.skillEvaluator.processInputEvent(event)
            }
        },
        onHistoryClick = onHistoryClick,
        onKeyboardClick = onKeyboardClick,
        modifier = modifier,
    )
}

/**
 * The most recent thing the assistant said, or null if it has not answered yet.
 *
 * The interaction log holds full [org.dicio.skill.skill.SkillOutput]s, which can render graphical
 * answers the hero screen has no room for; this takes only the spoken form. The full log, with its
 * graphical output, is what the History button leads to.
 */
private fun InteractionLog.lastSpokenAnswer(skillContext: SkillContext): String? {
    val lastAnswer = interactions.lastOrNull()?.questionsAnswers?.lastOrNull()?.answer ?: return null
    return runCatching { lastAnswer.getSpeechOutput(skillContext) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

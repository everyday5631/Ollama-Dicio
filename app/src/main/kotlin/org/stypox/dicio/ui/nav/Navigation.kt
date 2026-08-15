package org.stypox.dicio.ui.nav

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.stypox.dicio.R
import org.stypox.dicio.io.input.stt_popup.SttPopupActivity
import org.stypox.dicio.settings.LocalAiSettingsScreen
import org.stypox.dicio.ui.enclave.ModelManagerScreen
import org.stypox.dicio.ui.enclave.OnboardingScreen
import org.stypox.dicio.ui.enclave.PrivacyScreen
import org.stypox.dicio.ui.enclave.PrivacyState
import org.stypox.dicio.ui.enclave.SkillsScreen
import org.stypox.dicio.ui.enclave.defaultSkillRows
import org.stypox.dicio.ui.enclave.VoiceRoute
import org.stypox.dicio.settings.MainSettingsScreen
import org.stypox.dicio.settings.SkillSettingsScreen
import org.stypox.dicio.ui.about.AboutScreen
import org.stypox.dicio.ui.home.HomeScreen

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val backIcon = @Composable {
        IconButton(
            onClick = { navController.navigateUp() }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    }

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            // Screen 1a is the hero the design calls for. The classic interaction log, with the
            // graphical skill outputs the orb screen has no room for, is one tap away behind the
            // History button rather than removed.
            VoiceRoute(
                onHistoryClick = { navController.navigate(History) },
                onKeyboardClick = { navController.navigate(History) },
            )
        }

        composable<History> {
            val context = LocalContext.current
            ScreenWithDrawer(
                onSettingsClick = { navController.navigate(MainSettings) },
                onAboutClick = { navController.navigate(About) },
                onSpeechToTextPopupClick = {
                    val intent = Intent(context, SttPopupActivity::class.java)
                    context.startActivity(intent)
                },
            ) {
                HomeScreen(it)
            }
        }

        composable<MainSettings> {
            MainSettingsScreen(
                navigationIcon = backIcon,
                navigateToSkillSettings = { navController.navigate(SkillSettings) },
                navigateToLocalAiSettings = { navController.navigate(LocalAiSettings) },
                navigateToModelManager = { navController.navigate(ModelManager) },
                navigateToPrivacyControls = { navController.navigate(PrivacyControls) },
            )
        }

        composable<SkillSettings> {
            SkillSettingsScreen(navigationIcon = backIcon)
        }

        composable<LocalAiSettings> {
            LocalAiSettingsScreen(navigationIcon = backIcon)
        }

        // ----- Enclave redesign (design_handoff_enclave_assistant) -----

        composable<ModelManager> {
            ModelManagerScreen(navigationIcon = backIcon)
        }

        composable<EnclaveSkills> {
            // TODO wire to the real per-skill settings once SkillSettingsViewModel exposes
            //  an enabled flag per skill id; the rows below mirror the repo README's skill set
            SkillsScreen(
                skills = defaultSkillRows(),
                onToggle = { _, _ -> },
                navigationIcon = backIcon,
            )
        }

        composable<PrivacyControls> {
            // held in nav-entry scope for now; persisting these to the settings store is
            // the follow-up noted in docs/enclave-design.md
            var privacy by remember { mutableStateOf(PrivacyState()) }
            PrivacyScreen(
                state = privacy,
                onChange = { privacy = it },
                onClearAllData = {},
                navigationIcon = backIcon,
            )
        }

        composable<Onboarding> {
            OnboardingScreen(
                onGetStarted = { navController.popBackStack() },
                onReadPrivacyPromise = {},
            )
        }

        composable<About> {
            AboutScreen(navigationIcon = backIcon)
        }
    }
}

@Composable
fun ScreenWithDrawer(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSpeechToTextPopupClick: () -> Unit,
    screen: @Composable (navigationIcon: @Composable () -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onSettingsClick = onSettingsClick,
                onAboutClick = onAboutClick,
                onSpeechToTextPopupClick = onSpeechToTextPopupClick,
                closeDrawer = {
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        },
    ) {
        screen {
            AppBarDrawerIcon(
                onDrawerClick = {
                    scope.launch {
                        drawerState.apply {
                            if (isClosed) open() else close()
                        }
                    }
                },
                isClosed = drawerState.isClosed,
            )
        }
    }
}

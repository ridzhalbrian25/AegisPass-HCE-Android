package com.aegispass.hce.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aegispass.hce.data.hce.NfcTagReaderManager
import com.aegispass.hce.presentation.mvi.AegisIntent
import com.aegispass.hce.presentation.mvi.AegisViewModel
import com.aegispass.hce.presentation.ui.screens.AegisMainScreen
import com.aegispass.hce.presentation.ui.theme.AegisPassTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

/**
 * MainActivity hosting Jetpack Compose UI, BiometricPrompt (BIOMETRIC_STRONG | DEVICE_CREDENTIAL),
 * NfcAdapter.ReaderCallback, Haptic Feedback, and WindowManager.LayoutParams.FLAG_SECURE.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AegisViewModel by viewModels()
    private val nfcReaderManager = NfcTagReaderManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NFR 6.1 Anti-Screen Capture: Protect Hex Viewer, UID/AID, and Card Emulation screens
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            val state by viewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.biometricTriggerFlow.collectLatest { cardId ->
                    promptStrongBiometricForCard(cardId)
                }
            }

            AegisPassTheme(pureBlackOled = state.isOledPureBlack) {
                AegisMainScreen(
                    state = state,
                    onIntent = { intent ->
                        triggerHapticTick()
                        viewModel.dispatch(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcReaderManager.enableScanMode(this) { scannedResult ->
            runOnUiThread {
                triggerHapticSuccess()
                viewModel.dispatch(AegisIntent.OnPhysicalTagScanned(scannedResult))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcReaderManager.disableScanMode(this)
    }

    private fun promptStrongBiometricForCard(cardId: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    triggerHapticSuccess()
                    viewModel.dispatch(AegisIntent.OnBiometricAuthenticated(cardId))
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Otentikasi Biometrik AegisPass HCE")
            .setSubtitle("Verifikasi identitas untuk membuka kunci payload kartu di Android Keystore")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun triggerHapticTick() {
        val vibrator = getVibratorInstance() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private fun triggerHapticSuccess() {
        val vibrator = getVibratorInstance() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        }
    }

    private fun getVibratorInstance(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

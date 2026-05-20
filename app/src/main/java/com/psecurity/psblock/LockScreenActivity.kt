package com.psecurity.psblock

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.biometricEnabled
import com.psecurity.psblock.PSBlockPrefs.hashPassword
import com.psecurity.psblock.PSBlockPrefs.lockPasswordHash

class LockScreenActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        val etPass = findViewById<EditText>(R.id.etLockPassword)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        val btnBio = findViewById<Button>(R.id.btnBiometric)
        val tvStatus = findViewById<TextView>(R.id.tvLockStatus)

        val hash = lockPasswordHash
        if (hash.isNullOrBlank()) {
            SecurityGate.markUnlocked()
            appendLog(getString(R.string.lock_no_password_log))
            finish()
            return
        }

        val biometricReady = biometricEnabled && canUseBiometric()
        btnBio.visibility = if (biometricReady) View.VISIBLE else View.GONE
        tvStatus.text = if (biometricReady) {
            getString(R.string.lock_auth_status_biometric)
        } else {
            getString(R.string.lock_auth_status_password)
        }

        btnUnlock.setOnClickListener {
            val entered = etPass.text.toString()
            if (entered.isNotEmpty() && hash == hashPassword(entered)) {
                unlock(getString(R.string.lock_password_method))
            } else {
                Toast.makeText(this, getString(R.string.lock_wrong_password), Toast.LENGTH_SHORT).show()
                appendLog(getString(R.string.lock_wrong_password_log))
                etPass.text?.clear()
            }
        }

        btnBio.setOnClickListener { showBiometricPrompt() }
        if (biometricReady) showBiometricPrompt()
    }

    private fun unlock(method: String) {
        SecurityGate.markUnlocked()
        appendLog(getString(R.string.lock_unlocked_log, method))
        setResult(RESULT_OK)
        finish()
    }

    private fun canUseBiometric(): Boolean {
        return try {
            val bm = BiometricManager.from(this)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
    }

    private fun showBiometricPrompt() {
        if (!canUseBiometric()) {
            Toast.makeText(this, getString(R.string.lock_biometrics_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock(getString(R.string.lock_biometric_method))
                }
                override fun onAuthenticationFailed() {
                    Toast.makeText(this@LockScreenActivity, getString(R.string.lock_biometrics_not_recognized), Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        appendLog(getString(R.string.lock_biometrics_error_log, errString))
                    }
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_biometric_prompt_title))
            .setSubtitle(getString(R.string.lock_biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.lock_use_password))
            .build()
        prompt.authenticate(info)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        // Mantem o console protegido ate autenticar.
    }
}

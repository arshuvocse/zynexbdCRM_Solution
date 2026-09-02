package com.zynexbd.crmsolution.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.zynexbd.crmsolution.databinding.ActivityLoginBinding
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.AppLogger
import com.zynexbd.crmsolution.utils.AppUpdateHelper
import com.zynexbd.crmsolution.utils.LanguageManager
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.viewmodel.LoginUiState
import com.zynexbd.crmsolution.viewmodel.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single entry point for both Admin and User roles. On success, routes by
 * role: Admin -> AdminDashboardActivity, User -> UserHomeActivity.
 */
class LoginActivity : BaseActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i(TAG, "LoginActivity onCreate started")
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        AppLogger.i(TAG, "Current configured Base URL: ${session.getServerBaseUrl()}")

        // Check for App Updates
        AppUpdateHelper.checkForUpdate(this, lifecycleScope)

        if (session.isLoggedIn()) {
            AppLogger.i(TAG, "User already logged in with role: ${session.getRole()}. Redirecting.")
            routeByRole(session.getRole())
            return
        }

        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN
        binding.buttonLanguage.text = if (isEn) "🌐 English 🇬🇧" else "🌐 বাংলা 🇧🇩"
        binding.buttonLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }

        // Long click on branding to configure server Base URL if needed
        binding.layoutAppBranding.setOnLongClickListener {
            showServerConfigDialog()
            true
        }

        binding.layoutLoginFooter.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://zynexsofttech.com"))
                startActivity(browserIntent)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to open footer website link: ${e.message}")
            }
        }

        binding.buttonLogin.setOnClickListener {
            val username = binding.editUsername.text.toString().trim()
            val password = binding.editPassword.text.toString()
            AppLogger.i(TAG, "Login button clicked for username: '$username'")
            viewModel.login(username, password)
        }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is LoginUiState.Idle -> {
                    AppLogger.d(TAG, "Login UI State: Idle")
                }
                is LoginUiState.Loading -> {
                    AppLogger.d(TAG, "Login UI State: Loading")
                    binding.buttonLogin.isEnabled = false
                    binding.buttonLogin.text = if (isEn) "Signing in..." else "লগইন হচ্ছে..."
                }
                is LoginUiState.Error -> {
                    AppLogger.w(TAG, "Login UI State: Error - ${state.message}")
                    binding.buttonLogin.isEnabled = true
                    binding.buttonLogin.text = if (isEn) "SIGN IN" else "লগইন করুন"
                    val msg = when {
                        state.message.contains("Invalid username or password", ignoreCase = true) ->
                            if (isEn) "Invalid username or password." else "ইউজারনেম বা পাসওয়ার্ড সঠিক নয়।"
                        state.message.contains("Network", ignoreCase = true) ||
                        state.message.contains("Connection", ignoreCase = true) ||
                        state.message.contains("Failed to connect", ignoreCase = true) ->
                            if (isEn) "Server connection failed (${session.getServerBaseUrl()}). Check network." else "সারভারে কানেক্ট করা যাচ্ছে না (${session.getServerBaseUrl()})। নেটওয়ার্ক চেক করুন।"
                        else -> state.message
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                is LoginUiState.Success -> {
                    AppLogger.i(TAG, "Login UI State: Success! User: ${state.response.username}, Role: ${state.response.role}")
                    binding.buttonLogin.isEnabled = true
                    binding.buttonLogin.text = if (isEn) "SIGN IN" else "লগইন করুন"
                    routeByRole(state.response.role)
                }
            }
        }
    }

    private fun showServerConfigDialog() {
        val currentUrl = session.getServerBaseUrl()
        val input = EditText(this).apply {
            setText(currentUrl)
            setSelection(text.length)
            hint = "http://192.168.110.108:8080/"
        }
        AlertDialog.Builder(this)
            .setTitle("⚙️ Server Configuration")
            .setMessage("Configure API Base URL (e.g. http://192.168.110.108:8080/ for WiFi or http://127.0.0.1:8080/ for ADB reverse):")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotBlank()) {
                    session.setServerBaseUrl(newUrl)
                    ApiClient.resetClient()
                    AppLogger.i(TAG, "Updated Server Base URL to: $newUrl")
                    Toast.makeText(this, "Server URL updated: $newUrl", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Reset to Default") { _, _ ->
                session.setServerBaseUrl(null)
                ApiClient.resetClient()
                AppLogger.i(TAG, "Reset Server Base URL to default")
                Toast.makeText(this, "Reset to default: ${session.getServerBaseUrl()}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun routeByRole(role: String?) {
        AppLogger.i(TAG, "Routing by role: '$role'")
        when (role) {
            "Admin" -> {
                startActivity(Intent(this, AdminCrmDashboardActivity::class.java))
                finish()
            }
            "Manager" -> {
                startActivity(Intent(this, ManagerCrmDashboardActivity::class.java))
                finish()
            }
            "User" -> {
                startActivity(Intent(this, UserHomeActivity::class.java))
                finish()
            }
            else -> {
                AppLogger.e(TAG, "Unknown role received: '$role'")
                Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

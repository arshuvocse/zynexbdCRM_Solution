package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import android.os.Build
import android.provider.Settings
import com.zynexbd.crmsolution.models.LoginResponse
import com.zynexbd.crmsolution.repository.AuthRepository
import com.zynexbd.crmsolution.repository.AuthResult
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val response: LoginResponse) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _uiState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val uiState: LiveData<LoginUiState> = _uiState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("ইউজারনেম এবং পাসওয়ার্ড প্রদান করা আবশ্যক।")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val context = getApplication<Application>()
            val deviceId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                "UNKNOWN_DEVICE"
            }
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

            when (val result = repository.login(username, password, deviceId, deviceModel)) {
                is AuthResult.Success -> _uiState.value = LoginUiState.Success(result.response)
                is AuthResult.Error -> _uiState.value = LoginUiState.Error(result.message)
            }
        }
    }
}

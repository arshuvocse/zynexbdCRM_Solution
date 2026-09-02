package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.AttendanceResponse
import com.zynexbd.crmsolution.repository.AttendanceRepository
import kotlinx.coroutines.launch
import java.io.File

sealed class PunchUiState {
    object Idle : PunchUiState()
    object Loading : PunchUiState()
    data class Success(val response: AttendanceResponse) : PunchUiState()
    data class Error(val message: String) : PunchUiState()
}

class PunchAttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository(application)

    private val _uiState = MutableLiveData<PunchUiState>(PunchUiState.Idle)
    val uiState: LiveData<PunchUiState> = _uiState

    fun punchIn(selfieFile: File, latitude: Double, longitude: Double) {
        com.zynexbd.crmsolution.utils.AppLogger.i("PunchVM", "punchIn requested: lat=$latitude, lng=$longitude, selfie=${selfieFile.name} (${selfieFile.length()} bytes)")
        _uiState.value = PunchUiState.Loading
        viewModelScope.launch {
            repository.punchIn(selfieFile, latitude, longitude)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("PunchVM", "punchIn SUCCESS: timestamp=${it.timestamp}, status=${it.status}")
                    _uiState.value = PunchUiState.Success(it)
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("PunchVM", "punchIn FAILED: ${it.message}", it)
                    _uiState.value = PunchUiState.Error(it.message ?: "Duty In failed.")
                }
        }
    }

    fun punchOut(selfieFile: File, latitude: Double, longitude: Double) {
        com.zynexbd.crmsolution.utils.AppLogger.i("PunchVM", "punchOut requested: lat=$latitude, lng=$longitude, selfie=${selfieFile.name} (${selfieFile.length()} bytes)")
        _uiState.value = PunchUiState.Loading
        viewModelScope.launch {
            repository.punchOut(selfieFile, latitude, longitude)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("PunchVM", "punchOut SUCCESS: timestamp=${it.timestamp}, status=${it.status}")
                    _uiState.value = PunchUiState.Success(it)
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("PunchVM", "punchOut FAILED: ${it.message}", it)
                    _uiState.value = PunchUiState.Error(it.message ?: "Duty Out failed.")
                }
        }
    }

    fun resetState() {
        _uiState.value = PunchUiState.Idle
    }
}

package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.AttendanceResponse
import com.zynexbd.crmsolution.repository.AttendanceRepository
import kotlinx.coroutines.launch

class AdminAttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository(application)

    private val _records = MutableLiveData<List<AttendanceResponse>>(emptyList())
    val records: LiveData<List<AttendanceResponse>> = _records

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun load(month: Int? = null, year: Int? = null) {
        viewModelScope.launch {
            repository.getAllForAdmin(month = month, year = year)
                .onSuccess { _records.value = it }
                .onFailure { _error.value = it.message }
        }
    }
}

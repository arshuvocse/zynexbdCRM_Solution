package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.LeaveApplicationResponse
import com.zynexbd.crmsolution.repository.LeaveRepository
import kotlinx.coroutines.launch

class LeaveHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LeaveRepository(application)

    private val _applications = MutableLiveData<List<LeaveApplicationResponse>>(emptyList())
    val applications: LiveData<List<LeaveApplicationResponse>> = _applications

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun load() {
        viewModelScope.launch {
            repository.getMyHistory()
                .onSuccess { _applications.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun cancel(id: Int) {
        viewModelScope.launch {
            repository.cancel(id)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }
}

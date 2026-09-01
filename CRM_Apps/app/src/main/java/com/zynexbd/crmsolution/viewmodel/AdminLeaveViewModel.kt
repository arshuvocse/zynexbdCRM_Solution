package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.LeaveApplicationResponse
import com.zynexbd.crmsolution.repository.LeaveRepository
import kotlinx.coroutines.launch

class AdminLeaveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LeaveRepository(application)

    private val _applications = MutableLiveData<List<LeaveApplicationResponse>>(emptyList())
    val applications: LiveData<List<LeaveApplicationResponse>> = _applications

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var currentStatus: String? = "Pending"

    fun load(status: String? = currentStatus) {
        currentStatus = status
        viewModelScope.launch {
            repository.getApplications(status)
                .onSuccess { _applications.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun approve(id: Int) {
        viewModelScope.launch {
            repository.approve(id, null)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }

    fun reject(id: Int, comment: String?) {
        viewModelScope.launch {
            repository.reject(id, comment)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }

    fun bulkApprove(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.bulkApprove(ids, "Bulk approved by Admin")
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }
}

package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.ApplyLeaveRequest
import com.zynexbd.crmsolution.models.LeaveBalance
import com.zynexbd.crmsolution.models.LeaveType
import com.zynexbd.crmsolution.repository.LeaveRepository
import kotlinx.coroutines.launch

class ApplyLeaveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LeaveRepository(application)

    private val _leaveTypes = MutableLiveData<List<LeaveType>>(emptyList())
    val leaveTypes: LiveData<List<LeaveType>> = _leaveTypes

    private val _balances = MutableLiveData<List<LeaveBalance>>(emptyList())
    val balances: LiveData<List<LeaveBalance>> = _balances

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadTypesAndBalances() {
        viewModelScope.launch {
            repository.getActiveTypes()
                .onSuccess { _leaveTypes.value = it }
                .onFailure { _error.value = it.message }

            repository.getMyBalances()
                .onSuccess { _balances.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun apply(request: ApplyLeaveRequest, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.apply(request)
                .onSuccess { onDone(true, null) }
                .onFailure { onDone(false, it.message) }
        }
    }
}

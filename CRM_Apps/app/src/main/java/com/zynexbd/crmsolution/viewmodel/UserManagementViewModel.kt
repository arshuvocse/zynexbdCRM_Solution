package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.*
import com.zynexbd.crmsolution.repository.UserRepository
import kotlinx.coroutines.launch

class UserManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserRepository(application)

    private val _users = MutableLiveData<List<User>>(emptyList())
    val users: LiveData<List<User>> = _users

    private val _officeLocations = MutableLiveData<List<OfficeLocation>>(emptyList())
    val officeLocations: LiveData<List<OfficeLocation>> = _officeLocations

    private val _allOfficeLocations = MutableLiveData<List<OfficeLocation>>(emptyList())
    val allOfficeLocations: LiveData<List<OfficeLocation>> = _allOfficeLocations

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadOfficeLocations(all: Boolean? = null) {
        viewModelScope.launch {
            repository.getOfficeLocations(all)
                .onSuccess {
                    if (all == true) {
                        _allOfficeLocations.value = it
                    } else {
                        _officeLocations.value = it
                    }
                }
                .onFailure { _error.value = it.message }
        }
    }

    private val _quota = MutableLiveData<AdminUserQuota>(AdminUserQuota())
    val quota: LiveData<AdminUserQuota> = _quota

    fun loadQuota() {
        viewModelScope.launch {
            repository.getUserQuota()
                .onSuccess { _quota.value = it }
                .onFailure { /* fallback */ }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            loadQuota()
            repository.getUsers()
                .onSuccess { _users.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun createUser(request: CreateUserRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.createUser(request)
                .onSuccess { onDone(true); loadUsers(); loadQuota() }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    fun toggleActive(user: User) {
        viewModelScope.launch {
            repository.setActive(user, !user.isActive)
                .onSuccess { loadUsers() }
                .onFailure { _error.value = it.message }
        }
    }

    fun updateOfficeLocation(user: User, officeLocationId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val request = UpdateUserRequest(
                name = user.name,
                role = user.role,
                isActive = user.isActive,
                phoneNumber = user.phoneNumber,
                officeLocationId = officeLocationId,
                assignedOfficeLocationIds = user.assignedOfficeLocationIds
            )
            repository.updateUser(user.id, request)
                .onSuccess { onDone(true); loadUsers() }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    fun updateAdminOffices(user: User, assignedOfficeIds: List<Int>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val request = UpdateUserRequest(
                name = user.name,
                role = user.role,
                isActive = user.isActive,
                phoneNumber = user.phoneNumber,
                officeLocationId = assignedOfficeIds.firstOrNull(),
                assignedOfficeLocationIds = assignedOfficeIds
            )
            repository.updateUser(user.id, request)
                .onSuccess { onDone(true); loadUsers() }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    fun createOfficeLocation(request: CreateOfficeLocationRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.createOfficeLocation(request)
                .onSuccess { onDone(true); loadOfficeLocations(); loadOfficeLocations(true) }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    fun updateOfficeLocationDetails(id: Int, request: UpdateOfficeLocationRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.updateOfficeLocation(id, request)
                .onSuccess { onDone(true); loadOfficeLocations(); loadOfficeLocations(true) }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    fun deleteOfficeLocation(id: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.deleteOfficeLocation(id)
                .onSuccess { onDone(true); loadOfficeLocations(); loadOfficeLocations(true) }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    private val api = com.zynexbd.crmsolution.network.ApiClient.getApiService(application)

    fun getUserLocation(userId: Int, onResult: (LocationResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.getLatestLocations()
                if (resp.isSuccessful) {
                    val loc = resp.body().orEmpty().firstOrNull { it.userId == userId }
                    onResult(loc)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun resetPassword(userId: Int, newPassword: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.resetPassword(userId, newPassword)
                .onSuccess { onDone(true) }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }

    fun forceLogoutUser(userId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.forceLogoutUser(userId)
                if (resp.isSuccessful) {
                    onDone(true)
                } else {
                    _error.value = "Force logout failed: HTTP ${resp.code()}"
                    onDone(false)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to connect to server."
                onDone(false)
            }
        }
    }

    fun resetUserDevice(userId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.resetUserDevice(userId)
                .onSuccess { onDone(true); loadUsers() }
                .onFailure { _error.value = it.message; onDone(false) }
        }
    }
}

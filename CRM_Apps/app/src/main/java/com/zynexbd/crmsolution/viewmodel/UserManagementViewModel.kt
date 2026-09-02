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
            com.zynexbd.crmsolution.utils.AppLogger.d("UserMgmtVM", "Loading office locations (all=$all)")
            repository.getOfficeLocations(all)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.d("UserMgmtVM", "Office locations loaded: ${it.size} entries")
                    if (all == true) {
                        _allOfficeLocations.value = it
                    } else {
                        _officeLocations.value = it
                    }
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "loadOfficeLocations failed: ${it.message}", it)
                    _error.value = it.message
                }
        }
    }

    private val _quota = MutableLiveData<AdminUserQuota>(AdminUserQuota())
    val quota: LiveData<AdminUserQuota> = _quota

    fun loadQuota() {
        viewModelScope.launch {
            repository.getUserQuota()
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.d("UserMgmtVM", "User quota loaded: ${it.usedUserCount}/${it.maxUserLimit}")
                    _quota.value = it
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.w("UserMgmtVM", "loadQuota failed: ${it.message}")
                }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Loading user list")
            loadQuota()
            repository.getUsers()
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.d("UserMgmtVM", "Loaded ${it.size} users")
                    _users.value = it
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "loadUsers failed: ${it.message}", it)
                    _error.value = it.message
                }
        }
    }

    fun createUser(request: CreateUserRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Creating user: ${request.username} (${request.role})")
            repository.createUser(request)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "User created successfully: ${request.username}")
                    onDone(true); loadUsers(); loadQuota()
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "createUser failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    fun toggleActive(user: User) {
        viewModelScope.launch {
            val newState = !user.isActive
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Toggling user active state for '${user.username}' -> $newState")
            repository.setActive(user, newState)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "User active state updated for '${user.username}'")
                    loadUsers()
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "toggleActive failed: ${it.message}", it)
                    _error.value = it.message
                }
        }
    }

    fun updateOfficeLocation(user: User, officeLocationId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Updating office location for '${user.username}' -> ID $officeLocationId")
            val request = UpdateUserRequest(
                name = user.name,
                role = user.role,
                isActive = user.isActive,
                phoneNumber = user.phoneNumber,
                officeLocationId = officeLocationId,
                assignedOfficeLocationIds = user.assignedOfficeLocationIds
            )
            repository.updateUser(user.id, request)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Office location updated for '${user.username}'")
                    onDone(true); loadUsers()
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "updateOfficeLocation failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    fun updateAdminOffices(user: User, assignedOfficeIds: List<Int>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Updating admin offices for '${user.username}' -> $assignedOfficeIds")
            val request = UpdateUserRequest(
                name = user.name,
                role = user.role,
                isActive = user.isActive,
                phoneNumber = user.phoneNumber,
                officeLocationId = assignedOfficeIds.firstOrNull(),
                assignedOfficeLocationIds = assignedOfficeIds
            )
            repository.updateUser(user.id, request)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Admin offices updated for '${user.username}'")
                    onDone(true); loadUsers()
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "updateAdminOffices failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    fun createOfficeLocation(request: CreateOfficeLocationRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Creating office location: ${request.name}")
            repository.createOfficeLocation(request)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Office location created: ${request.name}")
                    onDone(true); loadOfficeLocations(); loadOfficeLocations(true)
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "createOfficeLocation failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    fun updateOfficeLocationDetails(id: Int, request: UpdateOfficeLocationRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Updating office location details for ID: $id")
            repository.updateOfficeLocation(id, request)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Office location updated for ID: $id")
                    onDone(true); loadOfficeLocations(); loadOfficeLocations(true)
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "updateOfficeLocationDetails failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    fun deleteOfficeLocation(id: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Deleting office location ID: $id")
            repository.deleteOfficeLocation(id)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Office location deleted for ID: $id")
                    onDone(true); loadOfficeLocations(); loadOfficeLocations(true)
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "deleteOfficeLocation failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    private val api = com.zynexbd.crmsolution.network.ApiClient.getApiService(application)

    fun getUserLocation(userId: Int, onResult: (LocationResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                com.zynexbd.crmsolution.utils.AppLogger.d("UserMgmtVM", "Fetching latest location for user ID: $userId")
                val resp = api.getLatestLocations()
                if (resp.isSuccessful) {
                    val loc = resp.body().orEmpty().firstOrNull { it.userId == userId }
                    onResult(loc)
                } else {
                    com.zynexbd.crmsolution.utils.AppLogger.w("UserMgmtVM", "Failed to fetch user location: HTTP ${resp.code()}")
                    onResult(null)
                }
            } catch (e: Exception) {
                com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "getUserLocation exception: ${e.message}", e)
                onResult(null)
            }
        }
    }

    fun resetPassword(userId: Int, newPassword: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Resetting password for user ID: $userId")
            repository.resetPassword(userId, newPassword)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Password reset success for user ID: $userId")
                    onDone(true)
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "resetPassword failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }

    fun forceLogoutUser(userId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Triggering force logout for user ID: $userId")
                val resp = api.forceLogoutUser(userId)
                if (resp.isSuccessful) {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Force logout success for user ID: $userId")
                    onDone(true)
                } else {
                    com.zynexbd.crmsolution.utils.AppLogger.w("UserMgmtVM", "Force logout failed: HTTP ${resp.code()}")
                    _error.value = "Force logout failed: HTTP ${resp.code()}"
                    onDone(false)
                }
            } catch (e: Exception) {
                com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "forceLogoutUser exception: ${e.message}", e)
                _error.value = e.message ?: "Failed to connect to server."
                onDone(false)
            }
        }
    }

    fun resetUserDevice(userId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Resetting device binding for user ID: $userId")
            repository.resetUserDevice(userId)
                .onSuccess {
                    com.zynexbd.crmsolution.utils.AppLogger.i("UserMgmtVM", "Device binding reset success for user ID: $userId")
                    onDone(true); loadUsers()
                }
                .onFailure {
                    com.zynexbd.crmsolution.utils.AppLogger.e("UserMgmtVM", "resetUserDevice failed: ${it.message}", it)
                    _error.value = it.message; onDone(false)
                }
        }
    }
}

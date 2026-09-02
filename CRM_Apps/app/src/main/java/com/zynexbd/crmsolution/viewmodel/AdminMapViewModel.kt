package com.zynexbd.crmsolution.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zynexbd.crmsolution.models.LocationResponse
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.network.SignalRClient
import kotlinx.coroutines.launch

class AdminMapViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.getApiService(application)
    private val signalR = SignalRClient(application)
    private val session = com.zynexbd.crmsolution.utils.SessionManager(application)

    private val _locations = MutableLiveData<Map<Int, LocationResponse>>(emptyMap())
    val locations: LiveData<Map<Int, LocationResponse>> = _locations

    private val _connected = MutableLiveData(false)
    val connected: LiveData<Boolean> = _connected

    fun start() {
        loadInitial()
        signalR.connect(
            onLocationUpdated = { update -> upsert(update) },
            onStateChange = { isConnected -> _connected.postValue(isConnected) }
        )
    }

    private fun loadInitial() {
        viewModelScope.launch {
            try {
                val companyId = session.getCompanyId()
                com.zynexbd.crmsolution.utils.AppLogger.i("AdminMapVM", "Loading initial user locations (CompanyId: $companyId)")
                val resp = api.getLatestLocations(companyId)
                if (resp.isSuccessful) {
                    val map = resp.body().orEmpty().associateBy { it.userId }
                    com.zynexbd.crmsolution.utils.AppLogger.d("AdminMapVM", "Loaded ${map.size} initial locations")
                    _locations.postValue(map)
                } else {
                    com.zynexbd.crmsolution.utils.AppLogger.w("AdminMapVM", "Failed to load initial locations: HTTP ${resp.code()}")
                }
            } catch (e: Exception) {
                com.zynexbd.crmsolution.utils.AppLogger.e("AdminMapVM", "Error loading initial locations: ${e.message}", e)
            }
        }
    }

    private fun upsert(update: LocationResponse) {
        com.zynexbd.crmsolution.utils.AppLogger.d("AdminMapVM", "Live location updated for user ID: ${update.userId} (${update.latitude}, ${update.longitude})")
        val current = _locations.value.orEmpty().toMutableMap()
        current[update.userId] = update
        _locations.postValue(current)
    }

    override fun onCleared() {
        com.zynexbd.crmsolution.utils.AppLogger.i("AdminMapVM", "AdminMapViewModel onCleared, disconnecting SignalR")
        signalR.disconnect()
        super.onCleared()
    }
}

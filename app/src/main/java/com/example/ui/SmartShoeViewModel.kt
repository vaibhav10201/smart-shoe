package com.example.ui

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SmartShoeApi
import com.example.data.StepData
import com.example.data.TargetRequest
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

sealed class AppScreen {
    object Start : AppScreen()
    object Dashboard : AppScreen()
}

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class SmartShoeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SmartShoeConnection"
    private val BASE_URL = "http://192.168.4.1/"

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Start)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _stepData = MutableStateFlow<StepData?>(null)
    val stepData: StateFlow<StepData?> = _stepData.asStateFlow()

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting.asStateFlow()

    private val _showTargetReachedDialog = MutableStateFlow(false)
    val showTargetReachedDialog: StateFlow<Boolean> = _showTargetReachedDialog.asStateFlow()

    private var pollingJob: Job? = null
    private var lastAlarmedTarget: Int = -1
    private var boundNetwork: Network? = null

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val loggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d(TAG, "Connection attempt started: ${request.url}")
        
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failure: ${e.javaClass.simpleName} - ${e.message}")
            throw e
        }

        Log.d(TAG, "HTTP response code: ${response.code}")
        
        val responseBody = response.body
        val source = responseBody?.source()
        source?.request(Long.MAX_VALUE)
        val buffer = source?.buffer
        val bodyString = buffer?.clone()?.readString(Charset.forName("UTF-8"))
        Log.d(TAG, "Response body: $bodyString")
        
        if (!response.isSuccessful) {
            Log.e(TAG, "Connection failure (non-2xx code)")
        }
        
        response
    }

    private val api: SmartShoeApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .socketFactory(OkHttpClient().socketFactory) // Default socket factory
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SmartShoeApi::class.java)
    }

    /**
     * Binds the application process to the Wi-Fi network even if it has no internet.
     * This ensures Retrofit traffic goes over the SmartShoe AP.
     */
    private suspend fun ensureNetworkBinding(): Boolean = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "Requesting Wi-Fi network binding...")
        
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Wi-Fi network available. Binding process...")
                try {
                    connectivityManager.bindProcessToNetwork(network)
                    boundNetwork = network
                    if (continuation.isActive) continuation.resume(true) { }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind process to network", e)
                    if (continuation.isActive) continuation.resume(false) { }
                }
            }

            override fun onUnavailable() {
                Log.e(TAG, "Wi-Fi network unavailable.")
                if (continuation.isActive) continuation.resume(false) { }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost. Clearing binding.")
                if (boundNetwork == network) {
                    connectivityManager.bindProcessToNetwork(null)
                    boundNetwork = null
                }
            }
        }

        connectivityManager.requestNetwork(request, callback, 10000)
        
        continuation.invokeOnCancellation {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun connect() {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting
            
            // Try to bind to Wi-Fi first (optional but recommended for reliability with mobile data)
            // If binding fails, we still try the request as the user might already be connected correctly.
            ensureNetworkBinding()

            try {
                val data = withContext(Dispatchers.IO) { api.getSteps() }
                _stepData.value = data
                _connectionStatus.value = ConnectionStatus.Connected
                _screen.value = AppScreen.Dashboard
                startPolling()
            } catch (e: JsonDataException) {
                Log.e(TAG, "JSON parsing error", e)
                _connectionStatus.value = ConnectionStatus.Error("Invalid data format from Smart Shoe.")
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _connectionStatus.value = ConnectionStatus.Error("Cannot reach Smart Shoe. Ensure you are connected to the 'SmartShoe' Wi-Fi.")
            }
        }
    }

    fun disconnect() {
        stopPolling()
        connectivityManager.bindProcessToNetwork(null)
        boundNetwork = null
        _screen.value = AppScreen.Start
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val data = withContext(Dispatchers.IO) { api.getSteps() }
                    _stepData.value = data
                    _connectionStatus.value = ConnectionStatus.Connected
                    checkAlarm(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    _connectionStatus.value = ConnectionStatus.Connecting // Reconnecting indicator
                }
                delay(1000)
            }
        }
    }

    private fun checkAlarm(data: StepData) {
        if (data.target > 0 && data.steps >= data.target) {
            if (lastAlarmedTarget != data.target) {
                triggerAlarm()
                lastAlarmedTarget = data.target
                _showTargetReachedDialog.value = true
            }
        } else if (data.steps < data.target) {
            if (lastAlarmedTarget != -1 && data.steps < data.target) {
                lastAlarmedTarget = -1
            }
        }
    }

    private fun triggerAlarm() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val r = RingtoneManager.getRingtone(getApplication(), notification)
            r.play()
        } catch (e: Exception) {
            Log.e(TAG, "Alarm failed", e)
        }
    }

    fun dismissTargetDialog() {
        _showTargetReachedDialog.value = false
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun updateTarget(newTarget: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (newTarget <= 0) {
            onError("Target must be greater than zero.")
            return
        }

        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { api.updateTarget(TargetRequest(newTarget)) }
                _stepData.value = data
                lastAlarmedTarget = -1
                onSuccess()
            } catch (e: Exception) {
                onError("Failed to update target. Check connection.")
            }
        }
    }

    fun resetSteps(onSuccess: () -> Unit, onError: (String) -> Unit) {
        _isResetting.value = true
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { api.resetSteps() }
                _stepData.value = data
                lastAlarmedTarget = -1
                _isResetting.value = false
                onSuccess()
            } catch (e: Exception) {
                _isResetting.value = false
                onError("Failed to reset steps. Check connection.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.bindProcessToNetwork(null)
    }
}

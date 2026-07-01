package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppScreen
import com.example.data.model.InspectionSession
import com.example.data.model.TrackMeasurement
import com.example.data.repository.TrackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TrackViewModel(val repository: TrackRepository) : ViewModel() {

    // Navigation State
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Sessions List
    val sessions: StateFlow<List<InspectionSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Active Session State
    private val _selectedSession = MutableStateFlow<InspectionSession?>(null)
    val selectedSession: StateFlow<InspectionSession?> = _selectedSession.asStateFlow()

    // Active Session Measurements List
    private val _measurements = MutableStateFlow<List<TrackMeasurement>>(emptyList())
    val measurements: StateFlow<List<TrackMeasurement>> = _measurements.asStateFlow()

    // Setup Form State (Temporary, used when editing or creating)
    private val _sessionInEdit = MutableStateFlow<InspectionSession?>(null)
    val sessionInEdit: StateFlow<InspectionSession?> = _sessionInEdit.asStateFlow()

    private var measurementsJob: Job? = null

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectSession(session: InspectionSession) {
        _selectedSession.value = session
        measurementsJob?.cancel()
        measurementsJob = viewModelScope.launch {
            repository.getMeasurementsForSession(session.id).collect { list ->
                _measurements.value = list
            }
        }
    }

    fun clearSelectedSession() {
        _selectedSession.value = null
        _measurements.value = emptyList()
        measurementsJob?.cancel()
    }

    // --- Session CRUD Actions ---

    fun prepareNewSession() {
        _sessionInEdit.value = InspectionSession(sectionName = "")
        navigateTo(AppScreen.SESSION_SETUP)
    }

    fun prepareEditSession(session: InspectionSession) {
        _sessionInEdit.value = session
        navigateTo(AppScreen.SESSION_SETUP)
    }

    fun saveSession(
        sectionName: String,
        startKm: Double,
        intervalMeters: Double,
        nominalGauge: Double,
        isCurve: Boolean,
        designSe: Double,
        remarks: String
    ) {
        viewModelScope.launch {
            val inEdit = _sessionInEdit.value
            if (inEdit != null) {
                if (inEdit.id == 0L) {
                    // Create New
                    val newSession = InspectionSession(
                        sectionName = sectionName.ifBlank { "Unspecified Section" },
                        startKm = startKm,
                        intervalMeters = intervalMeters,
                        nominalGauge = nominalGauge,
                        isCurve = isCurve,
                        designSuperelevation = if (isCurve) designSe else 0.0,
                        remarks = remarks
                    )
                    val sessionId = repository.insertSession(newSession)
                    // Select the newly created session
                    val createdSession = newSession.copy(id = sessionId)
                    selectSession(createdSession)
                    navigateTo(AppScreen.TRACK_RECORDER)
                } else {
                    // Update Existing
                    val updatedSession = inEdit.copy(
                        sectionName = sectionName.ifBlank { "Unspecified Section" },
                        startKm = startKm,
                        intervalMeters = intervalMeters,
                        nominalGauge = nominalGauge,
                        isCurve = isCurve,
                        designSuperelevation = if (isCurve) designSe else 0.0,
                        remarks = remarks
                    )
                    repository.updateSession(updatedSession)
                    _selectedSession.value = updatedSession
                    navigateTo(AppScreen.TRACK_RECORDER)
                }
            }
            _sessionInEdit.value = null
        }
    }

    fun deleteSession(session: InspectionSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_selectedSession.value?.id == session.id) {
                clearSelectedSession()
            }
        }
    }

    suspend fun insertSessionDirectly(session: InspectionSession): Long {
        return repository.insertSession(session)
    }

    suspend fun updateSessionDirectly(session: InspectionSession) {
        repository.updateSession(session)
    }

    // --- Measurement Actions ---

    fun addMeasurement(crossLevel: Double, gauge: Double, remarks: String) {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            val nextIndex = _measurements.value.size
            val distance = nextIndex * session.intervalMeters
            val measurement = TrackMeasurement(
                sessionId = session.id,
                stationIndex = nextIndex,
                distanceMeters = distance,
                crossLevel = crossLevel,
                gauge = gauge,
                remarks = remarks
            )
            repository.insertMeasurement(measurement)
        }
    }

    fun addMeasurementGaugeOnly(session: InspectionSession, gauge: Double, remarks: String, stationIdx: Int, distMeters: Double) {
        viewModelScope.launch {
            val measurement = TrackMeasurement(
                sessionId = session.id,
                stationIndex = stationIdx,
                distanceMeters = distMeters,
                crossLevel = 0.0,
                gauge = gauge,
                remarks = remarks
            )
            repository.insertMeasurement(measurement)
        }
    }

    fun updateMeasurement(measurement: TrackMeasurement) {
        viewModelScope.launch {
            repository.updateMeasurement(measurement)
        }
    }

    fun deleteMeasurement(measurement: TrackMeasurement) {
        viewModelScope.launch {
            repository.deleteMeasurementById(measurement.id)
            // Re-order remaining measurements to keep indexing seamless
            val remaining = _measurements.value.filter { it.id != measurement.id }
            val session = _selectedSession.value ?: return@launch
            val reordered = remaining.mapIndexed { idx, m ->
                m.copy(
                    stationIndex = idx,
                    distanceMeters = idx * session.intervalMeters
                )
            }
            repository.insertMeasurements(reordered)
        }
    }

    fun generateSampleDataForSession() {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            repository.deleteMeasurementsForSession(session.id)
            val list = mutableListOf<TrackMeasurement>()
            val nominalG = session.nominalGauge
            val designCl = session.designSuperelevation

            // Generate 12 stations of measurements (e.g. 36 meters)
            for (i in 0 until 12) {
                // Add some realistic wave deviations
                val clDev = when (i) {
                    2 -> 4.5
                    5 -> -6.0
                    8 -> 11.0  // Out of tolerance (Urgent Attention)
                    10 -> -1.0
                    else -> (i % 3 - 1) * 1.5 // general small variations
                }
                val gDev = when (i) {
                    3 -> 5.0
                    5 -> -3.0
                    8 -> 14.0  // Wide gauge
                    11 -> -7.0 // Tight gauge
                    else -> (i % 2) * 1.5
                }

                val remarks = when (i) {
                    5 -> "Bridge Approach"
                    8 -> "Joint sleeper low"
                    11 -> "Corroded liner"
                    else -> ""
                }

                list.add(
                    TrackMeasurement(
                        sessionId = session.id,
                        stationIndex = i,
                        distanceMeters = i * session.intervalMeters,
                        crossLevel = designCl + clDev,
                        gauge = nominalG + gDev,
                        remarks = remarks
                    )
                )
            }
            repository.insertMeasurements(list)
        }
    }

    fun clearAllMeasurementsForSession() {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            repository.deleteMeasurementsForSession(session.id)
        }
    }
}

class TrackViewModelFactory(private val repository: TrackRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrackViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

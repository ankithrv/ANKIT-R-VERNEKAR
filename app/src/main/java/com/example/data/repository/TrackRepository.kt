package com.example.data.repository

import com.example.data.dao.TrackDao
import com.example.data.model.InspectionSession
import com.example.data.model.TrackMeasurement
import kotlinx.coroutines.flow.Flow

class TrackRepository(private val trackDao: TrackDao) {

    val allSessions: Flow<List<InspectionSession>> = trackDao.getAllSessions()

    fun getSessionById(sessionId: Long): Flow<InspectionSession?> {
        return trackDao.getSessionById(sessionId)
    }

    fun getMeasurementsForSession(sessionId: Long): Flow<List<TrackMeasurement>> {
        return trackDao.getMeasurementsForSession(sessionId)
    }

    suspend fun insertSession(session: InspectionSession): Long {
        return trackDao.insertSession(session)
    }

    suspend fun updateSession(session: InspectionSession) {
        trackDao.updateSession(session)
    }

    suspend fun deleteSession(session: InspectionSession) {
        trackDao.deleteSession(session)
    }

    suspend fun insertMeasurement(measurement: TrackMeasurement): Long {
        return trackDao.insertMeasurement(measurement)
    }

    suspend fun insertMeasurements(measurements: List<TrackMeasurement>) {
        trackDao.insertMeasurements(measurements)
    }

    suspend fun updateMeasurement(measurement: TrackMeasurement) {
        trackDao.updateMeasurement(measurement)
    }

    suspend fun deleteMeasurementById(measurementId: Long) {
        trackDao.deleteMeasurementById(measurementId)
    }

    suspend fun deleteMeasurementsForSession(sessionId: Long) {
        trackDao.deleteMeasurementsForSession(sessionId)
    }
}

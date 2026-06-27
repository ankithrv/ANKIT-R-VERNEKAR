package com.example.data.dao

import androidx.room.*
import com.example.data.model.InspectionSession
import com.example.data.model.TrackMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    // --- Sessions ---
    @Query("SELECT * FROM inspection_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<InspectionSession>>

    @Query("SELECT * FROM inspection_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: Long): Flow<InspectionSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InspectionSession): Long

    @Update
    suspend fun updateSession(session: InspectionSession)

    @Delete
    suspend fun deleteSession(session: InspectionSession)

    // --- Measurements ---
    @Query("SELECT * FROM track_measurements WHERE sessionId = :sessionId ORDER BY stationIndex ASC")
    fun getMeasurementsForSession(sessionId: Long): Flow<List<TrackMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: TrackMeasurement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurements(measurements: List<TrackMeasurement>)

    @Update
    suspend fun updateMeasurement(measurement: TrackMeasurement)

    @Query("DELETE FROM track_measurements WHERE id = :measurementId")
    suspend fun deleteMeasurementById(measurementId: Long)

    @Query("DELETE FROM track_measurements WHERE sessionId = :sessionId")
    suspend fun deleteMeasurementsForSession(sessionId: Long)
}

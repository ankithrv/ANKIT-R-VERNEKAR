package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_measurements",
    foreignKeys = [
        ForeignKey(
            entity = InspectionSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class TrackMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val stationIndex: Int,
    val distanceMeters: Double,
    val crossLevel: Double, // Measured cross level in mm
    val gauge: Double,      // Measured gauge in mm
    val remarks: String = ""
)

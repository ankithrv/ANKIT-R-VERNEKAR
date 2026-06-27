package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspection_sessions")
data class InspectionSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionName: String,
    val date: Long = System.currentTimeMillis(),
    val startKm: Double = 0.0,
    val intervalMeters: Double = 3.0,
    val nominalGauge: Double = 1676.0, // Broad Gauge: 1676mm, Standard: 1435mm
    val isCurve: Boolean = false,
    val designSuperelevation: Double = 0.0, // designed superelevation in mm (if curve)
    val remarks: String = ""
)

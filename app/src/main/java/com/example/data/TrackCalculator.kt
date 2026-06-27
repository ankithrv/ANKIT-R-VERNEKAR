package com.example.data

import kotlin.math.abs
import kotlin.math.sqrt

enum class TrackStatus(val label: String, val colorHex: String, val level: Int) {
    EXCELLENT("Excellent", "#388E3C", 1),         // Dark green
    GOOD("Good", "#689F38", 2),                  // Light green
    TOLERABLE("Tolerable", "#FBC02D", 3),         // Muted yellow
    URGENT_ATTENTION("Urgent Attention", "#E65100", 4), // Muted orange
    EMERGENCY("Emergency", "#D84315", 5);        // Red-orange/Crimson

    companion object {
        fun getWorstOf(vararg statuses: TrackStatus): TrackStatus {
            return statuses.maxByOrNull { it.level } ?: EXCELLENT
        }
    }
}

object TrackCalculator {

    // Twist interpretation based on mm/meter
    fun interpretTwist(twistMmPerMeter: Double): TrackStatus {
        return when {
            twistMmPerMeter < 1.0 -> TrackStatus.EXCELLENT
            twistMmPerMeter < 2.0 -> TrackStatus.GOOD
            twistMmPerMeter < 3.5 -> TrackStatus.TOLERABLE
            twistMmPerMeter < 5.0 -> TrackStatus.URGENT_ATTENTION
            else -> TrackStatus.EMERGENCY
        }
    }

    // Cross Level deviation interpretation based on mm deviation from design superelevation
    fun interpretCrossLevel(measuredCl: Double, designCl: Double): TrackStatus {
        val deviation = abs(measuredCl - designCl)
        return when {
            deviation <= 3.0 -> TrackStatus.EXCELLENT
            deviation <= 6.0 -> TrackStatus.GOOD
            deviation <= 10.0 -> TrackStatus.TOLERABLE
            deviation <= 15.0 -> TrackStatus.URGENT_ATTENTION
            else -> TrackStatus.EMERGENCY
        }
    }

    // Gauge interpretation based on deviation from nominal gauge
    // BG (Broad Gauge): 1676 mm. Standard limits are typically: -6mm to +15mm
    fun interpretGauge(measuredGauge: Double, nominalGauge: Double): TrackStatus {
        val deviation = measuredGauge - nominalGauge
        return when {
            // Tight gauge is dangerous (climbing)
            deviation < -8.0 -> TrackStatus.EMERGENCY
            deviation < -5.0 -> TrackStatus.URGENT_ATTENTION
            deviation < -2.0 -> TrackStatus.TOLERABLE
            // Slack gauge is dangerous (wheel drop)
            deviation > 20.0 -> TrackStatus.EMERGENCY
            deviation > 12.0 -> TrackStatus.URGENT_ATTENTION
            deviation > 6.0 -> TrackStatus.TOLERABLE
            // Normal range
            deviation >= -2.0 && deviation <= 3.0 -> TrackStatus.EXCELLENT
            else -> TrackStatus.GOOD
        }
    }

    // Calculate Standard Deviation for a list of values
    fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.sum() / values.size
        return sqrt(variance)
    }

    // Get maintenance suggestions based on values
    fun getMaintenanceRecommendation(
        worstTwist: Double,
        worstClDev: Double,
        worstGaugeDev: Double,
        avgTwist: Double,
        clSD: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (worstTwist >= 5.0) {
            recommendations.add("🚨 EMERGENCY: Severe twist defect detected (>= 5.0 mm/m). Impose speed restriction (e.g. 20-30 km/h) immediately and pack/tamp sleepers manually.")
        } else if (worstTwist >= 3.5) {
            recommendations.add("⚠️ URGENT: Track twist exceeds urgent limits (3.5 - 5.0 mm/m). Schedule tamping and packing within 48 hours to prevent derailment risk.")
        } else if (worstTwist >= 2.0 && clSD > 4.0) {
            recommendations.add("🛠️ ROUTINE MAINTENANCE: Twist exceeds 2.0 mm/m with uneven settlement. Plan through pack tamping on the next maintenance block.")
        }

        if (abs(worstClDev) >= 15.0) {
            recommendations.add("🚨 EMERGENCY: Cross-level deviation exceeds 15mm. Immediate attention required to restore cross-level symmetry, especially on curve transitions.")
        } else if (abs(worstClDev) >= 10.0) {
            recommendations.add("⚠️ URGENT: High cross-level error (10-15mm). Align rails and check for ballast pocket failures or packing issues under joint sleepers.")
        }

        val maxGaugeError = worstGaugeDev // can be positive or negative
        if (maxGaugeError > 15.0 || maxGaugeError < -8.0) {
            recommendations.add("🚨 EMERGENCY: Gauge deviation is critical. Gauge widening or rail sluing is mandatory. Check for loose spikes, damaged liners, or broken rail pads.")
        } else if (maxGaugeError > 8.0 || maxGaugeError < -5.0) {
            recommendations.add("⚠️ URGENT: Gauge exceeds permissible tolerances. Plan regauging or sleeper replacement at affected stations.")
        }

        if (recommendations.isEmpty()) {
            if (clSD > 3.0) {
                recommendations.add("✨ Track is generally safe, but Cross-Level Standard Deviation (SD = ${String.format("%.2f", clSD)} mm) is slightly high. Plan mechanical tamping to restore general track geometry.")
            } else {
                recommendations.add("✅ TRACK SAFE: All parameters (Twist, Cross-Level, and Gauge) are within healthy limits. Regular monitoring only.")
            }
        }

        return recommendations
    }
}

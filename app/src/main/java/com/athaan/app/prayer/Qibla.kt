package com.athaan.app.prayer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Qibla {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    /** Direction of the Kaaba in degrees clockwise from true north. */
    fun bearing(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dl)
        val x = cos(p1) * Math.tan(p2) - sin(p1) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Great-circle distance to the Kaaba in km. */
    fun distanceKm(lat: Double, lng: Double): Double {
        val r = 6371.0
        val p1 = Math.toRadians(lat); val p2 = Math.toRadians(KAABA_LAT)
        val dp = p2 - p1; val dl = Math.toRadians(KAABA_LNG - lng)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

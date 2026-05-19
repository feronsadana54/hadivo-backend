package com.hadivo.attendance.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GeoUtilsTest {

    @Test
    fun `distance from a point to itself is zero`() {
        val distance = GeoUtils.distanceMeters(-6.2, 106.8, -6.2, 106.8)
        assertThat(distance).isEqualTo(0.0)
    }

    @Test
    fun `Jakarta to Bandung is roughly 120km`() {
        val jakarta = Pair(-6.2088, 106.8456)
        val bandung = Pair(-6.9175, 107.6191)
        val distance = GeoUtils.distanceMeters(jakarta.first, jakarta.second, bandung.first, bandung.second)
        assertThat(distance).isBetween(110_000.0, 130_000.0)
    }

    @ParameterizedTest
    @CsvSource(
        "0,    0,    0,    0.001,  111",
        "-6.2, 106.8, -6.201, 106.8, 111",
    )
    fun `tiny offsets translate to expected meters`(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        approxMeters: Int,
    ) {
        val distance = GeoUtils.distanceMeters(lat1, lon1, lat2, lon2)
        assertThat(distance).isBetween(approxMeters - 5.0, approxMeters + 5.0)
    }

    @Test
    fun `isWithinRadius is true at the center`() {
        assertThat(GeoUtils.isWithinRadius(-6.2, 106.8, -6.2, 106.8, 50)).isTrue()
    }

    @Test
    fun `isWithinRadius is true at the boundary`() {
        val centerLat = -6.2
        val centerLon = 106.8
        val pointLat = centerLat + 0.0004
        val pointLon = centerLon
        assertThat(GeoUtils.isWithinRadius(pointLat, pointLon, centerLat, centerLon, 100)).isTrue()
    }

    @Test
    fun `isWithinRadius is false when clearly outside`() {
        assertThat(GeoUtils.isWithinRadius(-6.21, 106.81, -6.2, 106.8, 100)).isFalse()
    }
}

package com.zynexbd.crmsolution.utils

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.zynexbd.crmsolution.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RouteResult(
    val points: List<LatLng>,
    val distanceKm: Double,
    val isSnappedToRoad: Boolean
)

data class ProcessedSegmentRoute(
    val segmentId: Int,
    val points: List<LatLng>,
    val distanceKm: Double,
    val isSnappedToRoad: Boolean,
    val rawPoints: List<LatLng>,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

data class MultiSegmentRouteResult(
    val segmentRoutes: List<ProcessedSegmentRoute>,
    val totalDistanceKm: Double,
    val totalRoadDistanceKm: Double,
    val totalRawDistanceKm: Double,
    val isFullySnapped: Boolean,
    val snappedCount: Int,
    val fallbackCount: Int
)

object RoadRoutingHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Routes each continuous movement segment independently to avoid drawing false lines
     * across stops, gaps, or stationary periods.
     * Prioritizes Google Directions API (with real-time road & traffic routing),
     * with automatic fallback to OSRM and raw GPS points.
     */
    suspend fun routeMovementSegments(segments: List<MovementSegment>): MultiSegmentRouteResult = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) {
            return@withContext MultiSegmentRouteResult(
                segmentRoutes = emptyList(),
                totalDistanceKm = 0.0,
                totalRoadDistanceKm = 0.0,
                totalRawDistanceKm = 0.0,
                isFullySnapped = false,
                snappedCount = 0,
                fallbackCount = 0
            )
        }

        val processedRoutes = mutableListOf<ProcessedSegmentRoute>()
        var totalRoadDist = 0.0
        var totalRawDist = 0.0
        var snappedCount = 0
        var fallbackCount = 0

        for (seg in segments) {
            val rawLatLngs = seg.points.map { it.latLng }
            val singleResult = snapSegmentToRoad(rawLatLngs)

            if (singleResult.isSnappedToRoad) {
                snappedCount++
                totalRoadDist += singleResult.distanceKm
            } else {
                fallbackCount++
                totalRawDist += singleResult.distanceKm
            }

            processedRoutes.add(
                ProcessedSegmentRoute(
                    segmentId = seg.id,
                    points = singleResult.points,
                    distanceKm = singleResult.distanceKm,
                    isSnappedToRoad = singleResult.isSnappedToRoad,
                    rawPoints = rawLatLngs,
                    startTimeMillis = seg.startTimeMillis,
                    endTimeMillis = seg.endTimeMillis
                )
            )
        }

        val totalCombinedDistance = totalRoadDist + totalRawDist
        val isFullySnapped = fallbackCount == 0 && snappedCount > 0

        return@withContext MultiSegmentRouteResult(
            segmentRoutes = processedRoutes,
            totalDistanceKm = totalCombinedDistance,
            totalRoadDistanceKm = totalRoadDist,
            totalRawDistanceKm = totalRawDist,
            isFullySnapped = isFullySnapped,
            snappedCount = snappedCount,
            fallbackCount = fallbackCount
        )
    }

    /**
     * Snaps a single segment coordinates list to roads.
     * Order of execution:
     * 1. Google Directions API (Live road network & turn-by-turn traffic path)
     * 2. OSRM fallback (OpenStreetMap road network)
     * 3. Raw GPS points (Graceful offline fallback)
     */
    private fun snapSegmentToRoad(rawPoints: List<LatLng>): RouteResult {
        if (rawPoints.size < 2) {
            return RouteResult(rawPoints, 0.0, false)
        }

        // Filter micro-jitter on consecutive points (< 15 meters apart)
        val filtered = filterJitter(rawPoints)
        if (filtered.size < 2) {
            return RouteResult(rawPoints, calculateDirectDistance(rawPoints), false)
        }

        // 1. Try Google Directions API
        try {
            val googleResult = snapViaGoogleDirections(filtered)
            if (googleResult != null && googleResult.isSnappedToRoad) {
                return googleResult
            }
        } catch (_: Exception) {
            // Google Directions failed, continue to OSRM
        }

        // 2. Try OSRM fallback
        try {
            val osrmResult = snapViaOsrm(filtered)
            if (osrmResult != null && osrmResult.isSnappedToRoad) {
                return osrmResult
            }
        } catch (_: Exception) {
            // OSRM failed, continue to raw points
        }

        // 3. Fallback to raw GPS points
        return RouteResult(
            points = rawPoints,
            distanceKm = calculateDirectDistance(rawPoints),
            isSnappedToRoad = false
        )
    }

    /**
     * Google Directions API road snapping with traffic model and intermediate waypoints.
     */
    private fun snapViaGoogleDirections(points: List<LatLng>): RouteResult? {
        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank()) return null

        val origin = points.first()
        val destination = points.last()

        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"

        // Build intermediate waypoints (maximum 8 via waypoints to keep response fast and reliable)
        val intermediatePoints = if (points.size > 2) {
            subsamplePoints(points.subList(1, points.size - 1), 8)
        } else {
            emptyList()
        }

        val waypointsParam = if (intermediatePoints.isNotEmpty()) {
            "&waypoints=" + intermediatePoints.joinToString("|") { "via:${it.latitude},${it.longitude}" }
        } else {
            ""
        }

        val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=$originStr" +
                "&destination=$destStr" +
                waypointsParam +
                "&mode=driving" +
                "&departure_time=now" +
                "&traffic_model=best_guess" +
                "&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LiveTrackingApp/2.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val bodyStr = response.body?.string() ?: return null
        val json = JSONObject(bodyStr)
        val status = json.optString("status")

        if (status.equals("OK", ignoreCase = true)) {
            val routes = json.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val firstRoute = routes.getJSONObject(0)
                val overviewPolyline = firstRoute.optJSONObject("overview_polyline")
                val encodedPoints = overviewPolyline?.optString("points")

                if (!encodedPoints.isNullOrBlank()) {
                    val decodedPoints = decodePolyline(encodedPoints)
                    if (decodedPoints.isNotEmpty()) {
                        // Compute total distance across legs
                        var totalMeters = 0.0
                        val legs = firstRoute.optJSONArray("legs")
                        if (legs != null) {
                            for (i in 0 until legs.length()) {
                                val leg = legs.getJSONObject(i)
                                val distObj = leg.optJSONObject("distance")
                                totalMeters += distObj?.optDouble("value", 0.0) ?: 0.0
                            }
                        }

                        val distanceKm = if (totalMeters > 0) totalMeters / 1000.0 else calculateDirectDistance(decodedPoints)

                        return RouteResult(
                            points = decodedPoints,
                            distanceKm = distanceKm,
                            isSnappedToRoad = true
                        )
                    }
                }
            }
        }

        return null
    }

    /**
     * OSRM road snapping as secondary fallback.
     */
    private fun snapViaOsrm(points: List<LatLng>): RouteResult? {
        val pointsToRoute = if (points.size > 25) {
            subsamplePoints(points, 25)
        } else {
            points
        }

        val coordsParam = pointsToRoute.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coordsParam?overview=full&geometries=geojson"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LiveTrackingSystemApp/2.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val bodyStr = response.body?.string() ?: return null
        val json = JSONObject(bodyStr)
        val code = json.optString("code")

        if (code.equals("Ok", ignoreCase = true)) {
            val routes = json.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val firstRoute = routes.getJSONObject(0)
                val distanceMeters = firstRoute.optDouble("distance", 0.0)
                val geometry = firstRoute.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")

                if (coordinates != null && coordinates.length() > 0) {
                    val snappedPoints = mutableListOf<LatLng>()
                    for (i in 0 until coordinates.length()) {
                        val coordPair = coordinates.getJSONArray(i)
                        val lng = coordPair.getDouble(0)
                        val lat = coordPair.getDouble(1)
                        snappedPoints.add(LatLng(lat, lng))
                    }

                    if (snappedPoints.isNotEmpty()) {
                        return RouteResult(
                            points = snappedPoints,
                            distanceKm = distanceMeters / 1000.0,
                            isSnappedToRoad = true
                        )
                    }
                }
            }
        }

        return null
    }

    /**
     * Decodes standard Google Encoded Polyline algorithm into LatLng points.
     */
    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun filterJitter(rawPoints: List<LatLng>): List<LatLng> {
        if (rawPoints.size <= 2) return rawPoints
        val filtered = mutableListOf<LatLng>()
        filtered.add(rawPoints.first())
        for (i in 1 until rawPoints.size - 1) {
            val prev = filtered.last()
            val curr = rawPoints[i]
            val results = FloatArray(1)
            Location.distanceBetween(prev.latitude, prev.longitude, curr.latitude, curr.longitude, results)
            if (results[0] >= GpsProcessingConfig.MIN_JITTER_DISTANCE_METERS) {
                filtered.add(curr)
            }
        }
        filtered.add(rawPoints.last())
        return filtered
    }

    private fun calculateDirectDistance(points: List<LatLng>): Double {
        var rawDist = 0.0
        for (i in 1 until points.size) {
            val results = FloatArray(1)
            Location.distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
                results
            )
            rawDist += results[0]
        }
        return rawDist / 1000.0
    }

    private fun subsamplePoints(points: List<LatLng>, maxPoints: Int): List<LatLng> {
        if (points.size <= maxPoints) return points
        val step = points.size.toDouble() / (maxPoints - 1)
        val result = mutableListOf<LatLng>()
        result.add(points.first())
        for (i in 1 until maxPoints - 1) {
            val idx = (i * step).toInt().coerceIn(1, points.size - 2)
            result.add(points[idx])
        }
        result.add(points.last())
        return result
    }

    /**
     * Legacy single route method preserved for backward compatibility.
     */
    suspend fun getRoadSnappedRoute(rawPoints: List<LatLng>): RouteResult = withContext(Dispatchers.IO) {
        return@withContext snapSegmentToRoad(rawPoints)
    }
}

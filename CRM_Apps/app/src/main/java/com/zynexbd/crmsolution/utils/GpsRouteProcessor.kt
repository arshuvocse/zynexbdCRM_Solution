package com.zynexbd.crmsolution.utils

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.zynexbd.crmsolution.models.Customer
import com.zynexbd.crmsolution.models.LocationResponse
import com.zynexbd.crmsolution.models.OfficeLocation
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class PlaceType {
    CUSTOMER,
    OFFICE,
    UNKNOWN
}

enum class TimelineEventType {
    START,
    MOVEMENT,
    CUSTOMER_VISIT,
    OFFICE_VISIT,
    LONG_STOP,
    SHORT_STOP,
    GPS_GAP,
    LAST_POINT
}

data class ProcessedPoint(
    val latLng: LatLng,
    val timeMillis: Long,
    val timeIso: String,
    val accuracy: Double?,
    val speed: Double?,
    val battery: Int?,
    val bearing: Double?,
    val originalResponse: LocationResponse
)

data class MovementSegment(
    val id: Int,
    val points: List<ProcessedPoint>,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Long,
    val rawDistanceKm: Double
)

data class DetectedStop(
    val id: Int,
    val centroid: LatLng,
    val arrivalTimeMillis: Long,
    val departureTimeMillis: Long,
    val durationMinutes: Long,
    val points: List<ProcessedPoint>,
    var matchedPlaceName: String? = null,
    var matchedPlaceType: PlaceType = PlaceType.UNKNOWN,
    var matchedPlaceDetails: String? = null
)

data class GpsGap(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Long,
    val fromPoint: LatLng,
    val toPoint: LatLng
)

data class TimelineEvent(
    val id: String,
    val type: TimelineEventType,
    val timeStr: String,
    val title: String,
    val description: String,
    val badgeText: String? = null,
    val durationMinutes: Long? = null,
    val latLng: LatLng,
    val battery: Int? = null,
    val speed: Double? = null,
    val distanceKm: Double? = null,
    val movementSegment: MovementSegment? = null,
    val stop: DetectedStop? = null
)

data class FullDayRouteSummary(
    val userName: String,
    val dateStr: String,
    val startTimeStr: String,
    val endTimeStr: String,
    var totalDistanceKm: Double = 0.0,
    var rawGpsDistanceKm: Double = 0.0,
    var roadDistanceKm: Double = 0.0,
    val totalMovingMinutes: Long,
    val totalStoppedMinutes: Long,
    val visitCount: Int,
    val movementSegmentCount: Int,
    val gpsGapCount: Int,
    val totalGpsGapMinutes: Long,
    val lastBattery: Int?,
    var routeModeTag: String = "📍 GPS Fallback",
    val validPointsCount: Int,
    val rawPointsCount: Int,
    var startAddress: String? = null,
    var endAddress: String? = null
)

data class FullDayProcessResult(
    val validPoints: List<ProcessedPoint>,
    val movementSegments: List<MovementSegment>,
    val stops: List<DetectedStop>,
    val gaps: List<GpsGap>,
    val timelineEvents: List<TimelineEvent>,
    val summary: FullDayRouteSummary
)

object GpsRouteProcessor {

    /**
     * Parses ISO-8601 UTC timestamp or fallback formats to epoch milliseconds.
     */
    fun parseTimestampToMillis(isoStr: String?): Long {
        if (isoStr.isNullOrBlank()) return 0L
        val cleanStr = isoStr.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(cleanStr)
                if (date != null) return date.time
            } catch (_: Exception) {
                // continue to next pattern
            }
        }
        return 0L
    }

    /**
     * Formats timestamp in milliseconds to user-friendly local time string (e.g. "09:42 AM").
     */
    fun formatLocalTime(millis: Long): String {
        if (millis <= 0L) return "--:--"
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    /**
     * Formats duration in minutes to string (e.g. "35 min" or "2h 15m").
     */
    fun formatDuration(minutes: Long): String {
        if (minutes < 1) return "< 1 min"
        val hours = minutes / 60
        val remainingMins = minutes % 60
        return if (hours > 0) {
            if (remainingMins > 0) "${hours}h ${remainingMins}m" else "${hours}h"
        } else {
            "${minutes} min"
        }
    }

    /**
     * Calculates geodesic distance in meters between two coordinates.
     */
    fun distanceBetweenMeters(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0]
    }

    /**
     * Main processing pipeline.
     * Takes raw LocationResponse list, customer master data, and office master data,
     * and performs accuracy filtering, jitter suppression, gap detection, stop/visit clustering,
     * place matching, and timeline generation.
     */
    fun process(
        userName: String,
        dateStr: String,
        rawLocations: List<LocationResponse>,
        customers: List<Customer> = emptyList(),
        offices: List<OfficeLocation> = emptyList()
    ): FullDayProcessResult {
        val rawCount = rawLocations.size
        if (rawLocations.isEmpty()) {
            return FullDayProcessResult(
                validPoints = emptyList(),
                movementSegments = emptyList(),
                stops = emptyList(),
                gaps = emptyList(),
                timelineEvents = emptyList(),
                summary = FullDayRouteSummary(
                    userName = userName,
                    dateStr = dateStr,
                    startTimeStr = "--:--",
                    endTimeStr = "--:--",
                    totalMovingMinutes = 0,
                    totalStoppedMinutes = 0,
                    visitCount = 0,
                    movementSegmentCount = 0,
                    gpsGapCount = 0,
                    totalGpsGapMinutes = 0,
                    lastBattery = null,
                    validPointsCount = 0,
                    rawPointsCount = 0
                )
            )
        }

        // 1. FILTERING: Accuracy, Coordinate validity & Chronological Sort
        val parsedList = rawLocations.mapNotNull { loc ->
            val lat = loc.latitude ?: return@mapNotNull null
            val lng = loc.longitude ?: return@mapNotNull null
            if (lat == 0.0 && lng == 0.0) return@mapNotNull null
            if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return@mapNotNull null

            val timeMillis = parseTimestampToMillis(loc.recordedAt)
            ProcessedPoint(
                latLng = LatLng(lat, lng),
                timeMillis = timeMillis,
                timeIso = loc.recordedAt.orEmpty(),
                accuracy = loc.accuracy,
                speed = loc.speed,
                battery = loc.deviceBattery,
                bearing = loc.bearing,
                originalResponse = loc
            )
        }.sortedBy { it.timeMillis }

        // Filter out extreme accuracy inaccuracy (keep points if accuracy is null or <= MAX_ACCURACY_METERS)
        val accuracyFiltered = parsedList.filter { pt ->
            val acc = pt.accuracy
            acc == null || acc <= GpsProcessingConfig.MAX_ACCURACY_METERS
        }

        // If filtering accuracy dropped everything, fall back to parsed list so user sees something
        val workingPoints = if (accuracyFiltered.isNotEmpty()) accuracyFiltered else parsedList

        // Filter out duplicate consecutive identical coordinates & impossible speed jumps
        val validPoints = mutableListOf<ProcessedPoint>()
        for (pt in workingPoints) {
            if (validPoints.isEmpty()) {
                validPoints.add(pt)
                continue
            }
            val last = validPoints.last()
            val distMeters = distanceBetweenMeters(last.latLng, pt.latLng)
            val timeDiffSec = (pt.timeMillis - last.timeMillis) / 1000.0

            // Skip identical duplicate
            if (distMeters < 1.0 && abs(timeDiffSec) < 10) {
                continue
            }

            // Check impossible speed jumps (> MAX_REALISTIC_SPEED_KMH) when time diff is small
            if (timeDiffSec in 1.0..3600.0) {
                val speedKmh = (distMeters / timeDiffSec) * 3.6
                if (speedKmh > GpsProcessingConfig.MAX_REALISTIC_SPEED_KMH && distMeters > 500) {
                    // Outlier GPS jump: ignore
                    continue
                }
            }

            validPoints.add(pt)
        }

        if (validPoints.isEmpty()) {
            return process(userName, dateStr, emptyList())
        }

        if (validPoints.size == 1) {
            val onlyPt = validPoints.first()
            val startPlace = matchLocationToPlace(onlyPt.latLng, customers, offices)
            val startLocName = startPlace?.first ?: "Recorded Location"
            val singleEvent = TimelineEvent(
                id = "single_point",
                type = TimelineEventType.LAST_POINT,
                timeStr = formatLocalTime(onlyPt.timeMillis),
                title = "🔴 Current / Recorded Point",
                description = "$startLocName (Accuracy: ${onlyPt.accuracy?.toInt() ?: "--"}m)",
                badgeText = if (onlyPt.battery != null) "🔋 ${onlyPt.battery}%" else null,
                latLng = onlyPt.latLng,
                battery = onlyPt.battery,
                speed = onlyPt.speed
            )
            return FullDayProcessResult(
                validPoints = validPoints,
                movementSegments = emptyList(),
                stops = emptyList(),
                gaps = emptyList(),
                timelineEvents = listOf(singleEvent),
                summary = FullDayRouteSummary(
                    userName = userName,
                    dateStr = dateStr,
                    startTimeStr = formatLocalTime(onlyPt.timeMillis),
                    endTimeStr = formatLocalTime(onlyPt.timeMillis),
                    totalDistanceKm = 0.0,
                    rawGpsDistanceKm = 0.0,
                    roadDistanceKm = 0.0,
                    totalMovingMinutes = 0,
                    totalStoppedMinutes = 0,
                    visitCount = 0,
                    movementSegmentCount = 0,
                    gpsGapCount = 0,
                    totalGpsGapMinutes = 0,
                    lastBattery = onlyPt.battery,
                    routeModeTag = "📍 1 Recorded Point",
                    validPointsCount = 1,
                    rawPointsCount = rawCount
                )
            )
        }

        // 2. TEMPORAL & SPATIAL SEGMENTATION: GPS Gaps, Stops & Movement Segments
        val detectedStops = mutableListOf<DetectedStop>()
        val detectedGaps = mutableListOf<GpsGap>()
        val movementSegments = mutableListOf<MovementSegment>()

        var currentStopPoints = mutableListOf<ProcessedPoint>()
        var currentMovePoints = mutableListOf<ProcessedPoint>()
        var segmentCounter = 1
        var stopCounter = 1

        fun flushMovement() {
            if (currentMovePoints.size >= 2) {
                var distM = 0.0
                for (i in 1 until currentMovePoints.size) {
                    distM += distanceBetweenMeters(currentMovePoints[i - 1].latLng, currentMovePoints[i].latLng)
                }
                val startT = currentMovePoints.first().timeMillis
                val endT = currentMovePoints.last().timeMillis
                val durMin = ((endT - startT) / (1000 * 60)).coerceAtLeast(1)

                // Only treat as moving segment if distance is notable (> 30m)
                if (distM >= 30.0) {
                    movementSegments.add(
                        MovementSegment(
                            id = segmentCounter++,
                            points = ArrayList(currentMovePoints),
                            startTimeMillis = startT,
                            endTimeMillis = endT,
                            durationMinutes = durMin,
                            rawDistanceKm = distM / 1000.0
                        )
                    )
                }
            }
            currentMovePoints.clear()
        }

        fun flushStop() {
            if (currentStopPoints.isNotEmpty()) {
                val startT = currentStopPoints.first().timeMillis
                val endT = currentStopPoints.last().timeMillis
                val durMin = ((endT - startT) / (1000 * 60)).coerceAtLeast(1)

                if (durMin >= GpsProcessingConfig.STOP_MIN_DURATION_MINUTES && currentStopPoints.size >= 2) {
                    // Compute centroid
                    var sumLat = 0.0
                    var sumLng = 0.0
                    for (pt in currentStopPoints) {
                        sumLat += pt.latLng.latitude
                        sumLng += pt.latLng.longitude
                    }
                    val centroid = LatLng(sumLat / currentStopPoints.size, sumLng / currentStopPoints.size)
                    val stopObj = DetectedStop(
                        id = stopCounter++,
                        centroid = centroid,
                        arrivalTimeMillis = startT,
                        departureTimeMillis = endT,
                        durationMinutes = durMin,
                        points = ArrayList(currentStopPoints)
                    )

                    // Match place
                    val matched = matchLocationToPlace(centroid, customers, offices)
                    if (matched != null) {
                        stopObj.matchedPlaceName = matched.first
                        stopObj.matchedPlaceType = matched.second
                        stopObj.matchedPlaceDetails = matched.third
                    }

                    detectedStops.add(stopObj)
                } else {
                    // Not long enough to be a stationary stop -> merge back into movement
                    currentMovePoints.addAll(currentStopPoints)
                }
            }
            currentStopPoints.clear()
        }

        var i = 0
        while (i < validPoints.size) {
            val curr = validPoints[i]

            // Check if there is a GPS gap from previous point
            if (i > 0) {
                val prev = validPoints[i - 1]
                val gapMinutes = (curr.timeMillis - prev.timeMillis) / (1000 * 60)
                if (gapMinutes >= GpsProcessingConfig.GPS_GAP_THRESHOLD_MINUTES) {
                    // Flush current segments before logging gap
                    flushStop()
                    flushMovement()

                    detectedGaps.add(
                        GpsGap(
                            startTimeMillis = prev.timeMillis,
                            endTimeMillis = curr.timeMillis,
                            durationMinutes = gapMinutes,
                            fromPoint = prev.latLng,
                            toPoint = curr.latLng
                        )
                    )
                }
            }

            // Cluster check for stationary stop
            var clusterEnd = i
            var clusterLatSum = curr.latLng.latitude
            var clusterLngSum = curr.latLng.longitude
            var count = 1

            for (j in (i + 1) until validPoints.size) {
                val nextPt = validPoints[j]
                val gapMin = (nextPt.timeMillis - validPoints[j - 1].timeMillis) / (1000 * 60)
                if (gapMin >= GpsProcessingConfig.GPS_GAP_THRESHOLD_MINUTES) break

                val centerLat = clusterLatSum / count
                val centerLng = clusterLngSum / count
                val dist = distanceBetweenMeters(LatLng(centerLat, centerLng), nextPt.latLng)

                if (dist <= GpsProcessingConfig.STOP_RADIUS_METERS) {
                    clusterEnd = j
                    clusterLatSum += nextPt.latLng.latitude
                    clusterLngSum += nextPt.latLng.longitude
                    count++
                } else {
                    break
                }
            }

            val clusterDurationMin = (validPoints[clusterEnd].timeMillis - validPoints[i].timeMillis) / (1000 * 60)
            if (count >= 2 && clusterDurationMin >= GpsProcessingConfig.STOP_MIN_DURATION_MINUTES) {
                // We found a stationary stop cluster from i to clusterEnd
                flushMovement()
                currentStopPoints.clear()
                for (k in i..clusterEnd) {
                    currentStopPoints.add(validPoints[k])
                }
                flushStop()
                // Connect departure point to next movement
                currentMovePoints.add(validPoints[clusterEnd])
                i = clusterEnd + 1
            } else {
                // Moving point
                currentMovePoints.add(curr)
                i++
            }
        }

        flushStop()
        flushMovement()

        // 3. GENERATE UNIFIED CHRONOLOGICAL TIMELINE
        val timelineEvents = mutableListOf<TimelineEvent>()

        // 🟢 START Event
        val startPt = validPoints.first()
        val startMatched = matchLocationToPlace(startPt.latLng, customers, offices)
        val startLocationName = startPt.originalResponse.locationAddress?.takeIf { it.isNotBlank() } ?: startMatched?.first ?: "Starting Location"
        val startBatteryText = if (startPt.battery != null) "🔋 ${startPt.battery}%" else null
        val startAccuracyText = if (startPt.accuracy != null) "GPS ±${startPt.accuracy.toInt()}m" else null
        val startDesc = if (!startPt.originalResponse.locationAddress.isNullOrBlank()) {
            "📍 ${startPt.originalResponse.locationAddress} | $startAccuracyText"
        } else {
            "$startLocationName | $startAccuracyText"
        }

        timelineEvents.add(
            TimelineEvent(
                id = "event_start",
                type = TimelineEventType.START,
                timeStr = formatLocalTime(startPt.timeMillis),
                title = "🟢 Journey Started",
                description = startDesc,
                badgeText = startBatteryText,
                latLng = startPt.latLng,
                battery = startPt.battery,
                speed = startPt.speed
            )
        )

        // Merge Movement Segments, Stops, and Gaps into chronological timeline
        data class TempEvent(val timeMillis: Long, val event: TimelineEvent)
        val tempEvents = mutableListOf<TempEvent>()

        for (seg in movementSegments) {
            val distFormatted = "%.2f km".format(seg.rawDistanceKm)
            val durFormatted = formatDuration(seg.durationMinutes)
            tempEvents.add(
                TempEvent(
                    timeMillis = seg.startTimeMillis,
                    event = TimelineEvent(
                        id = "seg_${seg.id}",
                        type = TimelineEventType.MOVEMENT,
                        timeStr = formatLocalTime(seg.startTimeMillis),
                        title = "🚗 Moving",
                        description = "Travel: $distFormatted ($durFormatted)",
                        badgeText = distFormatted,
                        durationMinutes = seg.durationMinutes,
                        latLng = seg.points.first().latLng,
                        distanceKm = seg.rawDistanceKm,
                        movementSegment = seg
                    )
                )
            )
        }

        for (stop in detectedStops) {
            val durFormatted = formatDuration(stop.durationMinutes)
            val (eventType, title) = when {
                stop.matchedPlaceType == PlaceType.CUSTOMER -> {
                    Pair(TimelineEventType.CUSTOMER_VISIT, "📍 Customer Visit: ${stop.matchedPlaceName}")
                }
                stop.matchedPlaceType == PlaceType.OFFICE -> {
                    Pair(TimelineEventType.OFFICE_VISIT, "🟠 Office Stay: ${stop.matchedPlaceName}")
                }
                stop.durationMinutes >= GpsProcessingConfig.LONG_STOP_THRESHOLD_MINUTES -> {
                    Pair(TimelineEventType.LONG_STOP, "⏸ Long Stop")
                }
                else -> {
                    Pair(TimelineEventType.SHORT_STOP, "⚪ Short Stop")
                }
            }

            val stopAddress = stop.points.firstOrNull { !it.originalResponse.locationAddress.isNullOrBlank() }?.originalResponse?.locationAddress
            val desc = buildString {
                if (!stopAddress.isNullOrBlank()) {
                    append("📍 ").append(stopAddress).append(" | ")
                } else if (!stop.matchedPlaceDetails.isNullOrBlank()) {
                    append(stop.matchedPlaceDetails).append(" | ")
                }
                append("Stayed: ").append(durFormatted)
                append(" (").append(formatLocalTime(stop.arrivalTimeMillis))
                append(" - ").append(formatLocalTime(stop.departureTimeMillis)).append(")")
            }

            tempEvents.add(
                TempEvent(
                    timeMillis = stop.arrivalTimeMillis,
                    event = TimelineEvent(
                        id = "stop_${stop.id}",
                        type = eventType,
                        timeStr = formatLocalTime(stop.arrivalTimeMillis),
                        title = title,
                        description = desc,
                        badgeText = durFormatted,
                        durationMinutes = stop.durationMinutes,
                        latLng = stop.centroid,
                        stop = stop
                    )
                )
            )
        }

        for (gap in detectedGaps) {
            val durFormatted = formatDuration(gap.durationMinutes)
            tempEvents.add(
                TempEvent(
                    timeMillis = gap.startTimeMillis,
                    event = TimelineEvent(
                        id = "gap_${gap.startTimeMillis}",
                        type = TimelineEventType.GPS_GAP,
                        timeStr = formatLocalTime(gap.startTimeMillis),
                        title = "⚠ GPS Signal Lost / No Data",
                        description = "Duration: $durFormatted (${formatLocalTime(gap.startTimeMillis)} - ${formatLocalTime(gap.endTimeMillis)})",
                        badgeText = "GAP $durFormatted",
                        durationMinutes = gap.durationMinutes,
                        latLng = gap.fromPoint
                    )
                )
            )
        }

        // Sort intermediate events by time
        tempEvents.sortBy { it.timeMillis }
        for (item in tempEvents) {
            // Avoid adding event at exact same start time as journey start if it overlaps
            timelineEvents.add(item.event)
        }

        // 🔴 LAST POINT Event
        val endPt = validPoints.last()
        val endMatched = matchLocationToPlace(endPt.latLng, customers, offices)
        val endLocationName = endPt.originalResponse.locationAddress?.takeIf { it.isNotBlank() } ?: endMatched?.first ?: "Last Recorded Location"
        val endBatteryText = if (endPt.battery != null) "🔋 ${endPt.battery}%" else null
        val endSpeedText = if (endPt.speed != null && endPt.speed > 0) "🚗 %.1f km/h".format(endPt.speed) else null

        val endDesc = buildString {
            if (!endPt.originalResponse.locationAddress.isNullOrBlank()) {
                append("📍 ").append(endPt.originalResponse.locationAddress)
            } else {
                append(endLocationName)
            }
            if (endSpeedText != null) append(" | ").append(endSpeedText)
            if (endPt.accuracy != null) append(" | GPS ±${endPt.accuracy.toInt()}m")
        }

        timelineEvents.add(
            TimelineEvent(
                id = "event_last",
                type = TimelineEventType.LAST_POINT,
                timeStr = formatLocalTime(endPt.timeMillis),
                title = "🔴 Last Recorded Location",
                description = endDesc,
                badgeText = endBatteryText,
                latLng = endPt.latLng,
                battery = endPt.battery,
                speed = endPt.speed
            )
        )

        // 4. FULL-DAY SUMMARY ANALYTICS
        var totalRawDistanceKm = 0.0
        var totalMovingMinutes = 0L
        for (seg in movementSegments) {
            totalRawDistanceKm += seg.rawDistanceKm
            totalMovingMinutes += seg.durationMinutes
        }

        var totalStoppedMinutes = 0L
        var visitsCount = 0
        for (stop in detectedStops) {
            totalStoppedMinutes += stop.durationMinutes
            if (stop.matchedPlaceType == PlaceType.CUSTOMER || stop.matchedPlaceType == PlaceType.OFFICE) {
                visitsCount++
            }
        }

        var totalGapMinutes = 0L
        for (gap in detectedGaps) {
            totalGapMinutes += gap.durationMinutes
        }

        val summary = FullDayRouteSummary(
            userName = userName,
            dateStr = dateStr,
            startTimeStr = formatLocalTime(startPt.timeMillis),
            endTimeStr = formatLocalTime(endPt.timeMillis),
            totalDistanceKm = totalRawDistanceKm,
            rawGpsDistanceKm = totalRawDistanceKm,
            roadDistanceKm = 0.0,
            totalMovingMinutes = totalMovingMinutes,
            totalStoppedMinutes = totalStoppedMinutes,
            visitCount = visitsCount,
            movementSegmentCount = movementSegments.size,
            gpsGapCount = detectedGaps.size,
            totalGpsGapMinutes = totalGapMinutes,
            lastBattery = endPt.battery,
            routeModeTag = "📍 GPS Path",
            validPointsCount = validPoints.size,
            rawPointsCount = rawCount,
            startAddress = startLocationName,
            endAddress = endLocationName
        )

        return FullDayProcessResult(
            validPoints = validPoints,
            movementSegments = movementSegments,
            stops = detectedStops,
            gaps = detectedGaps,
            timelineEvents = timelineEvents,
            summary = summary
        )
    }

    /**
     * Matches a coordinate to registered Customer or Office locations within proximity radius.
     * Returns Triple(Name, PlaceType, Address/Details) or null if unmatched.
     */
    fun matchLocationToPlace(
        latLng: LatLng,
        customers: List<Customer>,
        offices: List<OfficeLocation>
    ): Triple<String, PlaceType, String>? {
        // 1. Check Offices first
        var nearestOffice: OfficeLocation? = null
        var minOfficeDist = Float.MAX_VALUE

        for (office in offices) {
            if (!office.isActive) continue
            val dist = distanceBetweenMeters(latLng, LatLng(office.latitude, office.longitude))
            val threshold = if (office.radiusMeters > 0) office.radiusMeters.toFloat() else GpsProcessingConfig.DEFAULT_OFFICE_MATCH_RADIUS_METERS.toFloat()
            if (dist <= threshold && dist < minOfficeDist) {
                minOfficeDist = dist
                nearestOffice = office
            }
        }

        if (nearestOffice != null) {
            return Triple(nearestOffice.name, PlaceType.OFFICE, "Office Location")
        }

        // 2. Check Customers
        var nearestCustomer: Customer? = null
        var minCustomerDist = Float.MAX_VALUE

        for (customer in customers) {
            if (customer.latitude == 0.0 && customer.longitude == 0.0) continue
            val dist = distanceBetweenMeters(latLng, LatLng(customer.latitude, customer.longitude))
            if (dist <= GpsProcessingConfig.CUSTOMER_MATCH_RADIUS_METERS && dist < minCustomerDist) {
                minCustomerDist = dist
                nearestCustomer = customer
            }
        }

        if (nearestCustomer != null) {
            val addr = customerAddressOrFallback(nearestCustomer)
            return Triple(nearestCustomer.name, PlaceType.CUSTOMER, addr)
        }

        return null
    }

    private fun customerAddressOrFallback(customer: Customer): String {
        return when {
            customer.address.isNotBlank() -> customer.address
            customer.mobile.isNotBlank() -> "Mobile: ${customer.mobile}"
            else -> "Customer Visit"
        }
    }
}

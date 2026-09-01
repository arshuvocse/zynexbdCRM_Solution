package com.zynexbd.crmsolution.utils

/**
 * Centralized, configurable thresholds for GPS processing, stop detection,
 * GPS gap detection, and master location matching.
 */
object GpsProcessingConfig {

    /**
     * GPS accuracy threshold in meters.
     * Raw GPS points with accuracy values greater than this will be filtered out as low confidence.
     */
    const val MAX_ACCURACY_METERS = 100.0

    /**
     * Minimum distance in meters between consecutive GPS points in a movement segment
     * to eliminate micro-jitter while moving.
     */
    const val MIN_JITTER_DISTANCE_METERS = 15.0

    /**
     * Radius in meters within which consecutive points are considered stationary.
     */
    const val STOP_RADIUS_METERS = 45.0

    /**
     * Minimum stationary duration in minutes required to classify a cluster of points as a Stop / Visit.
     */
    const val STOP_MIN_DURATION_MINUTES = 3L

    /**
     * Duration in minutes to classify a Stop as a Long Stop vs Short Stop.
     */
    const val LONG_STOP_THRESHOLD_MINUTES = 15L

    /**
     * Time gap threshold in minutes between consecutive GPS pings.
     * If the interval exceeds this value, it is classified as GPS Signal Lost / Data Gap,
     * and routing polylines will NOT be drawn across the gap.
     */
    const val GPS_GAP_THRESHOLD_MINUTES = 15L

    /**
     * Proximity radius in meters to match a detected Stop or Waypoint to a registered Customer.
     */
    const val CUSTOMER_MATCH_RADIUS_METERS = 80.0

    /**
     * Default proximity radius in meters to match an Office if the office does not specify one.
     */
    const val DEFAULT_OFFICE_MATCH_RADIUS_METERS = 100.0

    /**
     * Maximum realistic travel speed (in km/h) to filter out impossible GPS teleportation jumps.
     */
    const val MAX_REALISTIC_SPEED_KMH = 160.0

    /**
     * Maximum waypoints sent in a single OSRM routing HTTP request to keep URL length safe.
     */
    const val MAX_OSRM_WAYPOINTS_PER_REQUEST = 60
}

package com.zynexbd.crmsolution.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale

/**
 * High-precision address resolver and formatter.
 * Automatically filters out Google Plus Codes (e.g., Q9VC+36H) and delivers
 * clean, human-readable street, road, sector/block, neighborhood and city names.
 */
object AddressHelper {

    // Matches Google Open Location Code / Plus Code patterns like Q9VC+36H, 7JVW+9F, etc.
    private val PLUS_CODE_PATTERN = Regex("^[2-9A-Z]{4,8}\\+[2-9A-Z0-9]*,?\\s*", RegexOption.IGNORE_CASE)
    private val EMBEDDED_PLUS_CODE = Regex("[2-9A-Z]{4,8}\\+[2-9A-Z0-9]*", RegexOption.IGNORE_CASE)

    /**
     * Formats a list of candidate addresses from Geocoder, picking the richest
     * and cleanest human-readable address.
     */
    fun formatAddressList(addressList: List<Address>?): String? {
        if (addressList.isNullOrEmpty()) return null

        // 1. Pick the best candidate that has actual street/area names rather than just a plus code
        var bestCandidate = addressList[0]
        for (candidate in addressList) {
            val line = candidate.getAddressLine(0)?.trim() ?: ""
            val hasPlusCode = EMBEDDED_PLUS_CODE.containsMatchIn(line)
            val hasStreetOrArea = !candidate.thoroughfare.isNullOrBlank() || !candidate.subLocality.isNullOrBlank()

            if (!hasPlusCode && hasStreetOrArea) {
                bestCandidate = candidate
                break
            }
            if (!hasPlusCode && EMBEDDED_PLUS_CODE.containsMatchIn(bestCandidate.getAddressLine(0) ?: "")) {
                bestCandidate = candidate
            }
        }

        // 2. Clean the full address line
        var fullLine = bestCandidate.getAddressLine(0)?.trim() ?: ""

        // Strip leading Plus Code (e.g. "Q9VC+36H, ঢাকা, বাংলাদেশ" -> "ঢাকা, বাংলাদেশ")
        fullLine = fullLine.replace(PLUS_CODE_PATTERN, "").trim()
        // Strip any remaining embedded plus code
        fullLine = fullLine.replace(EMBEDDED_PLUS_CODE, "").replace(", ,", ",").trim(' ', ',')

        // Strip trailing country name in English or Bengali
        fullLine = fullLine
            .replace(Regex(",?\\s*Bangladesh$", RegexOption.IGNORE_CASE), "")
            .replace(Regex(",?\\s*বাংলাদেশ$", RegexOption.IGNORE_CASE), "")
            .replace(Regex(",?\\s*BD$", RegexOption.IGNORE_CASE), "")
            .trim(' ', ',')

        // 3. Extract individual structural components across all candidates to assemble maximum detail
        val parts = mutableListOf<String>()

        val subThoroughfare = bestCandidate.subThoroughfare?.trim()?.takeIf { !EMBEDDED_PLUS_CODE.matches(it) }
        val thoroughfare = bestCandidate.thoroughfare?.trim()?.takeIf { !EMBEDDED_PLUS_CODE.matches(it) }
        val feature = bestCandidate.featureName?.trim()?.takeIf { !EMBEDDED_PLUS_CODE.matches(it) }
        
        // Find non-empty subLocality across candidates if bestCandidate lacks it
        val subLocality = bestCandidate.subLocality?.trim()
            ?: addressList.firstOrNull { !it.subLocality.isNullOrBlank() }?.subLocality?.trim()
            
        val locality = bestCandidate.locality?.trim()
            ?: addressList.firstOrNull { !it.locality.isNullOrBlank() }?.locality?.trim()

        val postalCode = bestCandidate.postalCode?.trim()

        // House & Road
        if (!subThoroughfare.isNullOrBlank() && !thoroughfare.isNullOrBlank()) {
            parts.add("$subThoroughfare $thoroughfare")
        } else if (!thoroughfare.isNullOrBlank()) {
            parts.add(thoroughfare)
        } else if (!feature.isNullOrBlank() && feature != locality && feature != subLocality) {
            parts.add(feature)
        }

        // SubLocality / Neighborhood / Area (e.g. Mirpur-10, Dhanmondi, Banani)
        if (!subLocality.isNullOrBlank() && !parts.any { it.contains(subLocality, ignoreCase = true) }) {
            parts.add(subLocality)
        }

        // Locality / City (e.g. Dhaka)
        if (!locality.isNullOrBlank() && !parts.any { it.contains(locality, ignoreCase = true) }) {
            if (!postalCode.isNullOrBlank()) {
                parts.add("$locality $postalCode")
            } else {
                parts.add(locality)
            }
        }

        val composed = if (parts.isNotEmpty()) parts.joinToString(", ") else ""

        // Decide which string gives the best human readability
        return when {
            // If composed has specific street/neighborhood (2 or more parts), prefer composed
            parts.size >= 2 -> composed
            // If fullLine has rich detail (contains commas and isn't just a single city name)
            fullLine.isNotBlank() && fullLine.contains(",") -> fullLine
            // Otherwise fallback to composed if available
            composed.isNotBlank() -> composed
            // Otherwise fullLine
            fullLine.isNotBlank() -> fullLine
            else -> null
        }
    }

    /**
     * Synchronously resolves a specific clean address from Latitude and Longitude.
     * Fetches top 5 candidates to guarantee real street & neighborhood detection.
     */
    fun resolveSpecificAddress(context: Context, latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        return try {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 5)
            if (!addresses.isNullOrEmpty()) {
                formatAddressList(addresses)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

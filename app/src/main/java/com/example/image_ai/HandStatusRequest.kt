package com.example.image_ai

data class HandStatusRequest(
    val device: DeviceInfo,
    val event: EventInfo,
    val location: LocationInfo,
    val payload: PayloadInfo
)

data class DeviceInfo(
    val device_key: String,
    val type: String
)

data class EventInfo(
    val type: String,
    val occurred_at: String,
    val severity: String
)

data class LocationInfo(
    val lat: Double,
    val lng: Double
)

data class PayloadInfo(
    val additionalProp1: Map<String, Any> = emptyMap()
)
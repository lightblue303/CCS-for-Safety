package com.example.worker1

import retrofit2.Call
import retrofit2.http.*

// 1. FCM 토큰 등록용
data class PushTokenRequest(
    val owner_type: String, // "WORKER"
    val token: String,
    val platform: String = "ANDROID"
)

// 2. 디바이스 구독용
data class SubscribeRequest(
    val token: String,
    val role: String = "WORKER"
)

// ⭐ [신규 추가] 사고 이벤트 전송용 데이터 구조
data class EventRequest(
    val device: DeviceInfo,
    val event: EventDetail,
    val location: LocationData,
    val payload: Map<String, Any> = emptyMap()
)

data class DeviceInfo(
    val device_key: String,
    val type: String = "WEARABLE"
)

data class EventDetail(
    val type: String, // "FALL_DETECTED", "SOS_BUTTON" 등
    val occurred_at: String, // ISO 8601 형식
    val severity: String = "critical"
)

data class LocationData(
    val lat: Double,
    val lng: Double
)

data class WorkLogRequest(
    val device_key: String,   // 매니저 앱과 동일하게 device_key로 통일!
    val status: String,       // "START" 또는 "END"
    val occurred_at: String,  // ISO 8601 형식 (예: 2026-04-25T12:00:00Z)
    val location: LocationData
)
interface ApiService {
    // [기존] FCM 토큰 등록
    @POST("api/v1/push-tokens/register")
    fun registerPushToken(@Body request: PushTokenRequest): Call<Void>

    // [기존] 디바이스 구독
    @POST("api/v1/devices/{device_key}/subscribe")
    fun subscribeDevice(
        @Path("device_key") deviceKey: String,
        @Body request: SubscribeRequest
    ): Call<Void>

    // [기존] 알림 확인
    @POST("api/v1/notifications/{notification_id}/ack")
    fun sendAck(
        @Path("notification_id") notificationId: Int
    ): Call<Void>

    // ⭐ [신규 추가] 사고 발생 이벤트 직접 전송 (POST /api/v1/events)
    @POST("api/v1/events")
    fun reportEvent(@Body request: EventRequest): Call<Void>

    @POST("api/v1/worker-status")
    fun reportWorkLog(@Body request: WorkLogRequest): Call<Void>
}
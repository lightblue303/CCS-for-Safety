package com.example.manager1

import retrofit2.Call
import retrofit2.http.*

// 1. FCM 토큰 등록용
data class PushTokenRequest(
    val owner_type: String, // "ADMIN"
    val token: String,
    val platform: String = "ANDROID"
)

// 2. 디바이스 구독용
data class SubscribeRequest(
    val token: String,
    val role: String = "ADMIN"
)

// ⭐ 3. 사고 이벤트 전송용 (추가됨)
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
    val device_key: String,   // 친구 요청대로 device_key로 설정
    val status: String,       // "START" 또는 "END"
    val occurred_at: String,  // ISO 8601 형식
    val location: LocationData
)

interface ApiService {
    // [기존] 관리자 FCM 토큰 등록
    @POST("api/v1/push-tokens/register")
    fun registerPushToken(@Body request: PushTokenRequest): Call<Void>

    // [기존] 관리자 디바이스 구독
    @POST("api/v1/devices/{device_key}/subscribe")
    fun subscribeDevice(
        @Path("device_key") deviceKey: String,
        @Body request: SubscribeRequest
    ): Call<Void>

    // ⭐ [신규] 사고 발생 이벤트 전송
    @POST("api/v1/events")
    fun reportEvent(@Body request: EventRequest): Call<Void>

    @POST("api/v1/work-logs") // 친구가 알려준 엔드포인트가 다르면 수정하세요!
    fun reportWorkLog(@Body request: WorkLogRequest): Call<Void>

    @GET("api/v1/worker-status")
    fun getWorkLogs(
        @Query("device_key") deviceKey: String
    ): Call<List<WorkLogRequest>>
}
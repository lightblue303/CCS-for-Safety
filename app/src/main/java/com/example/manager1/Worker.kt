package com.example.manager1

// 각 변수에 = "" 를 붙여서 빈 값이 들어와도 에러가 나지 않게 합니다.
data class Worker(
    val id: Int=0,
    val name: String = "",
    val location: String = "",
    val sensorPath: String = "none"
)
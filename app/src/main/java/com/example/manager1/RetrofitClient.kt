package com.example.manager1

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 친구를 만나면 수정할 IP 주소 (Worker와 동일하게 맞춰둡니다)
    private const val BASE_URL = "http://YOUR_SERVER_IP:8000/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
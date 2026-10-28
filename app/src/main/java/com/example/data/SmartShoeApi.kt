package com.example.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class StepData(
    val steps: Int,
    val target: Int
)

data class TargetRequest(
    val target: Int
)

interface SmartShoeApi {
    @GET("steps")
    suspend fun getSteps(): StepData

    @POST("target")
    suspend fun updateTarget(@Body request: TargetRequest): StepData

    @POST("reset")
    suspend fun resetSteps(): StepData
}

package com.example.questly.core.model

data class CheckIn(
    val id: String,
    val checkpointId: String,
    val timestampMillis: Long,
)

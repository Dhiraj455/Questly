package com.example.questly.core.data

sealed interface CheckInResult {
    data object Success : CheckInResult
    data object TooFar : CheckInResult
    data object OnCooldown : CheckInResult
    data object UnknownCheckpoint : CheckInResult
}

const val CHECK_IN_COOLDOWN_MILLIS = 3_600_000L

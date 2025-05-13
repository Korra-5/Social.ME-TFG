package com.example.socialme.model

data class PaymentVerificationRequest(
    val paymentId: String,
    val username: String
)
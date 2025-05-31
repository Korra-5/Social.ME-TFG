package com.example.socialme.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class PaymentRequestDTO(
    @field:NotBlank(message = "El username es obligatorio")
    val username: String,

    @field:NotNull(message = "El monto es obligatorio")
    @field:Positive(message = "El monto debe ser positivo")
    val amount: Double,

    @field:NotBlank(message = "La descripción es obligatoria")
    val description: String,

    val currency: String = "EUR"
)

data class PaymentResponseDTO(
    val success: Boolean,
    val paymentId: String? = null,
    val approvalUrl: String? = null,
    val message: String,
    val amount: Double? = null,
    val currency: String? = null
)

data class PaymentVerificationDTO(
    @field:NotBlank(message = "El paymentId es obligatorio")
    val paymentId: String,

    @field:NotBlank(message = "El payerId es obligatorio")
    val payerId: String,

    @field:NotBlank(message = "El username es obligatorio")
    val username: String
)

data class PaymentStatusDTO(
    val paymentId: String,
    val status: String,
    val amount: Double,
    val currency: String,
    val description: String,
    val createTime: String,
    val updateTime: String? = null
)
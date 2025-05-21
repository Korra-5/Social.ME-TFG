package com.example.socialme.dto

import java.util.Date

data class MensajeCreateDTO(
    val id: String?,
    val comunidadUrl: String,
    val username: String,
    val contenido: String,
    val fechaEnvio: Date,
    val leido: Boolean = false
)
package com.example.socialme.dto

data class MensajeCreateDTO(
    val comunidadUrl: String,
    val username: String,
    val contenido: String
    // Remove fechaEnvio from here - it will be set by the server
)
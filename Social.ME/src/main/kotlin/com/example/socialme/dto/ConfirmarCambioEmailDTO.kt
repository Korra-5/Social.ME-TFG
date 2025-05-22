package com.example.socialme.dto

data class ConfirmarCambioEmailDTO(
    val username: String,
    val nuevoEmail: String,
    val codigo: String
)
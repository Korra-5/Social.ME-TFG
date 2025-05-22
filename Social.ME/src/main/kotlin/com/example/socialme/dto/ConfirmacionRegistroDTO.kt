package com.example.socialme.dto

data class ConfirmacionRegistroDTO(
    val email: String,
    val codigo: String,
    val datosRegistro: UsuarioRegisterDTO
)
package com.example.socialme.dto

import java.util.Date

data class BloqueoDTO(
    val _id: String? = null,
    val bloqueador: String,
    val bloqueado: String,
    val fechaBloqueo: Date? = null
)
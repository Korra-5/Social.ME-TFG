// NotificacionDTO.kt
package com.example.socialme.dto

import java.util.*

data class NotificacionDTO(
    val _id: String?,
    val tipo: String,
    val titulo: String,
    val mensaje: String,
    val entidadId: String?,
    val entidadNombre: String?,
    val fechaCreacion: Date,
    val leida: Boolean
)
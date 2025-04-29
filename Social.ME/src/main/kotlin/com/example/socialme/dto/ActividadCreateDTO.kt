package com.example.socialme.dto

import com.example.socialme.model.Coordenadas
import java.util.*

import com.fasterxml.jackson.annotation.JsonFormat
import java.util.Date

// Activity DTOs
data class ActividadCreateDTO(
    val nombre: String,
    val descripcion: String,
    val comunidad: String,
    val creador: String,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val fotosCarruselBase64: List<String>? = null,  // Used for receiving base64 images
    val fotosCarruselIds: List<String>? = null,     // Used if files already uploaded
    val privada: Boolean,
    val coordenadas: Coordenadas,  // Coordenadas geográficas
    var direccion: String

)
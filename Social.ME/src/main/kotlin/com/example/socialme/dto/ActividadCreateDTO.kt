package com.example.socialme.dto

import com.example.socialme.model.Coordenadas

import java.util.Date

// Activity DTOs
data class ActividadCreateDTO(
    val nombre: String,
    val descripcion: String,
    val comunidad: String,
    val creador: String,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val fotosCarruselBase64: List<String>? = null,
    val fotosCarruselIds: List<String>? = null,
    val privada: Boolean,
    val coordenadas: Coordenadas,
    var lugar: String

)
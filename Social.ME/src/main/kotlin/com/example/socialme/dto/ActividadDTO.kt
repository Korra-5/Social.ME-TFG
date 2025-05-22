package com.example.socialme.dto

import com.example.socialme.model.Coordenadas
import java.util.*

data class ActividadDTO(
    val _id: String?,
    val nombre: String,
    val descripcion: String,
    val privada: Boolean,
    val creador: String,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val fotosCarruselIds: List<String>,
    val coordenadas: Coordenadas,
    var lugar: String
)

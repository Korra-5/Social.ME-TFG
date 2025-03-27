package com.example.socialme.dto

import java.util.*

import com.fasterxml.jackson.annotation.JsonFormat
import java.util.Date

data class ActividadCreateDTO (
    val nombre: String,
    val descripcion: String,
    val comunidad: String,
    val creador: String,
    val lugar: String,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val fotosCarrusel: List<String>,
    val privada: Boolean
)
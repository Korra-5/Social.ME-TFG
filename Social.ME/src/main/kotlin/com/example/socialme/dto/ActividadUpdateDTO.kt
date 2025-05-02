package com.example.socialme.dto

import com.example.socialme.model.Coordenadas
import java.util.*

data class ActividadUpdateDTO(
    val _id: String,
    val nombre: String,
    val descripcion: String,
    val fotosCarruselBase64: List<String>? = null,  // Used for receiving base64 images
    val fotosCarruselIds: List<String>? = null,     // Used if files already uploaded
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val coordenadas: Coordenadas,  // Coordenadas geográficas
    var lugar: String
)

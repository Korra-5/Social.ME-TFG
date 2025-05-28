package com.example.socialme.dto

import com.example.socialme.model.Coordenadas
import java.util.Date
data class ComunidadCreateDTO(
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses: List<String>,
    val fotoPerfilBase64: String? = null,
    val fotoPerfilId: String? = null,
    val creador: String,
    val privada: Boolean,
    val coordenadas: Coordenadas,
    val codigoUnion:String?
)
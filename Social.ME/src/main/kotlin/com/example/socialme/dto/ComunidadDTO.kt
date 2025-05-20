package com.example.socialme.dto

import com.example.socialme.model.Coordenadas
import java.util.Date

data class ComunidadDTO(
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses: List<String> = emptyList(),
    val fotoPerfilId: String = "",
    val fotoCarruselIds: List<String> = emptyList(),
    val creador: String,
    val administradores: List<String> = emptyList(),
    val fechaCreacion: Date,
    val comunidadGlobal: Boolean = false,
    val privada: Boolean = false,
    val coordenadas: Coordenadas? = null,
    val codigoUnion: String? = null
)
package com.example.socialme.dto

import com.example.socialme.model.Coordenadas


data class ComunidadUpdateDTO(
    val currentURL: String,
    val newUrl: String,
    val nombre: String,
    val descripcion: String,
    val intereses: List<String>,
    val fotoPerfilBase64: String? = null,
    val fotoPerfilId: String? = null,
    val fotoCarruselBase64: List<String>? = null,
    val fotoCarruselIds: List<String>? = null,
    val administradores: List<String>?,
    val privada: Boolean,
    val coordenadas: Coordenadas,
)
package com.example.socialme.dto

import com.example.socialme.model.Coordenadas
import java.util.Date

data class ComunidadDTO(
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses: List<String>,
    val fotoPerfilId: String,
    val fotoCarruselIds: List<String>?,
    val creador: String,
    val administradores: List<String>?,
    val fechaCreacion: Date,
    val privada: Boolean,
    val coordenadas: Coordenadas?,
    val codigoUnion:String?,
    val expulsadosUsername:List<String>

)
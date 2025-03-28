package com.example.socialme.dto

import java.util.Date

data class ComunidadDTO(
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses:List<String>,
    val fotoPerfil:String,
    val fotoCarrusel: List<String>?,
    val creador:String,
    val administradores: List<String>?,
    val fechaCreacion:Date,
    val comunidadGlobal:Boolean,
    val privada:Boolean
) {
}
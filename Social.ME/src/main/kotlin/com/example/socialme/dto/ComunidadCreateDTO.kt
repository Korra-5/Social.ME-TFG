package com.example.socialme.dto

import java.util.Date

data class ComunidadCreateDTO(
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses:List<String>,
    val fotoPerfil: String,
    val creador: String,
    val comunidadGlobal:Boolean
) {
}
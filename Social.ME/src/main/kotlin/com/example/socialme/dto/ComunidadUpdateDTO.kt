package com.example.socialme.dto

data class ComunidadUpdateDTO (
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses:List<String>,
    val fotoPerfil: String,
    val fotoCarrusel:List<String>?,
    val administradores: List<String>?
)
package com.example.socialme.dto


data class ComunidadUpdateDTO(
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses: List<String>,
    val fotoPerfilBase64: String? = null,  // Used for receiving base64 image
    val fotoPerfilId: String? = null,      // Used if file already uploaded
    val fotoCarruselBase64: List<String>? = null,  // Used for receiving base64 images
    val fotoCarruselIds: List<String>? = null,     // Used if files already uploaded
    val administradores: List<String>?,
    val privada: Boolean
)
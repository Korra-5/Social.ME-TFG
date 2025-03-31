package com.example.socialme.dto

import com.example.socialme.model.Direccion

data class UsuarioUpdateDTO(
    val username: String,
    val password: String,
    val passwordRepeat: String,
    val email: String,
    val nombre: String,
    val apellido: String,
    val descripcion: String,
    val intereses: List<String>,
    val fotoPerfilBase64: String? = null,  // Used for receiving base64 image
    val fotoPerfilId: String? = null,      // Used if file already uploaded
    val direccion: Direccion?
)
package com.example.socialme.dto

import com.example.socialme.model.Direccion

data class UsuarioUpdateDTO(
    val currentUsername: String,
    val newUsername: String?,     // This can remain nullable
    val email: String,
    val nombre: String,
    val apellido: String,
    val descripcion: String,
    val intereses: List<String>,
    val fotoPerfilBase64: String?,  // Make this nullable since it might be empty
    val fotoPerfilId: String?,      // This can remain nullable
    val direccion: Direccion
)
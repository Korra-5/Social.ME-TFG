package com.example.socialme.dto

import com.example.socialme.model.Direccion

data class UsuarioUpdateDTO(
    val currentUsername: String,  // Username actual para buscar el usuario
    val newUsername: String?,     // Nuevo username (opcional)
    val email: String,
    val nombre: String,
    val apellido: String,
    val descripcion: String,
    val intereses: List<String>,
    val password: String,
    val passwordRepeat: String,
    val fotoPerfilBase64: String?,
    val fotoPerfilId: String?,
    val direccion: Direccion
)
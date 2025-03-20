package com.example.socialme.dto

import com.example.socialme.model.Direccion

data class UsuarioRegisterDTO(
    val username: String,
    val email: String,
    val password: String,
    val passwordRepeat: String,
    val rol: String?,
    val direccion: Direccion?,
    val nombre:String,
    val apellidos:String,
    val descripcion:String,
    val intereses: List<String>,
    val fotoPerfil:String
)


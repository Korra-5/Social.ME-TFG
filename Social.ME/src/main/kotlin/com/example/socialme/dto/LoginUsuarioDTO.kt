package com.example.socialme.dto

import com.example.socialme.model.Coordenadas

data class LoginUsuarioDTO(
    val username: String,
    val password: String,
    val coordenadas: Coordenadas? = null

)

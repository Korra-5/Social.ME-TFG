package com.example.socialme.dto

class CambiarContrasenaDTO(
    val username:String,
    val passwordActual:String,
    val passwordNueva:String,
    val passwordRepeat:String,
) {
}
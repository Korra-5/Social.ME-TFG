package com.example.socialme.dto

import com.example.socialme.model.Direccion

class UsuarioUpdateDTO(
    val username:String,
    val password:String,
    val email:String,
    val nombre:String,
    val apellido:String,
    val descripcion:String,
    val intereses:String,
    val fotoPerfil:String,
    val direccion: Direccion?
) {

}
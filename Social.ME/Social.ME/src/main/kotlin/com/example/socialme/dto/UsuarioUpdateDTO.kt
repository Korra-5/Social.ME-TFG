package com.example.socialme.dto

import com.example.socialme.model.Direccion

class UsuarioUpdateDTO(
    val username:String,
    val password:String,
    val passwordRepeat:String,
    val email:String,
    val nombre:String,
    val apellido:String,
    val descripcion:String,
    val intereses:List<String>,
    val fotoPerfil:String,
    val direccion: Direccion?
) {

}
package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId

data class Comunidad(
    @BsonId
    val _id: String,
    val url: String,
    val nombre: String,
    val descripcion: String,
    val intereses:List<String>,
    val actividades: List<String>?,
    val fotoPerfil: String?,
    val fotoCarrusel:List<String>?,
    val creador: String,
    val administradores: List<String>?
) {
}
package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import java.util.*

data class Usuario(
    @BsonId
    val _id: String?,
    val username:String,
    val password:String,
    val roles:String,
    val nombre:String,
    val apellidos:String,
    val descripcion:String,
    val email:String,
    val intereses: List<String>,
    val fotoPerfil:String,
    val comunidades: List <String>?,
    val actividades: List<String>?,
    val direccion: Direccion?,
    val fechaUnion: Date

) {
}
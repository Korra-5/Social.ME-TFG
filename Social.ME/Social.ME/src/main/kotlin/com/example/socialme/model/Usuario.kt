package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Usuarios")
data class Usuario(
    @BsonId
    val _id: String?,
    var username:String,
    val password:String,
    val roles:String,
    var nombre:String,
    var apellidos:String,
    var descripcion:String,
    var email:String,
    var intereses: List<String>,
    var fotoPerfil:String,
    var direccion: Direccion?,
    val fechaUnion: Date

) {
}
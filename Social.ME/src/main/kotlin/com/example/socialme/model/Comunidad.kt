package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*


@Document("Comunidades")
data class Comunidad(
    @BsonId
    val _id: String?,
    var url: String,
    var nombre: String,
    var descripcion: String,
    var intereses: List<String>,
    var fotoPerfilId: String,
    var fotoCarruselIds: List<String>?,
    var creador: String,
    var administradores: List<String>?,
    val fechaCreacion: Date,
    var privada: Boolean,
    var coordenadas: Coordenadas?,
    val codigoUnion: String?
)
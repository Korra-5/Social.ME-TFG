package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId

data class Actividad (
    @BsonId
    val _id: String?,
    val nombre: String,
    val descripcion: String,
    val fotosCarrusel:List<String>,
    val comunidad:String,
    val creador:String
)
    {
}
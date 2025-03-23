package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Actividades")
data class Actividad (
    @BsonId
    val _id: String?,
    val nombre: String,
    val descripcion: String,
    val fotosCarrusel:List<String>,
    val comunidad:String,
    val creador:String,
    val fechaCreacion: Date,
    val fechaInicio:Date,
    val fechaFinalizacion: Date,
    val privada:Boolean
)
    {
}
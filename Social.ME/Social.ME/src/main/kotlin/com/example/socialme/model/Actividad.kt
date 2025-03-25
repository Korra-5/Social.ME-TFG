package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Actividades")
data class Actividad (
    @BsonId
    val _id: String?,
    var nombre: String,
    var descripcion: String,
    var fotosCarrusel:List<String>,
    val comunidad:String,
    val creador:String,
    val fechaCreacion: Date,
    var fechaInicio:Date,
    var fechaFinalizacion: Date,
    val privada:Boolean,
    val lugar:String,
)
    {
}
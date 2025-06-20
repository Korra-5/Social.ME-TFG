package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Actividades")
data class Actividad(
    @BsonId
    val _id: String?,
    var nombre: String,
    var descripcion: String,
    var fotosCarruselIds: List<String>,
    var comunidad: String,
    var creador: String,
    val fechaCreacion: Date,
    var fechaInicio: Date,
    var fechaFinalizacion: Date,
    val privada: Boolean,
    var coordenadas: Coordenadas,
    var lugar: String
)
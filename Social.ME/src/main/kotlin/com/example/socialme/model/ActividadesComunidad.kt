package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document

@Document("ActividadesComunidad")
data class ActividadesComunidad(
    @BsonId
    val _id: String?,
    val comunidad:String,
    val idActividad:String?,
    var nombreActividad: String
)
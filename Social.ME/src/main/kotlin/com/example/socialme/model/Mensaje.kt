package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

@Document("Mensajes")
data class Mensaje(
    @BsonId
    val _id: String?,
    val comunidadUrl: String,
    val username: String,
    val contenido: String,
    val fechaEnvio: Date,
    val leido: Boolean = false
)
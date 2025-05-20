package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Bloqueos")
data class Bloqueo(
    @BsonId
    val _id: String,
    val bloqueador: String,  // Usuario que realizó el bloqueo
    val bloqueado: String,   // Usuario que fue bloqueado
    val fechaBloqueo: Date
)
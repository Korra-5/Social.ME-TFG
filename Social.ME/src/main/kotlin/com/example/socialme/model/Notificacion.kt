// Notificacion.kt
package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Notificaciones")
data class Notificacion(
    @BsonId
    val _id: String?,
    val tipo: String,
    val titulo: String,
    val mensaje: String,
    val usuarioDestino: String,
    val entidadId: String?,
    val entidadNombre: String?,
    val fechaCreacion: Date,
    val leida: Boolean = false
)
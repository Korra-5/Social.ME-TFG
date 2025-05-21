// Notificacion.kt
package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("Notificaciones")
data class Notificacion(
    @BsonId
    val _id: String?,
    val tipo: String,  // "ACTIVIDAD_PROXIMA", "ACTIVIDAD_INICIANDO", etc.
    val titulo: String,
    val mensaje: String,
    val usuarioDestino: String,
    val entidadId: String?,  // ID de la actividad relacionada
    val entidadNombre: String?,  // Nombre de la actividad relacionada
    val fechaCreacion: Date,
    val leida: Boolean = false  // Para marcar si el usuario ya leyó la notificación
)
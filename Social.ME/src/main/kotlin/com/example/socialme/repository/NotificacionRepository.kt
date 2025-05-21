// NotificacionRepository.kt
package com.example.socialme.repository

import com.example.socialme.model.Notificacion
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface NotificacionRepository : MongoRepository<Notificacion, String> {
    fun findByUsuarioDestinoOrderByFechaCreacionDesc(usuarioDestino: String): List<Notificacion>
    fun countByUsuarioDestinoAndLeida(usuarioDestino: String, leida: Boolean): Long
    fun findByUsuarioDestinoAndEntidadId(usuarioDestino: String, entidadId: String): List<Notificacion>
}
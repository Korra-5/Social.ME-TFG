// NotificacionService.kt
package com.example.socialme.service

import com.example.socialme.dto.NotificacionDTO
import com.example.socialme.model.Notificacion
import com.example.socialme.repository.NotificacionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class NotificacionService {

    @Autowired
    private lateinit var notificacionRepository: NotificacionRepository

    @Autowired
    private lateinit var simpMessagingTemplate: SimpMessagingTemplate

    // Crear una notificación y guardarla en la base de datos
    fun crearNotificacion(
        tipo: String,
        titulo: String,
        mensaje: String,
        usuarioDestino: String,
        entidadId: String? = null,
        entidadNombre: String? = null
    ): NotificacionDTO {
        val notificacion = Notificacion(
            _id = null,
            tipo = tipo,
            titulo = titulo,
            mensaje = mensaje,
            usuarioDestino = usuarioDestino,
            entidadId = entidadId,
            entidadNombre = entidadNombre,
            fechaCreacion = Date.from(Instant.now()),
            leida = false
        )

        val savedNotificacion = notificacionRepository.save(notificacion)

        // Enviar notificación en tiempo real al usuario
        val destino = "/queue/notificaciones.$usuarioDestino"
        val notificacionDTO = mapToDTO(savedNotificacion)
        simpMessagingTemplate.convertAndSend(destino, notificacionDTO)

        return notificacionDTO
    }

    // Obtener todas las notificaciones de un usuario
    fun obtenerNotificacionesUsuario(username: String): List<NotificacionDTO> {
        val notificaciones = notificacionRepository.findByUsuarioDestinoOrderByFechaCreacionDesc(username)
        return notificaciones.map { mapToDTO(it) }
    }

    // Marcar una notificación como leída
    fun marcarComoLeida(notificacionId: String): NotificacionDTO {
        val notificacion = notificacionRepository.findById(notificacionId).orElseThrow {
            throw Exception("Notificación no encontrada")
        }

        val notificacionActualizada = notificacion.copy(leida = true)
        val savedNotificacion = notificacionRepository.save(notificacionActualizada)

        return mapToDTO(savedNotificacion)
    }

    // Contar notificaciones no leídas
    fun contarNoLeidas(username: String): Long {
        return notificacionRepository.countByUsuarioDestinoAndLeida(username, false)
    }

    // Convertir de entidad a DTO
    private fun mapToDTO(notificacion: Notificacion): NotificacionDTO {
        return NotificacionDTO(
            _id = notificacion._id,
            tipo = notificacion.tipo,
            titulo = notificacion.titulo,
            mensaje = notificacion.mensaje,
            entidadId = notificacion.entidadId,
            entidadNombre = notificacion.entidadNombre,
            fechaCreacion = notificacion.fechaCreacion,
            leida = notificacion.leida
        )
    }
}
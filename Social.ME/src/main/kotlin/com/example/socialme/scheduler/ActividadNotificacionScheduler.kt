// ActividadNotificacionScheduler.kt
package com.example.socialme.scheduler

import com.example.socialme.repository.ActividadRepository
import com.example.socialme.repository.ParticipantesActividadRepository
import com.example.socialme.service.NotificacionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.*

@Component
@EnableScheduling
class ActividadNotificacionScheduler {

    @Autowired
    private lateinit var actividadRepository: ActividadRepository

    @Autowired
    private lateinit var participantesActividadRepository: ParticipantesActividadRepository

    @Autowired
    private lateinit var notificacionService: NotificacionService

    // Ejecutar cada minuto para verificar actividades próximas
    @Scheduled(fixedRate = 60000)
    fun verificarActividadesProximas() {
        val ahora = Instant.now()
        val enUnaHoraCincoMinutos = ahora.plus(Duration.ofMinutes(65))
        val enUnaHora = ahora.plus(Duration.ofMinutes(60))

        // Obtener todas las actividades
        val actividades = actividadRepository.findAll()

        actividades.forEach { actividad ->
            val fechaInicioInstant = actividad.fechaInicio.toInstant()

            // Notificar cuando queden 1 hora y 5 minutos
            if (fechaInicioInstant.isAfter(ahora.plus(Duration.ofMinutes(64))) &&
                fechaInicioInstant.isBefore(ahora.plus(Duration.ofMinutes(66)))) {

                // Obtener participantes
                val participantes = participantesActividadRepository.findByidActividad(actividad._id!!)

                participantes.forEach { participante ->
                    notificacionService.crearNotificacion(
                        tipo = "ACTIVIDAD_PROXIMA",
                        titulo = "Recordatorio: ${actividad.nombre}",
                        mensaje = "¡Tu actividad \"${actividad.nombre}\" comenzará en 1 hora y 5 minutos!",
                        usuarioDestino = participante.username,
                        entidadId = actividad._id,
                        entidadNombre = actividad.nombre
                    )
                }
            }

            // Notificar cuando esté iniciando
            if (fechaInicioInstant.isAfter(ahora.minus(Duration.ofMinutes(1))) &&
                fechaInicioInstant.isBefore(ahora.plus(Duration.ofMinutes(1)))) {

                // Obtener participantes
                val participantes = participantesActividadRepository.findByidActividad(actividad._id!!)

                participantes.forEach { participante ->
                    notificacionService.crearNotificacion(
                        tipo = "ACTIVIDAD_INICIANDO",
                        titulo = "¡${actividad.nombre} está comenzando!",
                        mensaje = "Tu actividad \"${actividad.nombre}\" está comenzando ahora.",
                        usuarioDestino = participante.username,
                        entidadId = actividad._id,
                        entidadNombre = actividad.nombre
                    )
                }
            }
        }
    }
}
// NotificacionController.kt
package com.example.socialme.controller

import com.example.socialme.dto.NotificacionDTO
import com.example.socialme.service.NotificacionService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Notificacion")
class NotificacionController {

    @Autowired
    private lateinit var notificacionService: NotificacionService

    @GetMapping("/obtenerNotificaciones/{username}")
    fun obtenerNotificaciones(
        httpRequest: HttpServletRequest,
        @PathVariable("username") username: String
    ): ResponseEntity<List<NotificacionDTO>> {
        return ResponseEntity(notificacionService.obtenerNotificacionesUsuario(username), HttpStatus.OK)
    }

    @GetMapping("/contarNoLeidas/{username}")
    fun contarNoLeidas(
        httpRequest: HttpServletRequest,
        @PathVariable("username") username: String
    ): ResponseEntity<Long> {
        return ResponseEntity(notificacionService.contarNoLeidas(username), HttpStatus.OK)
    }

    @PutMapping("/marcarComoLeida/{notificacionId}")
    fun marcarComoLeida(
        httpRequest: HttpServletRequest,
        @PathVariable("notificacionId") notificacionId: String
    ): ResponseEntity<NotificacionDTO> {
        return ResponseEntity(notificacionService.marcarComoLeida(notificacionId), HttpStatus.OK)
    }
}
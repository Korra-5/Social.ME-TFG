package com.example.socialme.controller

import com.example.socialme.dto.MensajeCreateDTO
import com.example.socialme.dto.MensajeDTO
import com.example.socialme.service.ChatService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Chat")
class ChatController {

    @Autowired
    private lateinit var chatService: ChatService

    @PostMapping("/enviarMensaje")
    fun enviarMensaje(
        httpRequest: HttpServletRequest,
        @RequestBody mensajeCreateDTO: MensajeCreateDTO
    ): ResponseEntity<MensajeDTO> {
        val mensaje = chatService.enviarMensaje(mensajeCreateDTO)
        return ResponseEntity(mensaje, HttpStatus.CREATED)
    }

    @GetMapping("/obtenerMensajes/{comunidadUrl}")
    fun obtenerMensajesComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable("comunidadUrl") comunidadUrl: String,
        @RequestParam(required = false) usuarioSolicitante: String?
    ): ResponseEntity<List<MensajeDTO>> {
        val mensajes = chatService.obtenerMensajesComunidad(comunidadUrl, usuarioSolicitante)
        return ResponseEntity.ok(mensajes)
    }
}
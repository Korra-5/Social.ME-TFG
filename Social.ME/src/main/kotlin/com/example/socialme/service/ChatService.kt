package com.example.socialme.service

import com.example.socialme.dto.MensajeCreateDTO
import com.example.socialme.dto.MensajeDTO
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Mensaje
import com.example.socialme.repository.ComunidadRepository
import com.example.socialme.repository.MensajeRepository
import com.example.socialme.repository.ParticipantesComunidadRepository
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date

@Service
class ChatService {

    @Autowired
    private lateinit var mensajeRepository: MensajeRepository

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    fun obtenerMensajesComunidad(comunidadUrl: String, usuarioSolicitante: String? = null): List<MensajeDTO> {
        comunidadRepository.findComunidadByUrl(comunidadUrl)
            .orElseThrow { NotFoundException("Comunidad no encontrada") }

        // Verificar si el usuario es ADMIN
        val esAdmin = if (usuarioSolicitante != null) {
            val usuario = usuarioRepository.findFirstByUsername(usuarioSolicitante).orElse(null)
            usuario?.roles == "ADMIN"
        } else false

        // Si no es ADMIN, verificar que sea miembro de la comunidad
        if (!esAdmin && usuarioSolicitante != null) {
            val esParticipante = participantesComunidadRepository.findByUsernameAndComunidad(
                usuarioSolicitante, comunidadUrl
            ).isPresent

            if (!esParticipante) {
                throw BadRequestException("El usuario no es miembro de esta comunidad")
            }
        }

        val mensajes = mensajeRepository.findByComunidadUrlOrderByFechaEnvioAsc(comunidadUrl)

        return mensajes.map { mensaje ->
            MensajeDTO(
                id = mensaje._id,
                comunidadUrl = mensaje.comunidadUrl,
                username = mensaje.username,
                contenido = mensaje.contenido,
                fechaEnvio = mensaje.fechaEnvio,
                leido = mensaje.leido
            )
        }
    }

    fun enviarMensaje(mensajeCreateDTO: MensajeCreateDTO): MensajeDTO {
        // Verificar que la comunidad existe
        val comunidad = comunidadRepository.findComunidadByUrl(mensajeCreateDTO.comunidadUrl)
            .orElseThrow { NotFoundException("Comunidad no encontrada") }

        // Verificar que el usuario existe
        val usuario = usuarioRepository.findFirstByUsername(mensajeCreateDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        // Verificar que el usuario es miembro de la comunidad
        val esParticipante = participantesComunidadRepository.findByUsernameAndComunidad(
            mensajeCreateDTO.username, mensajeCreateDTO.comunidadUrl
        ).isPresent

        if (!esParticipante) {
            throw BadRequestException("El usuario no es miembro de esta comunidad")
        }

        // Validar contenido del mensaje
        if (mensajeCreateDTO.contenido.isBlank()) {
            throw BadRequestException("El mensaje no puede estar vacío")
        }

        if (mensajeCreateDTO.contenido.length > 500) {
            throw BadRequestException("El mensaje no puede exceder los 500 caracteres")
        }

        // Crear y guardar el mensaje
        val mensaje = Mensaje(
            _id = null,
            comunidadUrl = mensajeCreateDTO.comunidadUrl,
            username = mensajeCreateDTO.username,
            contenido = mensajeCreateDTO.contenido,
            fechaEnvio = Date.from(Instant.now()),
            leido = false
        )

        val mensajeGuardado = mensajeRepository.save(mensaje)

        return MensajeDTO(
            id = mensajeGuardado._id,
            comunidadUrl = mensajeGuardado.comunidadUrl,
            username = mensajeGuardado.username,
            contenido = mensajeGuardado.contenido,
            fechaEnvio = mensajeGuardado.fechaEnvio,
            leido = mensajeGuardado.leido
        )
    }

    // Método para eliminar mensajes cuando se elimina una comunidad
    fun eliminarMensajesComunidad(comunidadUrl: String) {
        mensajeRepository.deleteByComunidadUrl(comunidadUrl)
    }
}
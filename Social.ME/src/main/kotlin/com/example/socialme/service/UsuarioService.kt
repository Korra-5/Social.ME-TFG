package com.example.socialme.service


import com.example.socialme.dto.UsuarioDTO
import com.example.socialme.dto.UsuarioRegisterDTO
import com.example.socialme.dto.UsuarioUpdateDTO
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Usuario
import com.example.socialme.repository.ParticipantesActividadRepository
import com.example.socialme.repository.ParticipantesComunidadRepository
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
@Service
class UsuarioService : UserDetailsService {

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var externalApiService: ExternalAPIService

    @Autowired
    private lateinit var participantesActividadRepository: ParticipantesActividadRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var gridFSService: GridFSService

    override fun loadUserByUsername(username: String?): UserDetails {
        var usuario: Usuario = usuarioRepository
            .findFirstByUsername(username!!)
            .orElseThrow {
                NotFoundException("$username no existente")
            }

        return User.builder()
            .username(usuario.username)
            .password(usuario.password)
            .roles(usuario.roles)
            .build()
    }

    fun insertUser(usuarioInsertadoDTO: UsuarioRegisterDTO): UsuarioDTO {
        // Existing validation code...

        // Process profile photo if provided in base64 format
        val fotoPerfilId =
            if (usuarioInsertadoDTO.fotoPerfilBase64 != null && usuarioInsertadoDTO.fotoPerfilBase64.isNotBlank()) {
                gridFSService.storeFileFromBase64(
                    usuarioInsertadoDTO.fotoPerfilBase64,
                    "profile_${usuarioInsertadoDTO.username}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "profilePhoto",
                        "username" to usuarioInsertadoDTO.username
                    )
                )
            } else usuarioInsertadoDTO.fotoPerfilId ?: ""

        // Insertar el user (convierto a Entity)
        val usuario = Usuario(
            _id = null,
            username = usuarioInsertadoDTO.username,
            password = passwordEncoder.encode(usuarioInsertadoDTO.password),
            roles = usuarioInsertadoDTO.rol.toString(),
            nombre = usuarioInsertadoDTO.nombre,
            apellidos = usuarioInsertadoDTO.apellidos,
            descripcion = usuarioInsertadoDTO.descripcion,
            email = usuarioInsertadoDTO.email,
            intereses = usuarioInsertadoDTO.intereses,
            fotoPerfilId = fotoPerfilId,
            direccion = usuarioInsertadoDTO.direccion,
            fechaUnion = Date.from(Instant.now()),
        )

        // inserto en bd
        usuarioRepository.insert(usuario)

        // retorno un DTO
        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            descripcion = usuario.descripcion,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            direccion = usuario.direccion,
            fotoPerfilId = fotoPerfilId,
        )
    }

    fun eliminarUsuario(username: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        // Delete the profile photo from GridFS
        try {
            if (usuario.fotoPerfilId.isNotBlank()) {
                gridFSService.deleteFile(usuario.fotoPerfilId)
            }
        } catch (e: Exception) {
            println("Error deleting profile photo: ${e.message}")
        }

        val userDTO = UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            descripcion = usuario.descripcion,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            direccion = usuario.direccion,
            fotoPerfilId = usuario.fotoPerfilId,
        )

        usuarioRepository.delete(usuario)
        return userDTO
    }

    fun modificarUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): UsuarioDTO {
        // Validate input
        if (usuarioUpdateDTO.password != usuarioUpdateDTO.passwordRepeat) {
            throw BadRequestException("Las contraseñas no coinciden")
        }

        // Find existing user using currentUsername
        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.currentUsername).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.currentUsername} no encontrado")
        }

        // Process profile photo if provided in base64 format
        val nuevaFotoPerfilId =
            if (usuarioUpdateDTO.fotoPerfilBase64 != null && usuarioUpdateDTO.fotoPerfilBase64.isNotBlank()) {
                // Delete old profile photo if exists
                try {
                    if (usuario.fotoPerfilId.isNotBlank()) {
                        gridFSService.deleteFile(usuario.fotoPerfilId)
                    }
                } catch (e: Exception) {
                    println("Error deleting old profile photo: ${e.message}")
                }

                // Store new profile photo with potential new username
                val usernameForPhoto = usuarioUpdateDTO.newUsername ?: usuarioUpdateDTO.currentUsername
                gridFSService.storeFileFromBase64(
                    usuarioUpdateDTO.fotoPerfilBase64,
                    "profile_${usernameForPhoto}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "profilePhoto",
                        "username" to usernameForPhoto
                    )
                )
            } else usuarioUpdateDTO.fotoPerfilId ?: usuario.fotoPerfilId

        // Store the old username for comparison
        val antiguoUsername = usuario.username

        // Update user information
        usuario.apply {
            // Only update username if a new one is provided
            username = usuarioUpdateDTO.newUsername ?: antiguoUsername
            email = usuarioUpdateDTO.email
            nombre = usuarioUpdateDTO.nombre
            apellidos = usuarioUpdateDTO.apellido
            descripcion = usuarioUpdateDTO.descripcion
            intereses = usuarioUpdateDTO.intereses
            fotoPerfilId = nuevaFotoPerfilId
            direccion = usuarioUpdateDTO.direccion

            // Only update password if a new one is provided
            if (usuarioUpdateDTO.password.isNotBlank()) {
                password = passwordEncoder.encode(usuarioUpdateDTO.password)
            }
        }

        val usuarioActualizado = usuarioRepository.save(usuario)

        // If username has changed, update references in other collections and GridFS metadata
        if (antiguoUsername != usuario.username) {
            // Update ParticipantesActividad
            val participantesActividad = participantesActividadRepository.findByUsername(antiguoUsername)
            participantesActividad.forEach { participante ->
                participante.username = usuario.username
                participantesActividadRepository.save(participante)
            }

            val participantesComunidad = participantesComunidadRepository.findByUsername(antiguoUsername)
            participantesComunidad.forEach { participante ->
                participante.username = usuario.username
                participantesComunidadRepository.save(participante)
            }
        }

        // Return updated user DTO
        return UsuarioDTO(
            username = usuario.username,
            email = usuarioUpdateDTO.email,
            intereses = usuarioUpdateDTO.intereses,
            nombre = usuarioUpdateDTO.nombre,
            apellido = usuarioUpdateDTO.apellido,
            fotoPerfilId = nuevaFotoPerfilId,
            direccion = usuarioUpdateDTO.direccion,
            descripcion = usuarioUpdateDTO.descripcion
        )
    }
}
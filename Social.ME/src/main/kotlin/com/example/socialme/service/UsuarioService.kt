package com.example.socialme.service

import com.example.socialme.dto.UsuarioDTO
import com.example.socialme.dto.UsuarioRegisterDTO
import com.example.socialme.dto.UsuarioUpdateDTO
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Usuario
import com.example.socialme.repository.ComunidadRepository
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
    private lateinit var comunidadRepository: ComunidadRepository

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
        val usuario: Usuario = usuarioRepository.findFirstByUsername(username!!)
            .orElseThrow { NotFoundException("$username no existente") }

        return User.builder()
            .username(usuario.username)
            .password(usuario.password)
            .roles(usuario.roles)
            .build()
    }

    fun insertUser(usuarioInsertadoDTO: UsuarioRegisterDTO): UsuarioDTO {
        // Procesar la foto de perfil si se proporciona en Base64;
        // En caso contrario, se utiliza el valor del DTO o, de no existir, una cadena vacía.
        val fotoPerfilId: String =
            if (usuarioInsertadoDTO.fotoPerfilBase64 != null && usuarioInsertadoDTO.fotoPerfilBase64.isNotBlank()) {
                gridFSService.storeFileFromBase64(
                    usuarioInsertadoDTO.fotoPerfilBase64,
                    "profile_${usuarioInsertadoDTO.username}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "profilePhoto",
                        "username" to usuarioInsertadoDTO.username
                    )
                ) ?: ""
            } else {
                usuarioInsertadoDTO.fotoPerfilId ?: ""
            }

        // Crear la entidad Usuario con todos los campos no nulos
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
            fotoPerfilId = fotoPerfilId, // Aquí se garantiza que no es null
            direccion = usuarioInsertadoDTO.direccion,
            fechaUnion = Date.from(Instant.now())
        )

        // Insertar el usuario en la base de datos
        usuarioRepository.insert(usuario)

        // Retornar un DTO de usuario
        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            descripcion = usuario.descripcion,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            direccion = usuario.direccion,
            fotoPerfilId = fotoPerfilId
        )
    }

    fun eliminarUsuario(username: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        // Before the deletion attempt, store the ID in a local variable
        val fotoId = usuario.fotoPerfilId
        try {
            if (!fotoId.isNullOrBlank()) {
                gridFSService.deleteFile(fotoId)
            }
        } catch (e: Exception) {
            println("Error deleting profile photo: ${e.message}")
        }

        // En el DTO se garantiza que fotoPerfilId no es null usando el operador Elvis
        val userDTO = UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            descripcion = usuario.descripcion,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            direccion = usuario.direccion,
            fotoPerfilId = usuario.fotoPerfilId ?: ""
        )

        usuarioRepository.delete(usuario)
        return userDTO
    }

    fun modificarUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): UsuarioDTO {
        // Validar que las contraseñas coincidan
        if (usuarioUpdateDTO.password != usuarioUpdateDTO.passwordRepeat) {
            throw BadRequestException("Las contraseñas no coinciden")
        }

        // Buscar el usuario existente usando currentUsername
        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.currentUsername).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.currentUsername} no encontrado")
        }

        if (usuarioUpdateDTO.newUsername!=null) {
            usuarioRepository.findFirstByUsername(usuarioUpdateDTO.newUsername).orElseThrow {
                throw NotFoundException("Usuario ${usuarioUpdateDTO.newUsername} ya existe, prueba con otro nombre")
            }
        }

        // Procesar la foto de perfil si se proporciona en Base64
        val nuevaFotoPerfilId: String =
            if (!usuarioUpdateDTO.fotoPerfilBase64.isNullOrBlank()) {
                // Intentar eliminar la foto de perfil antigua, si existe
                val fotoId = usuario.fotoPerfilId
                try {
                    if (!fotoId.isNullOrBlank()) {
                        gridFSService.deleteFile(fotoId)
                    }
                } catch (e: Exception) {
                    println("Error al eliminar la foto de perfil antigua: ${e.message}")
                }

                val usernameForPhoto = usuarioUpdateDTO.newUsername ?: usuarioUpdateDTO.currentUsername
                gridFSService.storeFileFromBase64(
                    usuarioUpdateDTO.fotoPerfilBase64,
                    "profile_${usernameForPhoto}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "profilePhoto",
                        "username" to usernameForPhoto
                    )
                ) ?: ""
            } else if (usuarioUpdateDTO.fotoPerfilId != null) {
                // Si se proporciona explícitamente un ID de foto
                usuarioUpdateDTO.fotoPerfilId
            } else {
                // Mantener la foto de perfil existente
                usuario.fotoPerfilId ?: ""
            }

        // Guardar el antiguo username para actualizar referencias si se cambia
        val antiguoUsername = usuario.username

        // Actualizar la información del usuario
        usuario.apply {
            username = usuarioUpdateDTO.newUsername ?: antiguoUsername
            email = usuarioUpdateDTO.email
            nombre = usuarioUpdateDTO.nombre
            apellidos = usuarioUpdateDTO.apellido
            descripcion = usuarioUpdateDTO.descripcion
            intereses = usuarioUpdateDTO.intereses
            fotoPerfilId = nuevaFotoPerfilId
            direccion = usuarioUpdateDTO.direccion

            if (usuarioUpdateDTO.password.isNotBlank()) {
                password = passwordEncoder.encode(usuarioUpdateDTO.password)
            }
        }

        val usuarioActualizado = usuarioRepository.save(usuario)

        // Si se ha cambiado el username, actualizar referencias en otras colecciones
        if (antiguoUsername != usuario.username) {
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

        // Retornar el DTO actualizado
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

    fun verUsuarioPorUsername(username: String):UsuarioDTO{
        val usuario=usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username not found")
        }
        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            intereses = usuario.intereses,
            fotoPerfilId = usuario.fotoPerfilId,
            direccion = usuario.direccion,
            descripcion = usuario.descripcion,
        )
    }

    fun verUsuariosPorComunidad(comunidad: String): List<UsuarioDTO> {
        // Verificar que la comunidad existe
        comunidadRepository.findComunidadByUrl(comunidad).orElseThrow {
            throw NotFoundException("Comunidad $comunidad no encontrada")
        }

        // Obtener la lista de participantes de la comunidad
        val participantes = participantesComunidadRepository.findParticipantesByComunidad(comunidad)

        // Crear una lista para almacenar los DTOs de usuario
        val listaUsuarios = mutableListOf<UsuarioDTO>()

        // Para cada participante, buscar su información completa y crear un DTO
        participantes.forEach { participante ->
            val usuario = usuarioRepository.findFirstByUsername(participante.username).orElseThrow {
                throw NotFoundException("Usuario ${participante.username} no encontrado")
            }

            // Crear y añadir el DTO a la lista
            listaUsuarios.add(
                UsuarioDTO(
                    username = usuario.username,
                    email = usuario.email,
                    intereses = usuario.intereses,
                    nombre = usuario.nombre,
                    apellido = usuario.apellidos,
                    fotoPerfilId = usuario.fotoPerfilId,
                    direccion = usuario.direccion,
                    descripcion = usuario.descripcion
                )
            )
        }

        // Devolver la lista de DTOs
        return listaUsuarios
    }


    fun verUsuariosPorActividad(actividadId: String): List<UsuarioDTO> {
        // Obtener la lista de participantes de la actividad
        val participantes = participantesActividadRepository.findByidActividad(actividadId)

        if (participantes.isEmpty()) {
            throw NotFoundException("No se encontraron participantes para la actividad con id $actividadId")
        }

        // Crear una lista para almacenar los DTOs de usuario
        val listaUsuarios = mutableListOf<UsuarioDTO>()

        // Para cada participante, buscar su información completa y crear un DTO
        participantes.forEach { participante ->
            val usuario = usuarioRepository.findFirstByUsername(participante.username).orElseThrow {
                throw NotFoundException("Usuario ${participante.username} no encontrado")
            }

            // Crear y añadir el DTO a la lista
            listaUsuarios.add(
                UsuarioDTO(
                    username = usuario.username,
                    email = usuario.email,
                    intereses = usuario.intereses,
                    nombre = usuario.nombre,
                    apellido = usuario.apellidos,
                    fotoPerfilId = usuario.fotoPerfilId,
                    direccion = usuario.direccion,
                    descripcion = usuario.descripcion
                )
            )
        }

        // Devolver la lista de DTOs
        return listaUsuarios
    }

    fun verTodosLosUsuarios(): List<UsuarioDTO> {
        return usuarioRepository.findAll().map { usuario ->
            UsuarioDTO(
                username = usuario.username,
                email = usuario.email,
                intereses = usuario.intereses,
                nombre = usuario.nombre,
                apellido = usuario.apellidos,
                fotoPerfilId = usuario.fotoPerfilId,
                direccion = usuario.direccion,
                descripcion = usuario.descripcion
            )
        }
    }
}
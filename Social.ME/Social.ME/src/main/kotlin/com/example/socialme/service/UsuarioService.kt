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
class UsuarioService:UserDetailsService {


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

        // COMPROBACIONES
        // Comprobar si los campos vienen vacíos
        if (usuarioInsertadoDTO.username.isBlank()
            || usuarioInsertadoDTO.email.isBlank()
            || usuarioInsertadoDTO.password.isBlank()
            || usuarioInsertadoDTO.passwordRepeat.isBlank()
        ) {

            throw BadRequestException("Uno o más campos vacíos")
        }

        if (usuarioInsertadoDTO.password.length < 4 || usuarioInsertadoDTO.password.length > 31) {
            throw BadRequestException("La contraseña debe estar entre 5 y 30 caracteres")
        }

        if (usuarioInsertadoDTO.username.length < 4 || usuarioInsertadoDTO.username.length > 31) {
            throw BadRequestException("El usuario debe estar entre 4 y 30 caracteres")
        }

        if (!"^[\\w\\.-]+@([\\w\\-]+\\.)+(com|org|net|es|terra|gmail)$".toRegex().matches(usuarioInsertadoDTO.email)) {
            throw BadRequestException("Introduce un email correcto")
        }


        // Fran ha comprobado que el usuario existe previamente
        if (usuarioRepository.findFirstByUsername(usuarioInsertadoDTO.username).isPresent) {
            throw BadRequestException("Usuario ${usuarioInsertadoDTO.username} ya está registrado")
        }

        // comprobar que ambas passwords sean iguales
        if (usuarioInsertadoDTO.password != usuarioInsertadoDTO.passwordRepeat) {
            throw BadRequestException("Las contraseñas no coinciden")
        }

        // Comprobar el ROL
        if (usuarioInsertadoDTO.rol != null && usuarioInsertadoDTO.rol != "USER" && usuarioInsertadoDTO.rol != "ADMIN") {
            throw BadRequestException("ROL: ${usuarioInsertadoDTO.rol} incorrecto")
        }

        // Comprobar el EMAIL


        // Comprobar la provincia
        val datosProvincias = externalApiService.obtenerDatosDesdeApi()
        var cpro: String = ""
        if (datosProvincias != null) {
            if (datosProvincias.data != null) {
                val provinciaEncontrada = datosProvincias.data.stream().filter {
                    it.PRO == usuarioInsertadoDTO.direccion?.provincia?.uppercase()
                }.findFirst().orElseThrow {
                    BadRequestException("Provincia ${usuarioInsertadoDTO.direccion?.provincia} no encontrada")
                }
                cpro = provinciaEncontrada.CPRO
            }
        }

        // Comprobar el municipio
        val datosMunicipios = externalApiService.obtenerMunicipiosDesdeApi(cpro)
        if (datosMunicipios != null) {
            if (datosMunicipios.data != null) {
                datosMunicipios.data.stream().filter {
                    it.DMUN50 == usuarioInsertadoDTO.direccion?.municipio?.uppercase()
                }.findFirst().orElseThrow {
                    BadRequestException("Municipio ${usuarioInsertadoDTO.direccion?.municipio} incorrecto")
                }
            }
        }


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
            fotoPerfil = usuarioInsertadoDTO.fotoPerfil,
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
            fotoPerfil = usuario.fotoPerfil,
        )

    }

    fun eliminarUsuario(username: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }
        val userDTO = UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            descripcion = usuario.descripcion,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            direccion = usuario.direccion,
            fotoPerfil = usuario.fotoPerfil,
        )
        usuarioRepository.delete(usuario)
        return userDTO

    }

    fun modificarUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): UsuarioDTO {
        // Validate input
        if (usuarioUpdateDTO.password != usuarioUpdateDTO.passwordRepeat) {
            throw BadRequestException("Las contraseñas no coinciden")
        }

        // Find existing user
        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.username).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.username} no encontrado")
        }

        // Store the old username for comparison
        val antiguoUsername = usuario.username

        // Update user information
        usuario.apply {
            username = usuarioUpdateDTO.username
            email = usuarioUpdateDTO.email
            nombre = usuarioUpdateDTO.nombre
            apellidos = usuarioUpdateDTO.apellido
            descripcion = usuarioUpdateDTO.descripcion
            intereses = usuarioUpdateDTO.intereses
            fotoPerfil = usuarioUpdateDTO.fotoPerfil
            direccion = usuarioUpdateDTO.direccion
        }

        val usuarioActualizado = usuarioRepository.save(usuario)

        // If username has changed, update references in other collections
        if (antiguoUsername != usuarioUpdateDTO.username) {
            // Update ParticipantesActividad
            val participantesActividad = participantesActividadRepository.findByUsername(antiguoUsername)
            participantesActividad.forEach { participante ->
                participante.username = usuarioUpdateDTO.username
                participantesActividadRepository.save(participante)
            }

            val participantesComunidad = participantesComunidadRepository.findByUsername(antiguoUsername)
            participantesComunidad.forEach { participante ->
                participante.username = usuarioUpdateDTO.username
                participantesComunidadRepository.save(participante)
            }
        }

        // Return updated user DTO
        return UsuarioDTO(
            username = usuarioUpdateDTO.username,
            email = usuarioUpdateDTO.email,
            intereses = usuarioUpdateDTO.intereses,
            nombre = usuarioUpdateDTO.nombre,
            apellido = usuarioUpdateDTO.apellido,
            fotoPerfil = usuarioUpdateDTO.fotoPerfil,
            direccion = usuarioUpdateDTO.direccion,
            descripcion = usuarioUpdateDTO.descripcion
        )
    }
}

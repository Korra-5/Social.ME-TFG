package com.example.socialme.service


import com.es.aplicacion.error.exception.BadRequestException
import com.es.aplicacion.error.exception.NotFoundException
import com.example.socialme.dto.UsuarioDTO
import com.example.socialme.dto.UsuarioRegisterDTO
import com.example.socialme.model.Usuario
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UsuarioService:UserDetailsService {


    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder
    @Autowired
    private lateinit var externalApiService: ExternalAPIService


        override fun loadUserByUsername(username: String?): UserDetails {
            var usuario: Usuario = usuarioRepository
                .findByNick(username!!)
                .orElseThrow {
                    NotFoundException("$username no existente")
                }

            return User.builder()
                .username(usuario.username)
                .password(usuario.password)
                .roles(usuario.roles)
                .build()
        }

        fun insertUser(usuarioInsertadoDTO: UsuarioRegisterDTO) : UsuarioDTO {

            // COMPROBACIONES
            // Comprobar si los campos vienen vacíos
            if (usuarioInsertadoDTO.username.isBlank()
                || usuarioInsertadoDTO.email.isBlank()
                || usuarioInsertadoDTO.password.isBlank()
                || usuarioInsertadoDTO.passwordRepeat.isBlank()) {

                throw BadRequestException("Uno o más campos vacíos")
            }

            if(usuarioInsertadoDTO.password.length<4||usuarioInsertadoDTO.password.length>31) {
                throw BadRequestException("La contraseña debe estar entre 5 y 30 caracteres")
            }

            if(usuarioInsertadoDTO.username.length<4||usuarioInsertadoDTO.username.length>31) {
                throw BadRequestException("El usuario debe estar entre 4 y 30 caracteres")
            }

            if (!"^[\\w\\.-]+@([\\w\\-]+\\.)+(com|org|net|es|terra|gmail)$".toRegex().matches(usuarioInsertadoDTO.email)) {
                throw BadRequestException("Introduce un email correcto")
            }


            // Fran ha comprobado que el usuario existe previamente
            if(usuarioRepository.findByNick(usuarioInsertadoDTO.username).isPresent) {
                throw BadRequestException("Usuario ${usuarioInsertadoDTO.username} ya está registrado")
            }

            // comprobar que ambas passwords sean iguales
            if(usuarioInsertadoDTO.password != usuarioInsertadoDTO.passwordRepeat) {
                throw BadRequestException("Las contraseñas no coinciden")
            }

            // Comprobar el ROL
            if(usuarioInsertadoDTO.rol != null && usuarioInsertadoDTO.rol != "USER" && usuarioInsertadoDTO.rol != "ADMIN" ) {
                throw BadRequestException("ROL: ${usuarioInsertadoDTO.rol} incorrecto")
            }

            // Comprobar el EMAIL


            // Comprobar la provincia
            val datosProvincias = externalApiService.obtenerDatosDesdeApi()
            var cpro: String = ""
            if(datosProvincias != null) {
                if(datosProvincias.data != null) {
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
            if(datosMunicipios != null) {
                if(datosMunicipios.data != null) {
                    datosMunicipios.data.stream().filter {
                        it.DMUN50 == usuarioInsertadoDTO.direccion?.municipio?.uppercase()
                    }.findFirst().orElseThrow {
                        BadRequestException("Municipio ${usuarioInsertadoDTO.direccion?.municipio} incorrecto")
                    }
                }
            }


            // Insertar el user (convierto a Entity)
            val usuario = Usuario(
                null,
                usuarioInsertadoDTO.username,
                passwordEncoder.encode(usuarioInsertadoDTO.password),
                usuarioInsertadoDTO.email,
                usuarioInsertadoDTO.rol.toString(),
                usuarioInsertadoDTO.direccion
            )

            // inserto en bd
            usuarioRepository.insert(usuario)

            // retorno un DTO
            return UsuarioDTO(
                usuario.username,
                usuario.email,
                usuario.roles
            )

        }}

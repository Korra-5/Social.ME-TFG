package com.example.socialme.controller

import com.example.socialme.dto.LoginUsuarioDTO
import com.example.socialme.dto.UsuarioDTO
import com.example.socialme.dto.UsuarioRegisterDTO
import com.example.socialme.dto.UsuarioUpdateDTO
import com.example.socialme.error.exception.UnauthorizedException
import com.example.socialme.service.TokenService
import com.example.socialme.service.UsuarioService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Usuario")
class UsuarioController {

    @Autowired
    private lateinit var authenticationManager: AuthenticationManager
    @Autowired
    private lateinit var tokenService: TokenService
    @Autowired
    private lateinit var usuarioService: UsuarioService

    @PostMapping("/register")
    fun insert(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioRegisterDTO: UsuarioRegisterDTO
    ) : ResponseEntity<UsuarioDTO> {
        val user = usuarioService.insertUser(usuarioRegisterDTO)
        return ResponseEntity(user, HttpStatus.CREATED)
    }

    @PostMapping("/login")
    fun login(@RequestBody usuario: LoginUsuarioDTO) : ResponseEntity<Any>? {

        val authentication: Authentication
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken(usuario.username, usuario.password))
        } catch (e: AuthenticationException) {
            throw UnauthorizedException("Credenciales incorrectas")
        }

        // SI PASAMOS LA AUTENTICACIÓN, SIGNIFICA QUE ESTAMOS BIEN AUTENTICADOS
        // PASAMOS A GENERAR EL TOKEN
        val token = tokenService.generarToken(authentication)
        usuarioService.modificarCoordenadasUsuario(usuario.coordenadas,usuario.username)
        return ResponseEntity(mapOf("token" to token), HttpStatus.CREATED)
    }

    @DeleteMapping("/eliminarUsuario/{username}")
    fun eliminarUsuario(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.eliminarUsuario(username), HttpStatus.OK)
    }

    @PutMapping("/modificarUsuario")
    fun modificarUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioUpdateDTO: UsuarioUpdateDTO
    ):ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.modificarUsuario(usuarioUpdateDTO), HttpStatus.OK)
    }

    @GetMapping("/verUsuarioPorUsername/{username}")
    fun verUsuarioPorUsername(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.verUsuarioPorUsername(username), HttpStatus.OK)
    }

    @GetMapping("/verUsuariosPorComunidad/{comunidad}")
    fun verUsuariosPorComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String
    ) : ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(usuarioService.verUsuariosPorComunidad(comunidad), HttpStatus.OK)
    }

    @GetMapping("/verUsuariosPorActividad/{actividadId}")
    fun verUsuariosPorActividad(
        httpRequest: HttpServletRequest,
        @PathVariable actividadId: String
    ) : ResponseEntity<List<UsuarioDTO>>  {
        return ResponseEntity(usuarioService.verUsuariosPorActividad(actividadId), HttpStatus.OK)
    }

    @GetMapping("/verTodosLosUsuarios")
    fun verTodosLosUsuarios(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(usuarioService.verTodosLosUsuarios(),HttpStatus.OK)

    }

}
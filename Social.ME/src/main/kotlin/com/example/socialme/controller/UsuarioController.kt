package com.example.socialme.controller

import com.example.socialme.dto.*
import com.example.socialme.error.exception.UnauthorizedException
import com.example.socialme.model.PaymentVerificationRequest
import com.example.socialme.model.VerificacionDTO
import com.example.socialme.service.PayPalService
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
import org.springframework.security.core.context.SecurityContextHolder
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

    @Autowired
    private lateinit var payPalService: PayPalService

    @PostMapping("/iniciarRegistro")
    fun iniciarRegistro(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioRegisterDTO: UsuarioRegisterDTO
    ): ResponseEntity<Map<String, String>> {
        val result = usuarioService.iniciarRegistroUsuario(usuarioRegisterDTO)
        return ResponseEntity(result, HttpStatus.OK)
    }

    @PostMapping("/completarRegistro")
    fun completarRegistro(@RequestBody verificacionDTO: VerificacionDTO): ResponseEntity<UsuarioDTO> {
        val usuario = usuarioService.verificarCodigoYCrearUsuario(verificacionDTO.email, verificacionDTO.codigo)
        return ResponseEntity(usuario, HttpStatus.CREATED)
    }

    @PostMapping("/login")
    fun login(@RequestBody usuario: LoginUsuarioDTO): ResponseEntity<Any>? {

        val authentication: Authentication
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(
                    usuario.username,
                    usuario.password
                )
            )
        } catch (e: AuthenticationException) {
            throw UnauthorizedException("Credenciales incorrectas")
        }

        // SI PASAMOS LA AUTENTICACIÓN, SIGNIFICA QUE ESTAMOS BIEN AUTENTICADOS
        // PASAMOS A GENERAR EL TOKEN
        val token = tokenService.generarToken(authentication)
        usuarioService.modificarCoordenadasUsuario(usuario.coordenadas, usuario.username)
        return ResponseEntity(mapOf("token" to token), HttpStatus.CREATED)
    }

    @DeleteMapping("/eliminarUsuario/{username}")
    fun eliminarUsuario(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.eliminarUsuario(username), HttpStatus.OK)
    }

    @PutMapping("/iniciarModificacionUsuario")
    fun iniciarModificacionUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioUpdateDTO: UsuarioUpdateDTO
    ): ResponseEntity<Map<String, String>> {
        val result = usuarioService.iniciarModificacionUsuario(usuarioUpdateDTO)
        return ResponseEntity(result, HttpStatus.OK)
    }

    @PostMapping("/completarModificacionUsuario")
    fun completarModificacionUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody verificacionDTO: VerificacionDTO
    ): ResponseEntity<UsuarioDTO> {
        val usuario = usuarioService.verificarCodigoYModificarUsuario(verificacionDTO.email, verificacionDTO.codigo)
        return ResponseEntity(usuario, HttpStatus.OK)
    }

    @PutMapping("/modificarUsuario")
    fun modificarUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioUpdateDTO: UsuarioUpdateDTO
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.modificarUsuario(usuarioUpdateDTO), HttpStatus.OK)
    }

    @PutMapping("/cambiarContrasena")
    fun cambiarContrasena(
        httpRequest: HttpServletRequest,
        @RequestBody cambiarContrasenaDTO: CambiarContrasenaDTO
        ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.cambiarContrasena(cambiarContrasenaDTO), HttpStatus.OK)
    }

    @GetMapping("/verUsuarioPorUsername/{username}")
    fun verUsuarioPorUsername(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.verUsuarioPorUsername(username), HttpStatus.OK)
    }

    @GetMapping("/usuarioEsAdmin/{username}")
    fun usuarioEsAdmin(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ):ResponseEntity<Boolean> {
        return ResponseEntity(usuarioService.usuarioEsAdmin(username), HttpStatus.OK)
    }

    @GetMapping("/verUsuariosPorComunidad/{comunidad}")
    fun verUsuariosPorComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String,
        @RequestParam usuarioActual: String
    ): ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(usuarioService.verUsuariosPorComunidad(comunidad, usuarioActual), HttpStatus.OK)
    }

    @GetMapping("/verUsuariosPorActividad/{actividadId}")
    fun verUsuariosPorActividad(
        httpRequest: HttpServletRequest,
        @PathVariable actividadId: String,
        @RequestParam usuarioActual: String
    ): ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(usuarioService.verUsuariosPorActividad(actividadId, usuarioActual), HttpStatus.OK)
    }

    @GetMapping("/verTodosLosUsuarios/{username}")
    fun verTodosLosUsuarios(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(usuarioService.verTodosLosUsuarios(username), HttpStatus.OK)
    }

    @PostMapping("/verificarCodigo")
    fun verificarCodigo(@RequestBody verificacionDTO: VerificacionDTO): ResponseEntity<Boolean> {
        val resultado = usuarioService.verificarCodigo(verificacionDTO.email, verificacionDTO.codigo)
        return ResponseEntity.ok(resultado)
    }

    @GetMapping("/reenviarCodigo/{email}")
    fun reenviarCodigo(@PathVariable email: String): ResponseEntity<Boolean> {
        val resultado = usuarioService.verificarGmail(email)
        return ResponseEntity.ok(resultado)
    }

    @PutMapping("/actualizarPremium/{username}")
    fun actualizarPremium(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.actualizarPremium(username), HttpStatus.OK)
    }

    @GetMapping("/verSolicitudesAmistad/{username}")
    fun verSolicitudesAmistad(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<SolicitudAmistadDTO>> {
        return ResponseEntity(usuarioService.verSolicitudesAmistad(username), HttpStatus.OK)
    }

    @GetMapping("/verAmigos/{username}")
    fun verAmigos(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(usuarioService.verAmigos(username), HttpStatus.OK)
    }

    @PostMapping("/enviarSolicitudAmistad")
    fun enviarSolicitudAmistad(
        httpRequest: HttpServletRequest,
        @RequestBody solicitudAmistadDTO: SolicitudAmistadDTO
    ): ResponseEntity<SolicitudAmistadDTO> {
        return ResponseEntity(usuarioService.enviarSolicitudAmistad(solicitudAmistadDTO), HttpStatus.CREATED)
    }

    @PutMapping("/aceptarSolicitud/{id}")
    fun aceptarSolicitud(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<Boolean> {
        return ResponseEntity(usuarioService.aceptarSolicitud(id), HttpStatus.OK)
    }

    @PostMapping("/bloquearUsuario")
    fun bloquearUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody bloqueoDTO: BloqueoDTO
    ): ResponseEntity<BloqueoDTO> {
        return ResponseEntity(
            usuarioService.bloquearUsuario(bloqueoDTO.bloqueador, bloqueoDTO.bloqueado),
            HttpStatus.CREATED
        )
    }

    @DeleteMapping("/desbloquearUsuario")
    fun desbloquearUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody bloqueoDTO: BloqueoDTO
    ): ResponseEntity<Boolean> {
        return ResponseEntity(
            usuarioService.desbloquearUsuario(bloqueoDTO.bloqueador, bloqueoDTO.bloqueado),
            HttpStatus.OK
        )
    }

    @GetMapping("/verUsuariosBloqueados/{username}")
    fun verUsuariosBloqueados(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<UsuarioDTO>> {
        return ResponseEntity(
            usuarioService.verUsuariosBloqueados(username),
            HttpStatus.OK
        )
    }

    @PutMapping("/cambiarPrivacidadComunidad/{username}/{privacidad}")
    fun cambiarPrivacidadComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable privacidad:String,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(
            usuarioService.cambiarPrivacidadComunidad(username, privacidad),
            HttpStatus.OK
        )
    }

    @PutMapping("/cambiarPrivacidadActividad/{username}/{privacidad}")
    fun cambiarPrivacidadActividad(
        httpRequest: HttpServletRequest,
        @PathVariable privacidad:String,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(
            usuarioService.cambiarPrivacidadActividad(username, privacidad),
            HttpStatus.OK
        )
    }

    @GetMapping("/verPrivacidadActividad/{username}")
    fun verPrivacidadActividad(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<String> {
        return ResponseEntity(
            usuarioService.verPrivacidadActividad(username),
            HttpStatus.OK
        )
    }

    @GetMapping("/verPrivacidadComunidad/{username}")
    fun verPrivacidadComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<String> {
        return ResponseEntity(
            usuarioService.verPrivacidadComunidad(username),
            HttpStatus.OK
        )
    }

    @PutMapping("/cambiarRadarDistancia/{username}/{radar}")
    fun cambiarRadarDistancia(
        httpRequest: HttpServletRequest,
        @PathVariable username: String,
        @PathVariable radar: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(
            usuarioService.cambiarRadarDistancia(username, radar),
            HttpStatus.OK
        )
    }

    @GetMapping("/verRadarDistancia/{username}")
    fun verRadarDistancia(
        httpRequest: HttpServletRequest,
        @PathVariable username: String,
    ): ResponseEntity<String> {
        return ResponseEntity(
            usuarioService.verRadarDistancia(username),
            HttpStatus.OK
        )
    }

    @PostMapping("/verificarPremium")
    fun verificarPremium(
        httpRequest: HttpServletRequest,
        @RequestBody paymentData: PaymentVerificationRequest
    ): ResponseEntity<Map<String, Any>> {
        // Verificar el pago con PayPal
        val isValidPayment = payPalService.verifyPayment(paymentData.paymentId)

        return if (isValidPayment) {
            // Actualizar usuario a premium
            val usuario = usuarioService.actualizarPremium(paymentData.username)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Premium activado correctamente",
                    "usuario" to usuario
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to "El pago no pudo ser verificado"
                )
            )
        }
    }

}
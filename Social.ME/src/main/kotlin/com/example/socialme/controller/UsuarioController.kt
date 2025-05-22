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

    // NUEVO ENDPOINT: Iniciar registro (solo envía código)
    @PostMapping("/iniciarRegistro")
    fun iniciarRegistro(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioRegisterDTO: UsuarioRegisterDTO
    ): ResponseEntity<Map<String, String>> {
        return try {
            val resultado = usuarioService.iniciarRegistro(usuarioRegisterDTO)
            if (resultado) {
                ResponseEntity.ok(mapOf(
                    "success" to "true",
                    "message" to "Código de verificación enviado al email ${usuarioRegisterDTO.email}"
                ))
            } else {
                ResponseEntity.badRequest().body(mapOf(
                    "success" to "false",
                    "message" to "Error al enviar código de verificación"
                ))
            }
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to "false",
                "message" to (e.message ?: "Error desconocido")
            ))
        }
    }

    // NUEVO ENDPOINT: Confirmar registro con código
    @PostMapping("/confirmarRegistro")
    fun confirmarRegistro(
        httpRequest: HttpServletRequest,
        @RequestBody confirmacionDTO: ConfirmacionRegistroDTO
    ): ResponseEntity<Any> {
        return try {
            val usuario = usuarioService.confirmarRegistro(confirmacionDTO.email, confirmacionDTO.codigo)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Usuario registrado correctamente",
                "usuario" to usuario
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Error al confirmar registro")
            ))
        }
    }

    // ENDPOINT ORIGINAL mantenido para compatibilidad (DEPRECATED)
    @PostMapping("/register")
    @Deprecated("Usar /iniciarRegistro y /confirmarRegistro en su lugar")
    fun insert(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioRegisterDTO: UsuarioRegisterDTO
    ): ResponseEntity<UsuarioDTO> {
        val user = usuarioService.insertUser(usuarioRegisterDTO)
        return ResponseEntity(user, HttpStatus.CREATED)
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

    // NUEVO ENDPOINT: Iniciar modificación (puede requerir verificación de email)
    @PostMapping("/iniciarCambioEmail")
    fun iniciarCambioEmail(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioUpdateDTO: UsuarioUpdateDTO
    ): ResponseEntity<Any> {
        return try {
            val usuario = usuarioService.iniciarCambioEmail(usuarioUpdateDTO)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "usuario" to usuario,
                "message" to "Si has cambiado el email, verifica el código enviado"
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Error al actualizar usuario")
            ))
        }
    }

    // NUEVO ENDPOINT: Confirmar cambio de email con código
    @PostMapping("/confirmarCambioEmail")
    fun confirmarCambioEmail(
        httpRequest: HttpServletRequest,
        @RequestBody confirmacionDTO: ConfirmarCambioEmailDTO
    ): ResponseEntity<Any> {
        return try {
            val usuario = usuarioService.confirmarCambioEmail(confirmacionDTO.username, confirmacionDTO.codigo)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Email actualizado correctamente",
                "usuario" to usuario
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Error al confirmar cambio de email")
            ))
        }
    }

    // ENDPOINT ORIGINAL mantenido para compatibilidad (DEPRECATED)
    @PutMapping("/modificarUsuario")
    @Deprecated("Usar /iniciarCambioEmail y /confirmarCambioEmail en su lugar")
    fun modificarUsuario(
        httpRequest: HttpServletRequest,
        @RequestBody usuarioUpdateDTO: UsuarioUpdateDTO
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.modificarUsuario(usuarioUpdateDTO), HttpStatus.OK)
    }

    @GetMapping("/verUsuarioPorUsername/{username}")
    fun verUsuarioPorUsername(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.verUsuarioPorUsername(username), HttpStatus.OK)
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

    // NUEVOS ENDPOINTS: Verificar registros pendientes
    @GetMapping("/verificarRegistroPendiente/{email}")
    fun verificarRegistroPendiente(
        httpRequest: HttpServletRequest,
        @PathVariable email: String
    ): ResponseEntity<Map<String, Boolean>> {
        val hayPendiente = usuarioService.hayRegistroPendiente(email)
        return ResponseEntity.ok(mapOf("pendiente" to hayPendiente))
    }

    @GetMapping("/verificarCambioEmailPendiente/{username}")
    fun verificarCambioEmailPendiente(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<Map<String, Boolean>> {
        val hayPendiente = usuarioService.hayCambioEmailPendiente(username)
        return ResponseEntity.ok(mapOf("pendiente" to hayPendiente))
    }

    @PutMapping("/actualizarPremium/{username}")
    fun actualizarPremium(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<UsuarioDTO> {
        return ResponseEntity(usuarioService.actualizarPremium(username), HttpStatus.OK)
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

    // ==================== AMISTADES ====================

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

    // ==================== BLOQUEOS ====================

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

    @GetMapping("/existeBloqueo/{usuario1}/{usuario2}")
    fun existeBloqueo(
        httpRequest: HttpServletRequest,
        @PathVariable usuario1: String,
        @PathVariable usuario2: String
    ): ResponseEntity<Boolean> {
        return ResponseEntity(
            usuarioService.existeBloqueo(usuario1, usuario2),
            HttpStatus.OK
        )
    }
}
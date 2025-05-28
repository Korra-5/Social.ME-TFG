package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.*
import com.example.socialme.repository.*
import com.example.socialme.utils.ContentValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import javax.mail.*
import javax.mail.internet.*

@Service
class UsuarioService : UserDetailsService {

    @Autowired
    private lateinit var mensajeRepository: MensajeRepository

    @Autowired
    private lateinit var notificacionRepository: NotificacionRepository

    @Autowired
    private lateinit var bloqueoRepository: BloqueoRepository

    @Autowired
    private lateinit var denunciaRepository: DenunciaRepository

    @Autowired
    private lateinit var actividadRepository: ActividadRepository

    @Autowired
    private lateinit var solicitudesAmistadRepository: SolicitudesAmistadRepository

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
    // Reemplaza solo estos métodos en UsuarioService.kt para debug

    // Mapas con timestamp para expiración
    private val verificacionCodigos = mutableMapOf<String, Pair<String, Long>>()
    private val usuariosPendientesVerificacion = mutableMapOf<String, UsuarioRegisterDTO>()
    private val modificacionesPendientesVerificacion = mutableMapOf<String, UsuarioUpdateDTO>()

    // Tiempo de expiración del código en milisegundos (15 minutos)
    private val CODIGO_EXPIRACION_MS = 15 * 60 * 1000L

    override fun loadUserByUsername(username: String?): UserDetails {
        val usuario: Usuario = usuarioRepository.findFirstByUsername(username!!)
            .orElseThrow { NotFoundException("$username no existente") }

        return User.builder()
            .username(usuario.username)
            .password(usuario.password)
            .roles(usuario.roles)
            .build()
    }



    fun iniciarModificacionUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): Map<String, String> {
        println("==========================================")
        println("🔄 INICIO MODIFICACION USUARIO")
        println("==========================================")
        println("📝 Current username: '${usuarioUpdateDTO.currentUsername}'")
        println("📧 New email: '${usuarioUpdateDTO.email}'")
        println("👤 New username: '${usuarioUpdateDTO.newUsername}'")
        println("📛 Nombre: '${usuarioUpdateDTO.nombre}'")
        println("📛 Apellido: '${usuarioUpdateDTO.apellido}'")

        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.currentUsername).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.currentUsername} no encontrado")
        }

        println("🔍 Usuario encontrado en BD:")
        println("   - Username: '${usuario.username}'")
        println("   - Email actual: '${usuario.email}'")
        println("   - Nombre actual: '${usuario.nombre}'")
        println("   - Apellido actual: '${usuario.apellidos}'")

        // VALIDAR CONTENIDO INAPROPIADO
        ContentValidator.validarContenidoInapropiado(
            usuarioUpdateDTO.newUsername ?: "",
            usuarioUpdateDTO.nombre ?: "",
            usuarioUpdateDTO.apellido ?: "",
            usuarioUpdateDTO.descripcion ?: ""
        )
        println("✅ Validación de contenido inapropiado pasada")

        // Si se está cambiando el username, validar que el nuevo no exista ya
        if (usuarioUpdateDTO.newUsername != null && usuarioUpdateDTO.newUsername != usuarioUpdateDTO.currentUsername) {
            if (usuarioRepository.existsByUsername(usuarioUpdateDTO.newUsername)) {
                println("❌ ERROR: Username '${usuarioUpdateDTO.newUsername}' ya existe")
                throw BadRequestException("El nombre de usuario ${usuarioUpdateDTO.newUsername} ya está en uso, prueba con otro nombre")
            }
            println("✅ Nuevo username '${usuarioUpdateDTO.newUsername}' disponible")
        }

        // Verificar si el email ha cambiado
        val emailCambiado = usuarioUpdateDTO.email != null && usuarioUpdateDTO.email != usuario.email

        println("==========================================")
        println("📧 VERIFICACIÓN DE EMAIL")
        println("==========================================")
        println("Email DTO: '${usuarioUpdateDTO.email}' (${usuarioUpdateDTO.email?.javaClass?.simpleName})")
        println("Email BD: '${usuario.email}' (${usuario.email.javaClass.simpleName})")
        println("Email es null en DTO: ${usuarioUpdateDTO.email == null}")
        println("Emails son iguales: ${usuarioUpdateDTO.email == usuario.email}")
        println("📨 EMAIL CAMBIÓ: $emailCambiado")
        println("==========================================")

        if (emailCambiado) {
            val nuevoEmail = usuarioUpdateDTO.email!!

            println("🔄 PROCESANDO CAMBIO DE EMAIL A: '$nuevoEmail'")

            // Verificar que el nuevo email no esté en uso por otro usuario
            if (usuarioRepository.existsByEmail(nuevoEmail)) {
                println("❌ ERROR: Email '$nuevoEmail' ya está registrado")
                throw BadRequestException("El email $nuevoEmail ya está registrado por otro usuario")
            }
            println("✅ Email '$nuevoEmail' disponible")

            // Estado actual de los mapas ANTES de limpiar
            println("📊 ESTADO DE MAPAS ANTES DE LIMPIAR:")
            println("   - Códigos de verificación: ${verificacionCodigos.keys}")
            println("   - Modificaciones pendientes: ${modificacionesPendientesVerificacion.keys}")

            // LIMPIAR códigos anteriores para este email
            println("🧹 Limpiando códigos anteriores para: '$nuevoEmail'")
            verificacionCodigos.remove(nuevoEmail)
            modificacionesPendientesVerificacion.remove(nuevoEmail)
            limpiarCodigosExpirados()

            // Estado después de limpiar
            println("📊 ESTADO DESPUÉS DE LIMPIAR:")
            println("   - Códigos de verificación: ${verificacionCodigos.keys}")
            println("   - Modificaciones pendientes: ${modificacionesPendientesVerificacion.keys}")

            // Enviar código de verificación al nuevo email
            println("📤 ENVIANDO CÓDIGO DE VERIFICACIÓN A: '$nuevoEmail'")
            if (!enviarCodigoVerificacion(nuevoEmail)) {
                println("❌ ERROR: No se pudo enviar código a '$nuevoEmail'")
                throw BadRequestException("No se pudo enviar el código de verificación al correo $nuevoEmail")
            }

            // GUARDAR los datos de modificación temporalmente hasta que verifique el email
            modificacionesPendientesVerificacion[nuevoEmail] = usuarioUpdateDTO

            println("💾 Guardando datos de modificación para: '$nuevoEmail'")
            println("📊 ESTADO FINAL DE MAPAS:")
            println("   - Códigos de verificación: ${verificacionCodigos.keys}")
            println("   - Modificaciones pendientes: ${modificacionesPendientesVerificacion.keys}")

            val resultado = mapOf(
                "message" to "Código de verificación enviado al correo $nuevoEmail",
                "email" to nuevoEmail,
                "requiresVerification" to "true"
            )

            println("🎯 RESULTADO: $resultado")
            println("==========================================")
            return resultado
        } else {
            println("⚡ NO HAY CAMBIO DE EMAIL - Aplicando modificación directamente")
            // Si no cambió el email, aplicar cambios directamente
            return aplicarModificacionUsuario(usuarioUpdateDTO)
        }
    }

    fun verificarCodigoYModificarUsuario(email: String, codigo: String): UsuarioDTO {
        println("==========================================")
        println("🔐 VERIFICACION CODIGO MODIFICACION")
        println("==========================================")
        println("📧 Email recibido: '$email'")
        println("🔢 Código recibido: '$codigo'")
        println("🔢 Longitud del código: ${codigo.length}")
        println("🔢 Código como bytes: ${codigo.toByteArray().contentToString()}")

        // Estado actual de los mapas
        println("📊 ESTADO ACTUAL DE MAPAS:")
        println("   - Códigos de verificación: ${verificacionCodigos.keys}")
        println("   - Códigos de verificación completos: $verificacionCodigos")
        println("   - Modificaciones pendientes: ${modificacionesPendientesVerificacion.keys}")

        // Limpiar códigos expirados
        println("🧹 Limpiando códigos expirados...")
        limpiarCodigosExpirados()

        println("📊 DESPUÉS DE LIMPIAR EXPIRADOS:")
        println("   - Códigos de verificación: ${verificacionCodigos.keys}")

        // Verificar que existe un código para este email
        val codigoData = verificacionCodigos[email]
        if (codigoData == null) {
            println("❌ ERROR: No se encontró código para email: '$email'")
            println("📊 Códigos disponibles: ${verificacionCodigos.keys}")
            println("📊 Códigos completos: $verificacionCodigos")
            throw BadRequestException("No se encontró código de verificación para este email o el código ha expirado")
        }

        val (codigoAlmacenado, timestamp) = codigoData
        val tiempoActual = System.currentTimeMillis()
        val tiempoTranscurrido = tiempoActual - timestamp

        println("✅ CÓDIGO ENCONTRADO:")
        println("   - Código almacenado: '$codigoAlmacenado'")
        println("   - Longitud almacenado: ${codigoAlmacenado.length}")
        println("   - Código como bytes: ${codigoAlmacenado.toByteArray().contentToString()}")
        println("   - Timestamp: $timestamp")
        println("   - Tiempo actual: $tiempoActual")
        println("   - Tiempo transcurrido: ${tiempoTranscurrido}ms")
        println("   - Tiempo límite: ${CODIGO_EXPIRACION_MS}ms")
        println("   - ¿Expirado?: ${tiempoTranscurrido > CODIGO_EXPIRACION_MS}")

        // Verificar que el código no haya expirado
        if (tiempoTranscurrido > CODIGO_EXPIRACION_MS) {
            println("❌ ERROR: Código expirado (${tiempoTranscurrido}ms > ${CODIGO_EXPIRACION_MS}ms)")
            verificacionCodigos.remove(email)
            modificacionesPendientesVerificacion.remove(email)
            throw BadRequestException("El código de verificación ha expirado. Solicita uno nuevo.")
        }
        println("✅ Código no expirado")

        // Verificar el código (con múltiples comparaciones para debug)
        val codigoLimpio = codigo.trim()
        val codigoAlmacenadoLimpio = codigoAlmacenado.trim()

        println("🔍 COMPARACIÓN DE CÓDIGOS:")
        println("   - Código recibido original: '$codigo'")
        println("   - Código recibido limpio: '$codigoLimpio'")
        println("   - Código almacenado original: '$codigoAlmacenado'")
        println("   - Código almacenado limpio: '$codigoAlmacenadoLimpio'")
        println("   - ¿Son iguales (originales)?: ${codigo == codigoAlmacenado}")
        println("   - ¿Son iguales (limpios)?: ${codigoLimpio == codigoAlmacenadoLimpio}")
        println("   - ¿Son iguales (equals)?: ${codigo.equals(codigoAlmacenado)}")
        println("   - ¿Son iguales (compareTo)?: ${codigo.compareTo(codigoAlmacenado) == 0}")

        if (codigoAlmacenadoLimpio != codigoLimpio) {
            println("❌ ERROR: Códigos no coinciden")
            println("   - Esperado: '$codigoAlmacenadoLimpio' (${codigoAlmacenadoLimpio.length} chars)")
            println("   - Recibido: '$codigoLimpio' (${codigoLimpio.length} chars)")

            // Comparación carácter por carácter
            val maxLen = maxOf(codigoAlmacenadoLimpio.length, codigoLimpio.length)
            for (i in 0 until maxLen) {
                val charAlmacenado = if (i < codigoAlmacenadoLimpio.length) codigoAlmacenadoLimpio[i] else "NULL"
                val charRecibido = if (i < codigoLimpio.length) codigoLimpio[i] else "NULL"
                println("     [$i]: '$charAlmacenado' vs '$charRecibido' ${if (charAlmacenado == charRecibido) "✅" else "❌"}")
            }

            throw BadRequestException("Código de verificación incorrecto")
        }
        println("✅ CÓDIGOS COINCIDEN")

        // Obtener los datos de modificación pendiente
        val modificacionData = modificacionesPendientesVerificacion[email]
        if (modificacionData == null) {
            println("❌ ERROR: No se encontraron datos de modificación para: '$email'")
            println("📊 Modificaciones pendientes: ${modificacionesPendientesVerificacion.keys}")
            throw BadRequestException("No se encontraron datos de modificación para este email")
        }
        println("✅ Datos de modificación encontrados para: '$email'")

        println("🔄 Aplicando modificación...")

        // Aplicar la modificación
        val resultado = aplicarModificacionUsuarioInterno(modificacionData)

        // LIMPIAR datos temporales
        println("🧹 Limpiando datos temporales...")
        verificacionCodigos.remove(email)
        modificacionesPendientesVerificacion.remove(email)

        println("✅ MODIFICACIÓN COMPLETADA EXITOSAMENTE")
        println("📊 ESTADO FINAL:")
        println("   - Códigos de verificación: ${verificacionCodigos.keys}")
        println("   - Modificaciones pendientes: ${modificacionesPendientesVerificacion.keys}")
        println("==========================================")

        return resultado
    }

    private fun enviarCodigoVerificacion(email: String): Boolean {
        println("==========================================")
        println("📤 ENVIANDO CODIGO VERIFICACION")
        println("==========================================")
        println("📧 Enviando código a: '$email'")

        // Configuración para el servidor de correo
        val props = Properties()
        props.put("mail.smtp.auth", "true")
        props.put("mail.smtp.starttls.enable", "true")
        props.put("mail.smtp.host", "smtp.gmail.com")
        props.put("mail.smtp.port", "587")

        // Credenciales de la cuenta de correo
        val username = System.getenv("EMAIL_USERNAME") ?: ""
        val password = System.getenv("EMAIL_PASSWORD") ?: ""

        if (username.isEmpty() || password.isEmpty()) {
            println("❌ ERROR: Credenciales de email no configuradas")
            println("   - EMAIL_USERNAME: ${if (username.isEmpty()) "VACÍO" else "CONFIGURADO"}")
            println("   - EMAIL_PASSWORD: ${if (password.isEmpty()) "VACÍO" else "CONFIGURADO"}")
            return false
        }
        println("✅ Credenciales de email configuradas")

        try {
            // Crear sesión con autenticación
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })
            println("✅ Sesión de email creada")

            // Crear el mensaje
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(username))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            message.subject = "Verificación de correo electrónico - SocialMe"
            println("✅ Mensaje de email creado")

            // Generar un código aleatorio para verificación
            val codigoVerificacion = generarCodigoVerificacion()
            val timestamp = System.currentTimeMillis()

            println("🔢 CÓDIGO GENERADO:")
            println("   - Código: '$codigoVerificacion'")
            println("   - Longitud: ${codigoVerificacion.length}")
            println("   - Timestamp: $timestamp")
            println("   - Código como bytes: ${codigoVerificacion.toByteArray().contentToString()}")

            // Almacenar el código con timestamp para verificación posterior
            verificacionCodigos[email] = Pair(codigoVerificacion, timestamp)

            println("💾 CÓDIGO ALMACENADO:")
            println("   - Email: '$email'")
            println("   - Código almacenado: '${verificacionCodigos[email]?.first}'")
            println("   - Timestamp almacenado: ${verificacionCodigos[email]?.second}")
            println("📊 Códigos activos después de almacenar: ${verificacionCodigos.keys}")

            // Crear el contenido del mensaje
            val htmlContent = """
        <html>
            <body>
                <h2>Verificación de correo electrónico - SocialMe</h2>
                <p>Gracias por actualizar tu información. Para verificar tu nueva dirección de correo electrónico, 
                por favor utiliza el siguiente código:</p>
                <h3 style="background-color: #f2f2f2; padding: 10px; text-align: center; font-family: monospace; letter-spacing: 2px; font-size: 24px;">$codigoVerificacion</h3>
                <p>Si no has solicitado esta verificación, por favor ignora este mensaje.</p>
                <p><strong>Este código expirará en 15 minutos.</strong></p>
                <p><strong>Nota importante:</strong> Introduce el código exactamente como se muestra: <code>$codigoVerificacion</code></p>
            </body>
        </html>
    """.trimIndent()

            // Establecer el contenido del mensaje como HTML
            message.setContent(htmlContent, "text/html; charset=utf-8")
            println("✅ Contenido del mensaje establecido")

            // Enviar el mensaje
            println("📤 Enviando mensaje...")
            Transport.send(message)

            println("✅ CORREO DE VERIFICACIÓN ENVIADO EXITOSAMENTE")
            println("📧 Email: '$email'")
            println("🔢 Código: '$codigoVerificacion'")
            println("==========================================")
            return true

        } catch (e: MessagingException) {
            e.printStackTrace()
            println("❌ ERROR AL ENVIAR CORREO:")
            println("   - Excepción: ${e.javaClass.simpleName}")
            println("   - Mensaje: ${e.message}")
            println("   - Stack trace: ${e.stackTraceToString()}")
            println("==========================================")
            return false
        }
    }

    private fun limpiarCodigosExpirados() {
        println("🧹 LIMPIANDO CÓDIGOS EXPIRADOS...")
        val ahora = System.currentTimeMillis()

        println("⏰ Tiempo actual: $ahora")
        println("⏰ Tiempo límite: ${CODIGO_EXPIRACION_MS}ms")

        val codigosExpirados = verificacionCodigos.filterValues { (_, timestamp) ->
            val tiempoTranscurrido = ahora - timestamp
            val expirado = tiempoTranscurrido > CODIGO_EXPIRACION_MS
            println("   - Email: timestamp=$timestamp, transcurrido=${tiempoTranscurrido}ms, expirado=$expirado")
            expirado
        }

        println("🗑️ Códigos a eliminar: ${codigosExpirados.keys}")

        codigosExpirados.keys.forEach { email ->
            println("   - Eliminando código expirado para: '$email'")
            verificacionCodigos.remove(email)
            modificacionesPendientesVerificacion.remove(email)
        }

        if (codigosExpirados.isNotEmpty()) {
            println("✅ Se limpiaron ${codigosExpirados.size} códigos expirados")
        } else {
            println("✅ No hay códigos expirados para limpiar")
        }

        println("📊 Códigos restantes: ${verificacionCodigos.keys}")
    }

    private fun generarCodigoVerificacion(): String {
        val codigo = (100000..999999).random().toString()
        println("🎲 Código generado por Random: '$codigo'")
        return codigo
    }

    fun verificarCodigo(email: String, codigo: String): Boolean {
        println("==========================================")
        println("🔍 VERIFICAR CODIGO SIMPLE")
        println("==========================================")
        println("📧 Email: '$email'")
        println("🔢 Código: '$codigo'")

        limpiarCodigosExpirados()

        val codigoData = verificacionCodigos[email]
        if (codigoData == null) {
            println("❌ No se encontró código para: '$email'")
            println("📊 Códigos disponibles: ${verificacionCodigos.keys}")
            return false
        }

        val (codigoAlmacenado, timestamp) = codigoData

        // Verificar expiración
        val tiempoTranscurrido = System.currentTimeMillis() - timestamp
        if (tiempoTranscurrido > CODIGO_EXPIRACION_MS) {
            println("❌ Código expirado para: '$email' (${tiempoTranscurrido}ms)")
            verificacionCodigos.remove(email)
            return false
        }

        val resultado = codigoAlmacenado.trim() == codigo.trim()
        println("🔍 Comparación:")
        println("   - Almacenado: '$codigoAlmacenado'")
        println("   - Recibido: '$codigo'")
        println("   - Resultado: $resultado")
        println("==========================================")

        return resultado
    }

    // MANTENER REGISTRO COMO ESTÁ (FUNCIONA)
    fun iniciarRegistroUsuario(usuarioInsertadoDTO: UsuarioRegisterDTO): Map<String, String> {

        // VALIDAR CONTENIDO INAPROPIADO
        ContentValidator.validarContenidoInapropiado(
            usuarioInsertadoDTO.username,
            usuarioInsertadoDTO.nombre,
            usuarioInsertadoDTO.apellidos,
            usuarioInsertadoDTO.descripcion
        )

        if (usuarioRepository.existsByUsername(usuarioInsertadoDTO.username)) {
            throw BadRequestException("El nombre de usuario ${usuarioInsertadoDTO.username} ya está en uso")
        }

        // Verificar que el email no existe
        if (usuarioRepository.existsByEmail(usuarioInsertadoDTO.email)) {
            throw BadRequestException("El email ${usuarioInsertadoDTO.email} ya está registrado")
        }

        // Enviar código de verificación por email
        if (!enviarCodigoVerificacion(usuarioInsertadoDTO.email)) {
            throw BadRequestException("No se pudo enviar el código de verificación al correo ${usuarioInsertadoDTO.email}")
        }

        // GUARDAR los datos del usuario temporalmente hasta que verifique el email
        usuariosPendientesVerificacion[usuarioInsertadoDTO.email] = usuarioInsertadoDTO

        return mapOf(
            "message" to "Código de verificación enviado al correo ${usuarioInsertadoDTO.email}",
            "email" to usuarioInsertadoDTO.email
        )
    }

    // MANTENER REGISTRO COMO ESTÁ (FUNCIONA)
    fun verificarCodigoYCrearUsuario(email: String, codigo: String): UsuarioDTO {

        // Verificar el código - MANTENER LÓGICA ORIGINAL PARA REGISTRO
        val codigoData = verificacionCodigos[email]
        val codigoAlmacenado = codigoData?.first
        if (codigoAlmacenado != codigo) {
            throw BadRequestException("Código de verificación incorrecto")
        }

        // Obtener los datos del usuario pendiente
        val usuarioData = usuariosPendientesVerificacion[email]
            ?: throw BadRequestException("No se encontraron datos de registro para este email")

        // Verificar nuevamente que el username y email no existan (por si acaso)
        if (usuarioRepository.existsByUsername(usuarioData.username)) {
            throw BadRequestException("El nombre de usuario ${usuarioData.username} ya está en uso")
        }

        if (usuarioRepository.existsByEmail(usuarioData.email)) {
            throw BadRequestException("El email ${usuarioData.email} ya está registrado")
        }

        // Procesar la foto de perfil si se proporciona en Base64
        val fotoPerfilId: String =
            if (usuarioData.fotoPerfilBase64 != null && usuarioData.fotoPerfilBase64.isNotBlank()) {
                gridFSService.storeFileFromBase64(
                    usuarioData.fotoPerfilBase64,
                    "profile_${usuarioData.username}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "profilePhoto",
                        "username" to usuarioData.username
                    )
                ) ?: ""
            } else {
                usuarioData.fotoPerfilId ?: ""
            }

        // CREAR el usuario SOLO después de verificar el email
        val usuario = Usuario(
            _id = null,
            username = usuarioData.username,
            password = passwordEncoder.encode(usuarioData.password),
            roles = usuarioData.rol.toString(),
            nombre = usuarioData.nombre,
            apellidos = usuarioData.apellidos,
            descripcion = usuarioData.descripcion,
            email = usuarioData.email,
            intereses = usuarioData.intereses,
            fotoPerfilId = fotoPerfilId,
            direccion = usuarioData.direccion,
            fechaUnion = Date.from(Instant.now()),
            coordenadas = null,
            premium = false,
            privacidadActividades = "TODOS",
            privacidadComunidades = "TODOS",
            radarDistancia = "50.0"
        )

        // Insertar el usuario en la base de datos
        usuarioRepository.insert(usuario)

        // LIMPIAR datos temporales
        verificacionCodigos.remove(email)
        usuariosPendientesVerificacion.remove(email)

        // Retornar un DTO de usuario
        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            descripcion = usuario.descripcion,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            direccion = usuario.direccion,
            fotoPerfilId = fotoPerfilId,
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )
    }

    private fun aplicarModificacionUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): Map<String, String> {
        val usuario = aplicarModificacionUsuarioInterno(usuarioUpdateDTO)
        return mapOf(
            "message" to "Usuario modificado correctamente",
            "requiresVerification" to "false"
        )
    }

    private fun aplicarModificacionUsuarioInterno(usuarioUpdateDTO: UsuarioUpdateDTO): UsuarioDTO {

        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.currentUsername).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.currentUsername} no encontrado")
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
                usuarioUpdateDTO.fotoPerfilId
            } else {
                usuario.fotoPerfilId ?: ""
            }

        // Guardar el antiguo username
        val antiguoUsername = usuario.username

        // Actualizar la información del usuario
        usuario.apply {
            username = usuarioUpdateDTO.newUsername ?: antiguoUsername
            email = usuarioUpdateDTO.email ?: usuario.email
            nombre = usuarioUpdateDTO.nombre ?: usuario.nombre
            apellidos = usuarioUpdateDTO.apellido ?: usuario.apellidos
            descripcion = usuarioUpdateDTO.descripcion ?: usuario.descripcion
            intereses = usuarioUpdateDTO.intereses ?: usuario.intereses
            fotoPerfilId = nuevaFotoPerfilId
            direccion = usuarioUpdateDTO.direccion ?: usuario.direccion
        }

        val usuarioActualizado = usuarioRepository.save(usuario)

        // Si se ha cambiado el username, actualizar referencias en todas las colecciones
        if (antiguoUsername != usuario.username) {
            val nuevoUsername = usuario.username

            // 1. Actualizar ParticipantesActividad
            val participantesActividad = participantesActividadRepository.findByUsername(antiguoUsername)
            participantesActividad.forEach { participante ->
                participante.username = nuevoUsername
                participantesActividadRepository.save(participante)
            }

            // 2. Actualizar ParticipantesComunidad
            val participantesComunidad = participantesComunidadRepository.findByUsername(antiguoUsername)
            participantesComunidad.forEach { participante ->
                participante.username = nuevoUsername
                participantesComunidadRepository.save(participante)
            }

            // 3. Actualizar Mensajes
            val mensajes = mensajeRepository.findAll().filter { it.username == antiguoUsername }
            mensajes.forEach { mensaje ->
                val mensajeActualizado = Mensaje(
                    _id = mensaje._id,
                    comunidadUrl = mensaje.comunidadUrl,
                    username = nuevoUsername,
                    contenido = mensaje.contenido,
                    fechaEnvio = mensaje.fechaEnvio,
                    leido = mensaje.leido
                )
                mensajeRepository.save(mensajeActualizado)
            }

            // 4. Actualizar Notificaciones (usuarioDestino)
            val notificaciones = notificacionRepository.findByUsuarioDestinoOrderByFechaCreacionDesc(antiguoUsername)
            notificaciones.forEach { notificacion ->
                val notificacionActualizada = Notificacion(
                    _id = notificacion._id,
                    tipo = notificacion.tipo,
                    titulo = notificacion.titulo,
                    mensaje = notificacion.mensaje,
                    usuarioDestino = nuevoUsername,
                    entidadId = notificacion.entidadId,
                    entidadNombre = notificacion.entidadNombre,
                    fechaCreacion = notificacion.fechaCreacion,
                    leida = notificacion.leida
                )
                notificacionRepository.save(notificacionActualizada)
            }

            // 5. Actualizar Bloqueos (tanto bloqueador como bloqueado)
            val bloqueosBloqueador = bloqueoRepository.findAllByBloqueador(antiguoUsername)
            bloqueosBloqueador.forEach { bloqueo ->
                val bloqueoActualizado = Bloqueo(
                    _id = bloqueo._id,
                    bloqueador = nuevoUsername,
                    bloqueado = bloqueo.bloqueado,
                    fechaBloqueo = bloqueo.fechaBloqueo
                )
                bloqueoRepository.save(bloqueoActualizado)
            }

            val bloqueosBloqueado = bloqueoRepository.findAllByBloqueado(antiguoUsername)
            bloqueosBloqueado.forEach { bloqueo ->
                val bloqueoActualizado = Bloqueo(
                    _id = bloqueo._id,
                    bloqueador = bloqueo.bloqueador,
                    bloqueado = nuevoUsername,
                    fechaBloqueo = bloqueo.fechaBloqueo
                )
                bloqueoRepository.save(bloqueoActualizado)
            }

            // 6. Actualizar Denuncias (usuarioDenunciante)
            val denuncias = denunciaRepository.findAll().filter { it.usuarioDenunciante == antiguoUsername }
            denuncias.forEach { denuncia ->
                val denunciaActualizada = Denuncia(
                    _id = denuncia._id,
                    motivo = denuncia.motivo,
                    cuerpo = denuncia.cuerpo,
                    nombreItemDenunciado = denuncia.nombreItemDenunciado,
                    tipoItemDenunciado = denuncia.tipoItemDenunciado,
                    usuarioDenunciante = nuevoUsername,
                    fechaCreacion = denuncia.fechaCreacion,
                    solucionado = denuncia.solucionado
                )
                denunciaRepository.save(denunciaActualizada)
            }

            // 7. Actualizar denuncias donde el usuario es el item denunciado
            val denunciasComoItem = denunciaRepository.findAll().filter {
                it.tipoItemDenunciado == "usuario" && it.nombreItemDenunciado == antiguoUsername
            }
            denunciasComoItem.forEach { denuncia ->
                val denunciaActualizada = Denuncia(
                    _id = denuncia._id,
                    motivo = denuncia.motivo,
                    cuerpo = denuncia.cuerpo,
                    nombreItemDenunciado = nuevoUsername,
                    tipoItemDenunciado = denuncia.tipoItemDenunciado,
                    usuarioDenunciante = denuncia.usuarioDenunciante,
                    fechaCreacion = denuncia.fechaCreacion,
                    solucionado = denuncia.solucionado
                )
                denunciaRepository.save(denunciaActualizada)
            }

            // 8. Actualizar SolicitudesAmistad (remitente y destinatario)
            val solicitudesRemitente = solicitudesAmistadRepository.findAll().filter { it.remitente == antiguoUsername }
            solicitudesRemitente.forEach { solicitud ->
                val solicitudActualizada = SolicitudAmistad(
                    _id = solicitud._id,
                    remitente = nuevoUsername,
                    destinatario = solicitud.destinatario,
                    fechaEnviada = solicitud.fechaEnviada,
                    aceptada = solicitud.aceptada
                )
                solicitudesAmistadRepository.save(solicitudActualizada)
            }

            val solicitudesDestinatario = solicitudesAmistadRepository.findAll().filter { it.destinatario == antiguoUsername }
            solicitudesDestinatario.forEach { solicitud ->
                val solicitudActualizada = SolicitudAmistad(
                    _id = solicitud._id,
                    remitente = solicitud.remitente,
                    destinatario = nuevoUsername,
                    fechaEnviada = solicitud.fechaEnviada,
                    aceptada = solicitud.aceptada
                )
                solicitudesAmistadRepository.save(solicitudActualizada)
            }

            // 9. Actualizar Actividades (creador)
            val actividades = actividadRepository.findAll().filter { it.creador == antiguoUsername }
            actividades.forEach { actividad ->
                actividad.creador = nuevoUsername
                actividadRepository.save(actividad)
            }

            // 10. Actualizar Comunidades (creador y administradores)
            val comunidades = comunidadRepository.findByCreador(antiguoUsername)
            comunidades.forEach { comunidad ->
                val nuevosAdministradores = comunidad.administradores?.map { admin ->
                    if (admin == antiguoUsername) nuevoUsername else admin
                }

                comunidad.creador = nuevoUsername
                comunidad.administradores = nuevosAdministradores
                comunidadRepository.save(comunidad)
            }

            // También buscar comunidades donde sea administrador pero no creador
            val todasComunidades = comunidadRepository.findAll()
            todasComunidades.forEach { comunidad ->
                if (comunidad.creador != antiguoUsername && comunidad.administradores?.contains(antiguoUsername) == true) {
                    val nuevosAdministradores = comunidad.administradores?.map { admin ->
                        if (admin == antiguoUsername) nuevoUsername else admin
                    }

                    comunidad.administradores = nuevosAdministradores
                    comunidadRepository.save(comunidad)
                }
            }

            println("Username actualizado de '$antiguoUsername' a '$nuevoUsername' en todas las colecciones")
        }

        // Retornar el DTO actualizado
        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            fotoPerfilId = nuevaFotoPerfilId,
            direccion = usuario.direccion,
            descripcion = usuario.descripcion,
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )
    }

    // Función para usar cuando no hay cambio de email (compatibilidad hacia atrás)
    fun modificarUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): UsuarioDTO {
        return aplicarModificacionUsuarioInterno(usuarioUpdateDTO)
    }

    fun modificarCoordenadasUsuario(coordenadas: Coordenadas?, username: String) {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow { NotFoundException("Usuario $username no existe") }
        try {
            // Actualizamos las coordenadas del usuario
            usuario.coordenadas = Coordenadas(
                coordenadas!!.latitud,coordenadas.longitud
            )

            // Guardamos el usuario actualizado
            usuarioRepository.save(usuario)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Formato de coordenadas inválido: $coordenadas")
        }
    }

    fun verificarGmail(gmail: String): Boolean {
        // Esta función ahora solo la usamos para reenviar códigos
        return enviarCodigoVerificacion(gmail)
    }

    // RESTO DE MÉTODOS IGUALES - NO TOCAR
    fun eliminarUsuario(username: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        // Verificar si el usuario es creador de alguna comunidad
        val comunidadesCreadas = comunidadRepository.findAll().filter { it.creador == username }

        if (comunidadesCreadas.isNotEmpty()) {
            // El usuario es creador de al menos una comunidad
            val nombresComunidades = comunidadesCreadas.joinToString(", ") { it.nombre }
            throw BadRequestException("No se puede eliminar la cuenta mientras seas el creador de las siguientes comunidades: $nombresComunidades. Por favor, elimina o cede la propiedad de estas comunidades primero.")
        }

        // Si no es creador de ninguna comunidad, proceder con la eliminación
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
            fotoPerfilId = usuario.fotoPerfilId ?: "",
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )

        usuarioRepository.delete(usuario)
        return userDTO
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
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )
    }

    fun actualizarPremium(username: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        usuario.premium = true
        val usuarioActualizado = usuarioRepository.save(usuario)

        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            fotoPerfilId = usuario.fotoPerfilId ?: "",
            direccion = usuario.direccion,
            descripcion = usuario.descripcion,
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )
    }

    fun verUsuariosPorComunidad(comunidad: String, usuarioActual: String): List<UsuarioDTO> {
        // Verificar que la comunidad existe
        comunidadRepository.findComunidadByUrl(comunidad).orElseThrow {
            throw NotFoundException("Comunidad $comunidad no encontrada")
        }

        // Verificar que el usuario actual existe
        usuarioRepository.findFirstByUsername(usuarioActual).orElseThrow {
            throw NotFoundException("Usuario $usuarioActual no encontrado")
        }

        // Obtener usuarios con los que hay bloqueo
        val usuariosBloqueados = bloqueoRepository.findAllByBloqueador(usuarioActual)
            .map { it.bloqueado }
            .toSet()

        val usuariosQueBloquearon = bloqueoRepository.findAllByBloqueado(usuarioActual)
            .map { it.bloqueador }
            .toSet()

        val usuariosConBloqueo = usuariosBloqueados + usuariosQueBloquearon

        // Obtener la lista de participantes de la comunidad
        val participantes = participantesComunidadRepository.findParticipantesByComunidad(comunidad)

        // Crear una lista para almacenar los DTOs de usuario
        val listaUsuarios = mutableListOf<UsuarioDTO>()

        // Para cada participante, buscar su información completa y crear un DTO
        participantes.forEach { participante ->
            // Saltarse los usuarios con los que hay bloqueo
            if (!usuariosConBloqueo.contains(participante.username)) {
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
                        descripcion = usuario.descripcion,
                        premium = usuario.premium,
                        privacidadActividades = usuario.privacidadActividades,
                        privacidadComunidades = usuario.privacidadComunidades,
                        radarDistancia = usuario.radarDistancia,
                    )
                )
            }
        }

        return listaUsuarios
    }

    fun verUsuariosPorActividad(actividadId: String, usuarioActual: String): List<UsuarioDTO> {
        // Verificar que el usuario actual existe
        usuarioRepository.findFirstByUsername(usuarioActual).orElseThrow {
            throw NotFoundException("Usuario $usuarioActual no encontrado")
        }

        // Obtener usuarios con los que hay bloqueo
        val usuariosBloqueados = bloqueoRepository.findAllByBloqueador(usuarioActual)
            .map { it.bloqueado }
            .toSet()

        val usuariosQueBloquearon = bloqueoRepository.findAllByBloqueado(usuarioActual)
            .map { it.bloqueador }
            .toSet()

        val usuariosConBloqueo = usuariosBloqueados + usuariosQueBloquearon

        // Obtener la lista de participantes de la actividad
        val participantes = participantesActividadRepository.findByidActividad(actividadId)

        if (participantes.isEmpty()) {
            throw NotFoundException("No se encontraron participantes para la actividad con id $actividadId")
        }

        // Crear una lista para almacenar los DTOs de usuario
        val listaUsuarios = mutableListOf<UsuarioDTO>()

        // Para cada participante, buscar su información completa y crear un DTO
        participantes.forEach { participante ->
            // Saltarse los usuarios con los que hay bloqueo
            if (!usuariosConBloqueo.contains(participante.username)) {
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
                        descripcion = usuario.descripcion,
                        premium = usuario.premium,
                        privacidadActividades = usuario.privacidadActividades,
                        privacidadComunidades = usuario.privacidadComunidades,
                        radarDistancia = usuario.radarDistancia,
                    )
                )
            }
        }

        // Devolver la lista de DTOs
        return listaUsuarios
    }

    fun verTodosLosUsuarios(username: String): List<UsuarioDTO> {
        // Buscar el usuario actual para verificar que existe
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        // Obtener usuarios que el usuario actual ha bloqueado
        val usuariosBloqueados = bloqueoRepository.findAllByBloqueador(username)
            .map { it.bloqueado }
            .toSet()

        // Obtener usuarios que han bloqueado al usuario actual
        val usuariosQueBloquearon = bloqueoRepository.findAllByBloqueado(username)
            .map { it.bloqueador }
            .toSet()

        // Combinar ambos conjuntos para tener todos los usuarios con los que hay bloqueo
        val usuariosConBloqueo = usuariosBloqueados + usuariosQueBloquearon

        return usuarioRepository.findAll()
            .filter { usuario ->
                // Excluir al usuario actual
                usuario.username != username &&
                        // Excluir a los usuarios con los que hay bloqueo
                        !usuariosConBloqueo.contains(usuario.username)
            }
            .sortedBy { usuario ->
                // Ordenar por nombre, luego por apellido para casos con mismo nombre
                "${usuario.nombre.lowercase()}${usuario.apellidos.lowercase()}"
            }
            .map { usuario ->
                UsuarioDTO(
                    username = usuario.username,
                    email = usuario.email,
                    intereses = usuario.intereses,
                    nombre = usuario.nombre,
                    apellido = usuario.apellidos,
                    fotoPerfilId = usuario.fotoPerfilId,
                    direccion = usuario.direccion,
                    descripcion = usuario.descripcion,
                    premium = usuario.premium,
                    privacidadActividades = usuario.privacidadActividades,
                    privacidadComunidades = usuario.privacidadComunidades,
                    radarDistancia = usuario.radarDistancia,
                )
            }
    }

    fun bloquearUsuario(bloqueador: String, bloqueado: String): BloqueoDTO {
        // Verificar que ambos usuarios existen
        val usuarioBloqueador = usuarioRepository.findFirstByUsername(bloqueador).orElseThrow {
            throw NotFoundException("Usuario bloqueador $bloqueador no encontrado")
        }

        val usuarioBloqueado = usuarioRepository.findFirstByUsername(bloqueado).orElseThrow {
            throw NotFoundException("Usuario a bloquear $bloqueado no encontrado")
        }

        // Verificar que no se está intentando bloquear a uno mismo
        if (bloqueador == bloqueado) {
            throw BadRequestException("No puedes bloquearte a ti mismo")
        }

        // Verificar si ya existe un bloqueo
        if (bloqueoRepository.existsByBloqueadorAndBloqueado(bloqueador, bloqueado)) {
            throw BadRequestException("Ya has bloqueado a este usuario")
        }

        // Crear el nuevo bloqueo
        val nuevoBloqueo = Bloqueo(
            _id = UUID.randomUUID().toString(),
            bloqueador = bloqueador,
            bloqueado = bloqueado,
            fechaBloqueo = Date()
        )

        // Guardar el bloqueo en la base de datos
        val bloqueoGuardado = bloqueoRepository.save(nuevoBloqueo)

        // Eliminar cualquier solicitud de amistad pendiente entre estos usuarios
        eliminarSolicitudesAmistad(bloqueador, bloqueado)

        // Eliminar amistad existente si la hay
        eliminarAmistad(bloqueador, bloqueado)

        // Retornar DTO
        return BloqueoDTO(
            _id = bloqueoGuardado._id,
            bloqueador = bloqueoGuardado.bloqueador,
            bloqueado = bloqueoGuardado.bloqueado,
            fechaBloqueo = bloqueoGuardado.fechaBloqueo
        )
    }

    fun desbloquearUsuario(bloqueador: String, bloqueado: String): Boolean {
        // Buscar el bloqueo existente
        val bloqueo = bloqueoRepository.findByBloqueadorAndBloqueado(bloqueador, bloqueado).orElseThrow {
            throw NotFoundException("No existe un bloqueo de $bloqueador hacia $bloqueado")
        }

        // Eliminar el bloqueo
        bloqueoRepository.delete(bloqueo)

        return true
    }

    fun existeBloqueo(usuario1: String, usuario2: String): Boolean {
        // Comprobar si existe un bloqueo en cualquier dirección
        return bloqueoRepository.existsByBloqueadorAndBloqueado(usuario1, usuario2) ||
                bloqueoRepository.existsByBloqueadorAndBloqueado(usuario2, usuario1)
    }

    fun verUsuariosBloqueados(username: String): List<UsuarioDTO> {
        // Verificar que el usuario existe
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        // Obtener todos los bloqueos realizados por el usuario
        val bloqueos = bloqueoRepository.findAllByBloqueador(username)

        // Crear una lista para almacenar los DTOs de usuario
        val listaUsuariosBloqueados = mutableListOf<UsuarioDTO>()

        // Para cada bloqueo, buscar la información del usuario bloqueado
        bloqueos.forEach { bloqueo ->
            val usuarioBloqueado = usuarioRepository.findFirstByUsername(bloqueo.bloqueado).orElseThrow {
                throw NotFoundException("Usuario bloqueado ${bloqueo.bloqueado} no encontrado")
            }

            // Crear y añadir el DTO a la lista
            listaUsuariosBloqueados.add(
                UsuarioDTO(
                    username = usuarioBloqueado.username,
                    email = usuarioBloqueado.email,
                    intereses = usuarioBloqueado.intereses,
                    nombre = usuarioBloqueado.nombre,
                    apellido = usuarioBloqueado.apellidos,
                    fotoPerfilId = usuarioBloqueado.fotoPerfilId,
                    direccion = usuarioBloqueado.direccion,
                    descripcion = usuarioBloqueado.descripcion,
                    premium = usuarioBloqueado.premium,
                    privacidadActividades = usuarioBloqueado.privacidadActividades,
                    privacidadComunidades = usuarioBloqueado.privacidadComunidades,
                    radarDistancia = usuarioBloqueado.radarDistancia,
                )
            )
        }

        // Devolver la lista de DTOs de usuarios bloqueados
        return listaUsuariosBloqueados
    }

    fun eliminarSolicitudesAmistad(usuario1: String, usuario2: String) {
        // Buscar solicitudes en ambas direcciones
        val solicitudes = solicitudesAmistadRepository.findByRemitenteAndDestinatario(usuario1, usuario2) +
                solicitudesAmistadRepository.findByRemitenteAndDestinatario(usuario2, usuario1)

        // Eliminar todas las solicitudes encontradas
        solicitudesAmistadRepository.deleteAll(solicitudes)
    }

    fun eliminarAmistad(usuario1: String, usuario2: String) {
        // Buscar relaciones de amistad aceptadas en ambas direcciones
        val amistad1 = solicitudesAmistadRepository.findByRemitenteAndDestinatarioAndAceptada(usuario1, usuario2, true)
        val amistad2 = solicitudesAmistadRepository.findByRemitenteAndDestinatarioAndAceptada(usuario2, usuario1, true)

        // Eliminar la amistad si existe
        if (amistad1 != null) {
            solicitudesAmistadRepository.delete(amistad1)
        }

        if (amistad2 != null) {
            solicitudesAmistadRepository.delete(amistad2)
        }
    }

    fun verSolicitudesAmistad(username: String): List<SolicitudAmistadDTO> {
        // Verificar que el usuario existe
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        // Obtener todas las solicitudes de amistad pendientes (no aceptadas) donde el usuario es el destinatario
        val solicitudesPendientes = solicitudesAmistadRepository.findByDestinatarioAndAceptada(username, false)

        // Convertir a DTO
        return solicitudesPendientes.map { solicitud ->
            SolicitudAmistadDTO(
                _id = solicitud._id,
                remitente = solicitud.remitente,
                destinatario = solicitud.destinatario,
                fechaEnviada = solicitud.fechaEnviada,
                aceptada = solicitud.aceptada
            )
        }
    }

    fun verAmigos(username: String): List<UsuarioDTO> {
        // Verificar que el usuario existe
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        // Obtener usuarios con los que hay bloqueo
        val usuariosBloqueados = bloqueoRepository.findAllByBloqueador(username)
            .map { it.bloqueado }
            .toSet()

        val usuariosQueBloquearon = bloqueoRepository.findAllByBloqueado(username)
            .map { it.bloqueador }
            .toSet()

        val usuariosConBloqueo = usuariosBloqueados + usuariosQueBloquearon

        // Buscar todas las solicitudes aceptadas donde el usuario es remitente o destinatario
        val amigosComoRemitente = solicitudesAmistadRepository.findByRemitenteAndAceptada(username, true)
        val amigosComoDestinatario = solicitudesAmistadRepository.findByDestinatarioAndAceptada(username, true)

        // Crear una lista para almacenar los usernames de los amigos
        val usernamesAmigos = mutableSetOf<String>()

        // Añadir usernames de amigos donde el usuario actual es el remitente
        amigosComoRemitente.forEach { solicitud ->
            // No añadir si hay bloqueo
            if (!usuariosConBloqueo.contains(solicitud.destinatario)) {
                usernamesAmigos.add(solicitud.destinatario)
            }
        }

        // Añadir usernames de amigos donde el usuario actual es el destinatario
        amigosComoDestinatario.forEach { solicitud ->
            // No añadir si hay bloqueo
            if (!usuariosConBloqueo.contains(solicitud.remitente)) {
                usernamesAmigos.add(solicitud.remitente)
            }
        }

        // Crear una lista para almacenar los DTOs de usuario
        val listaAmigos = mutableListOf<UsuarioDTO>()

        // Para cada username de amigo, buscar su información completa y crear un DTO
        usernamesAmigos.forEach { amigoUsername ->
            val amigo = usuarioRepository.findFirstByUsername(amigoUsername).orElseThrow {
                throw NotFoundException("Usuario amigo $amigoUsername no encontrado")
            }

            // Crear y añadir el DTO a la lista
            listaAmigos.add(
                UsuarioDTO(
                    username = amigo.username,
                    email = amigo.email,
                    intereses = amigo.intereses,
                    nombre = amigo.nombre,
                    apellido = amigo.apellidos,
                    fotoPerfilId = amigo.fotoPerfilId,
                    direccion = amigo.direccion,
                    descripcion = amigo.descripcion,
                    premium = amigo.premium,
                    privacidadActividades = amigo.privacidadActividades,
                    privacidadComunidades = amigo.privacidadComunidades,
                    radarDistancia = amigo.radarDistancia,
                )
            )
        }

        // Devolver la lista de DTOs de amigos
        return listaAmigos
    }

    fun enviarSolicitudAmistad(solicitudAmistadDTO: SolicitudAmistadDTO): SolicitudAmistadDTO {
        // Verificar que ambos usuarios existen
        val remitente = usuarioRepository.findFirstByUsername(solicitudAmistadDTO.remitente).orElseThrow {
            throw NotFoundException("Usuario remitente ${solicitudAmistadDTO.remitente} no encontrado")
        }

        val destinatario = usuarioRepository.findFirstByUsername(solicitudAmistadDTO.destinatario).orElseThrow {
            throw NotFoundException("Usuario destinatario ${solicitudAmistadDTO.destinatario} no encontrado")
        }

        // Verificar que no existe un bloqueo entre los usuarios
        if (existeBloqueo(solicitudAmistadDTO.remitente, solicitudAmistadDTO.destinatario)) {
            throw BadRequestException("No puedes enviar solicitudes de amistad a usuarios bloqueados o que te han bloqueado")
        }

        // Verificar que no exista ya una solicitud pendiente entre estos usuarios
        val solicitudExistente = solicitudesAmistadRepository.findByRemitenteAndDestinatario(
            solicitudAmistadDTO.remitente,
            solicitudAmistadDTO.destinatario
        )

        if (solicitudExistente.isNotEmpty()) {
            throw BadRequestException("Ya existe una solicitud de amistad entre estos usuarios")
        }

        // Crear la nueva solicitud de amistad con estado no aceptado
        val nuevaSolicitud = SolicitudAmistad(
            _id = UUID.randomUUID().toString(),
            remitente = solicitudAmistadDTO.remitente,
            destinatario = solicitudAmistadDTO.destinatario,
            fechaEnviada = Date(),
            aceptada = false
        )

        // Guardar la solicitud en la base de datos
        solicitudesAmistadRepository.save(nuevaSolicitud)

        // Retornar DTO
        return SolicitudAmistadDTO(
            _id = nuevaSolicitud._id,
            remitente = nuevaSolicitud.remitente,
            destinatario = nuevaSolicitud.destinatario,
            fechaEnviada = nuevaSolicitud.fechaEnviada,
            aceptada = nuevaSolicitud.aceptada
        )
    }

    fun aceptarSolicitud(id: String): Boolean {
        // Buscar la solicitud por ID
        val solicitud = solicitudesAmistadRepository.findById(id).orElseThrow {
            throw NotFoundException("Solicitud de amistad con ID $id no encontrada")
        }

        // Verificar que la solicitud no haya sido aceptada ya
        if (solicitud.aceptada) {
            throw BadRequestException("Esta solicitud ya ha sido aceptada")
        }

        // Crear una nueva solicitud con los mismos datos pero con aceptada = true
        val solicitudAceptada = SolicitudAmistad(
            _id = solicitud._id,
            remitente = solicitud.remitente,
            destinatario = solicitud.destinatario,
            fechaEnviada = solicitud.fechaEnviada,
            aceptada = true
        )

        // Guardar la solicitud actualizada
        solicitudesAmistadRepository.save(solicitudAceptada)

        return true
    }
    fun verComunidadPorUsuario(username: String, usuarioSolicitante: String): List<ComunidadDTO> {
        // Verificar que el usuario objetivo existe
        val usuarioObjetivo = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { NotFoundException("Usuario $username no encontrado") }

        // Verificar que el usuario solicitante existe
        usuarioRepository.findFirstByUsername(usuarioSolicitante)
            .orElseThrow { NotFoundException("Usuario solicitante $usuarioSolicitante no encontrado") }

        // Si es el propio usuario, mostrar todas sus comunidades
        if (username == usuarioSolicitante) {
            val participaciones = participantesComunidadRepository.findByUsername(username)
            if (participaciones.isEmpty()) {
                return emptyList()
            }

            return participaciones.mapNotNull { participante ->
                val comunidadOpt = comunidadRepository.findComunidadByUrl(participante.comunidad)
                if (comunidadOpt.isPresent) {
                    val comunidad = comunidadOpt.get()
                    ComunidadDTO(
                        url = comunidad.url,
                        nombre = comunidad.nombre,
                        descripcion = comunidad.descripcion,
                        intereses = comunidad.intereses,
                        fotoPerfilId = comunidad.fotoPerfilId,
                        fotoCarruselIds = comunidad.fotoCarruselIds,
                        creador = comunidad.creador,
                        administradores = comunidad.administradores,
                        fechaCreacion = comunidad.fechaCreacion,
                        privada = comunidad.privada,
                        coordenadas = comunidad.coordenadas,
                        codigoUnion = comunidad.codigoUnion
                    )
                } else null
            }
        }

        // Si no es el propio usuario, verificar configuración de privacidad
        when (usuarioObjetivo.privacidadComunidades.uppercase()) {
            "NADIE" -> return emptyList()
            "AMIGOS" -> {
                // Verificar si son amigos
                val sonAmigos = verificarAmistad(username, usuarioSolicitante)
                if (!sonAmigos) {
                    return emptyList()
                }
            }
            "TODOS" -> {
                // Permitir ver las comunidades
            }
        }

        // Si llega aquí, puede ver las comunidades
        val participaciones = participantesComunidadRepository.findByUsername(username)
        if (participaciones.isEmpty()) {
            return emptyList()
        }

        return participaciones.mapNotNull { participante ->
            val comunidadOpt = comunidadRepository.findComunidadByUrl(participante.comunidad)
            if (comunidadOpt.isPresent) {
                val comunidad = comunidadOpt.get()
                ComunidadDTO(
                    url = comunidad.url,
                    nombre = comunidad.nombre,
                    descripcion = comunidad.descripcion,
                    intereses = comunidad.intereses,
                    fotoPerfilId = comunidad.fotoPerfilId,
                    fotoCarruselIds = comunidad.fotoCarruselIds,
                    creador = comunidad.creador,
                    administradores = comunidad.administradores,
                    fechaCreacion = comunidad.fechaCreacion,
                    privada = comunidad.privada,
                    coordenadas = comunidad.coordenadas,
                    codigoUnion = comunidad.codigoUnion
                )
            } else null
        }
    }

    fun verificarAmistad(usuario1: String, usuario2: String): Boolean {
        val amistad1 = solicitudesAmistadRepository.findByRemitenteAndDestinatarioAndAceptada(usuario1, usuario2, true)
        val amistad2 = solicitudesAmistadRepository.findByRemitenteAndDestinatarioAndAceptada(usuario2, usuario1, true)
        return amistad1 != null || amistad2 != null
    }

    fun verActividadesPorUsername(username: String, usuarioSolicitante: String): List<ActividadDTO> {
        // Verificar que el usuario objetivo existe
        val usuarioObjetivo = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { NotFoundException("Usuario $username no encontrado") }

        // Verificar que el usuario solicitante existe
        usuarioRepository.findFirstByUsername(usuarioSolicitante)
            .orElseThrow { NotFoundException("Usuario solicitante $usuarioSolicitante no encontrado") }

        // Si es el propio usuario, mostrar todas sus actividades
        if (username == usuarioSolicitante) {
            val participantes = participantesActividadRepository.findByUsername(username)
            val actividadesIds = participantes.map { it.idActividad }
            val actividadesEncontradas = mutableListOf<Actividad>()

            actividadesIds.forEach { idActividad ->
                val actividad = actividadRepository.findActividadBy_id(idActividad)
                actividad.ifPresent { actividadesEncontradas.add(it) }
            }

            return actividadesEncontradas.map { actividad ->
                ActividadDTO(
                    nombre = actividad.nombre,
                    descripcion = actividad.descripcion,
                    privada = actividad.privada,
                    creador = actividad.creador,
                    fotosCarruselIds = actividad.fotosCarruselIds,
                    fechaFinalizacion = actividad.fechaFinalizacion,
                    fechaInicio = actividad.fechaInicio,
                    _id = actividad._id,
                    coordenadas = actividad.coordenadas,
                    lugar = actividad.lugar
                )
            }
        }

        // Si no es el propio usuario, verificar configuración de privacidad
        when (usuarioObjetivo.privacidadActividades.uppercase()) {
            "NADIE" -> return emptyList()
            "AMIGOS" -> {
                // Verificar si son amigos
                val sonAmigos = verificarAmistad(username, usuarioSolicitante)
                if (!sonAmigos) {
                    return emptyList()
                }
            }
            "TODOS" -> {
                // Permitir ver las actividades
            }
        }

        // Si llega aquí, puede ver las actividades
        val participantes = participantesActividadRepository.findByUsername(username)
        val actividadesIds = participantes.map { it.idActividad }
        val actividadesEncontradas = mutableListOf<Actividad>()

        actividadesIds.forEach { idActividad ->
            val actividad = actividadRepository.findActividadBy_id(idActividad)
            actividad.ifPresent { actividadesEncontradas.add(it) }
        }

        return actividadesEncontradas.map { actividad ->
            ActividadDTO(
                nombre = actividad.nombre,
                descripcion = actividad.descripcion,
                privada = actividad.privada,
                creador = actividad.creador,
                fotosCarruselIds = actividad.fotosCarruselIds,
                fechaFinalizacion = actividad.fechaFinalizacion,
                fechaInicio = actividad.fechaInicio,
                _id = actividad._id,
                coordenadas = actividad.coordenadas,
                lugar = actividad.lugar
            )
        }
    }
    fun cambiarRadarDistancia(username: String, radar: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        // Validar que el radar sea un número válido entre 10 y 100
        val radarFloat = try {
            radar.toFloat()
        } catch (e: NumberFormatException) {
            throw BadRequestException("El valor del radar debe ser un número válido")
        }

        if (radarFloat < 10f || radarFloat > 100f) {
            throw BadRequestException("El radar debe estar entre 10 y 100 km")
        }

        usuario.radarDistancia = radar
        val usuariodto=usuarioRepository.save(usuario)

        // Devolver lista de usuarios cercanos basada en el nuevo radar (simulado)
        // En una implementación real, aquí buscarías usuarios dentro del radio especificado
        return UsuarioDTO(
            username = usuariodto.username,
            email = usuariodto.email,
            intereses = usuariodto.intereses,
            nombre = usuariodto.nombre,
            apellido = usuariodto.apellidos,
            fotoPerfilId = usuariodto.fotoPerfilId ?: "",
            direccion = usuariodto.direccion,
            descripcion = usuariodto.descripcion,
            premium = usuariodto.premium,
            privacidadActividades = usuariodto.privacidadActividades,
            privacidadComunidades = usuariodto.privacidadComunidades,
            radarDistancia = usuariodto.radarDistancia,

            )
    }
    fun cambiarPrivacidadComunidad(username: String, privacidad: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        // Validar que la privacidad sea uno de los valores válidos
        val privacidadValida = privacidad.uppercase()
        if (privacidadValida !in listOf("TODOS", "AMIGOS", "NADIE")) {
            throw BadRequestException("Valor de privacidad inválido. Debe ser: TODOS, AMIGOS o NADIE")
        }

        usuario.privacidadComunidades = privacidadValida
        val usuarioActualizado = usuarioRepository.save(usuario)

        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            fotoPerfilId = usuario.fotoPerfilId ?: "",
            direccion = usuario.direccion,
            descripcion = usuario.descripcion,
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )
    }

    fun cambiarPrivacidadActividad(username: String, privacidad: String): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario $username no encontrado")
        }

        // Validar que la privacidad sea uno de los valores válidos
        val privacidadValida = privacidad.uppercase()
        if (privacidadValida !in listOf("TODOS", "AMIGOS", "NADIE")) {
            throw BadRequestException("Valor de privacidad inválido. Debe ser: TODOS, AMIGOS o NADIE")
        }

        usuario.privacidadActividades = privacidadValida
        val usuarioActualizado = usuarioRepository.save(usuario)

        return UsuarioDTO(
            username = usuario.username,
            email = usuario.email,
            intereses = usuario.intereses,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            fotoPerfilId = usuario.fotoPerfilId ?: "",
            direccion = usuario.direccion,
            descripcion = usuario.descripcion,
            premium = usuario.premium,
            privacidadActividades = usuario.privacidadActividades,
            privacidadComunidades = usuario.privacidadComunidades,
            radarDistancia = usuario.radarDistancia,
        )
    }

    fun verRadarDistancia(username:String):String{
        return usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Este usuario no existe")
        }.radarDistancia
    }

    fun verPrivacidadActividad(username:String):String{        return usuarioRepository.findFirstByUsername(username).orElseThrow {
        NotFoundException("Este usuario no existe")
    }.privacidadActividades
    }

    fun verPrivacidadComunidad(username:String):String{
        return usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Este usuario no existe")
        }.privacidadComunidades
    }
}
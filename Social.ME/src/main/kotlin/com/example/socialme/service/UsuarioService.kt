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
import kotlin.math.log

@Service
class UsuarioService : UserDetailsService {

    @Autowired
    private lateinit var externalAPIService: ExternalAPIService

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
    private lateinit var participantesActividadRepository: ParticipantesActividadRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var gridFSService: GridFSService

    // Mapa para almacenar temporalmente los códigos de verificación CON TIMESTAMP
    private val verificacionCodigos = mutableMapOf<String, Pair<String, Long>>()

    // Mapa para almacenar temporalmente los datos de usuarios pendientes de verificación
    private val usuariosPendientesVerificacion = mutableMapOf<String, UsuarioRegisterDTO>()

    // Mapa para almacenar temporalmente los datos de modificaciones pendientes de verificación
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

    private fun validarIntereses(intereses: List<String>) {
        intereses.forEach { interes ->
            val interesLimpio = interes.trim()
            if (interesLimpio.length > 25) {
                throw BadRequestException("Los intereses no pueden exceder los 25 caracteres: '$interesLimpio' tus intereses son de ${interesLimpio.length}")
            }
            if (interesLimpio.contains(" ")) {
                throw BadRequestException("Los intereses no pueden contener espacios: '$interesLimpio'")
            }
            if (interesLimpio.contains(",")) {
                throw BadRequestException("Los intereses no pueden contener comas: '$interesLimpio'")
            }
        }
    }
    fun iniciarRegistroUsuario(usuarioInsertadoDTO: UsuarioRegisterDTO): Map<String, String> {
        ContentValidator.validarContenidoInapropiado(
            usuarioInsertadoDTO.username,
            usuarioInsertadoDTO.nombre,
            usuarioInsertadoDTO.apellidos,
            usuarioInsertadoDTO.descripcion
        )

        validarIntereses(usuarioInsertadoDTO.intereses)

        if (usuarioInsertadoDTO.descripcion.length > 600) {
            throw BadRequestException("La descripcion de un usuario no puede ser superior a 600 caracteres")
        }

        if (usuarioInsertadoDTO.nombre.length > 30) {
            throw BadRequestException("El nombre de un usuario no puede ser superior a 30 caracteres")
        }

        if (usuarioInsertadoDTO.apellidos.length > 60) {
            throw BadRequestException("Los apellidos de un usuario no pueden ser superiores a 60 caracteres")
        }

        if (usuarioInsertadoDTO.username.length > 30) {
            throw BadRequestException("El username de un usuario no puede ser superior a 30 caracteres")
        }

        // Validar provincia
        if (!externalAPIService.verificarProvinciaExiste(usuarioInsertadoDTO.direccion?.provincia ?: "")) {
            throw BadRequestException("La provincia '${usuarioInsertadoDTO.direccion?.provincia}' no es válida")
        }

        // Validar municipio
        if (!externalAPIService.verificarMunicipioExiste(usuarioInsertadoDTO.direccion?.municipio ?: "",
                usuarioInsertadoDTO.direccion?.provincia ?: ""
            )) {
            throw BadRequestException("El municipio '${usuarioInsertadoDTO.direccion?.municipio}' no existe en la provincia '${usuarioInsertadoDTO.direccion?.provincia}'")
        }

        if (usuarioRepository.existsByUsername(usuarioInsertadoDTO.username)) {
            throw BadRequestException("El nombre de usuario ${usuarioInsertadoDTO.username} ya está en uso")
        }

        if (usuarioRepository.existsByEmail(usuarioInsertadoDTO.email)) {
            throw BadRequestException("El email ${usuarioInsertadoDTO.email} ya está registrado")
        }

        if (!enviarCodigoVerificacion(usuarioInsertadoDTO.email)) {
            throw BadRequestException("No se pudo enviar el código de verificación al correo ${usuarioInsertadoDTO.email}")
        }

        usuarioInsertadoDTO.username = normalizarTexto(usuarioInsertadoDTO.username.toLowerCase())

        usuariosPendientesVerificacion[usuarioInsertadoDTO.email] = usuarioInsertadoDTO

        return mapOf(
            "message" to "Código de verificación enviado al correo ${usuarioInsertadoDTO.email}",
            "email" to usuarioInsertadoDTO.email
        )
    }

    fun iniciarModificacionUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): Map<String, String> {
        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.currentUsername).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.currentUsername} no encontrado")
        }

        if (usuarioUpdateDTO.descripcion.length > 600) {
            throw BadRequestException("La descripcion de un usuario no puede ser superior a 600 caracteres")
        }

        if (usuarioUpdateDTO.nombre.length > 30) {
            throw BadRequestException("El nombre de un usuario no puede ser superior a 30 caracteres")
        }

        if (usuarioUpdateDTO.apellido.length > 60) {
            throw BadRequestException("Los apellidos de un usuario no pueden ser superiores a 60 caracteres")
        }

        if ((usuarioUpdateDTO.newUsername?.length ?: usuarioUpdateDTO.currentUsername.length) > 30) {
            throw BadRequestException("El username de un usuario no puede ser superior a 30 caracteres")
        }


        ContentValidator.validarContenidoInapropiado(
            usuarioUpdateDTO.newUsername ?: "",
            usuarioUpdateDTO.nombre ?: "",
            usuarioUpdateDTO.apellido ?: "",
            usuarioUpdateDTO.descripcion ?: ""
        )

        validarIntereses(usuarioUpdateDTO.intereses)

        // Validar provincia si se está modificando
        if (!externalAPIService.verificarProvinciaExiste(usuarioUpdateDTO.direccion.provincia)) {
            throw BadRequestException("La provincia '${usuarioUpdateDTO.direccion.provincia}' no es válida")
        }

        // Validar municipio si se está modificando
        val provinciaAValidar = usuarioUpdateDTO.direccion.provincia ?: usuario.direccion?.provincia
        if (provinciaAValidar != null) {
            if (!externalAPIService.verificarMunicipioExiste(usuarioUpdateDTO.direccion.municipio, provinciaAValidar)) {
                throw BadRequestException("El municipio '${usuarioUpdateDTO.direccion.municipio}' no existe en la provincia '$provinciaAValidar'")
            }
        } else {
            throw BadRequestException("Se debe proporcionar una provincia válida para validar el municipio")
        }

        if (usuarioUpdateDTO.newUsername != null && usuarioUpdateDTO.newUsername != usuarioUpdateDTO.currentUsername) {
            if (usuarioRepository.existsByUsername(usuarioUpdateDTO.newUsername.toString())) {
                throw BadRequestException("El nombre de usuario ${usuarioUpdateDTO.newUsername} ya está en uso, prueba con otro nombre")
            }
        }

        val emailCambiado = usuarioUpdateDTO.email != null && usuarioUpdateDTO.email != usuario.email

        println("Email actual: '${usuario.email}'")
        println("Email nuevo: '${usuarioUpdateDTO.email}'")
        println("Email cambió: $emailCambiado")

        if (emailCambiado) {
            val nuevoEmail = usuarioUpdateDTO.email!!

            if (usuarioRepository.existsByEmail(nuevoEmail)) {
                throw BadRequestException("El email $nuevoEmail ya está registrado por otro usuario")
            }

            verificacionCodigos.remove(nuevoEmail)
            modificacionesPendientesVerificacion.remove(nuevoEmail)
            limpiarCodigosExpirados()

            if (!enviarCodigoVerificacion(nuevoEmail)) {
                throw BadRequestException("No se pudo enviar el código de verificación al correo $nuevoEmail")
            }

            usuarioUpdateDTO.newUsername = normalizarTexto(
                usuarioUpdateDTO.newUsername?.toLowerCase() ?: usuarioUpdateDTO.currentUsername.toLowerCase()
            )

            modificacionesPendientesVerificacion[nuevoEmail] = usuarioUpdateDTO

            return mapOf(
                "message" to "Código de verificación enviado al correo $nuevoEmail",
                "email" to nuevoEmail,
                "requiresVerification" to "true"
            )
        } else {
            return aplicarModificacionUsuario(usuarioUpdateDTO)
        }
    }
    // Modificar para que no aparezcan usuarios ADMIN en búsquedas
    fun verTodosLosUsuarios(username: String): List<UsuarioDTO> {
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        val usuariosBloqueados = bloqueoRepository.findAllByBloqueador(username)
            .map { it.bloqueado }
            .toSet()

        val usuariosQueBloquearon = bloqueoRepository.findAllByBloqueado(username)
            .map { it.bloqueador }
            .toSet()

        val usuariosConBloqueo = usuariosBloqueados + usuariosQueBloquearon

        return usuarioRepository.findAll()
            .filter { usuario ->
                usuario.username != username &&
                        !usuariosConBloqueo.contains(usuario.username) &&
                        usuario.roles != "ADMIN" // No mostrar usuarios ADMIN
            }
            .sortedBy { usuario ->
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
                    rol = usuario.roles
                )
            }
    }
    fun enviarSolicitudAmistad(solicitudAmistadDTO: SolicitudAmistadDTO): SolicitudAmistadDTO {
        val remitente = usuarioRepository.findFirstByUsername(solicitudAmistadDTO.remitente).orElseThrow {
            throw NotFoundException("Usuario remitente ${solicitudAmistadDTO.remitente} no encontrado")
        }

        val destinatario = usuarioRepository.findFirstByUsername(solicitudAmistadDTO.destinatario).orElseThrow {
            throw NotFoundException("Usuario destinatario ${solicitudAmistadDTO.destinatario} no encontrado")
        }

        if (remitente.roles == "ADMIN") {
            throw BadRequestException("Los administradores no pueden enviar solicitudes de amistad")
        }

        if (destinatario.roles == "ADMIN") {
            throw BadRequestException("No puedes enviar solicitudes de amistad a administradores")
        }

        if (existeBloqueo(solicitudAmistadDTO.remitente, solicitudAmistadDTO.destinatario)) {
            throw BadRequestException("No puedes enviar solicitudes de amistad a usuarios bloqueados o que te han bloqueado")
        }

        val solicitudRemitente = solicitudesAmistadRepository.findByRemitenteAndDestinatario(
            solicitudAmistadDTO.remitente,
            solicitudAmistadDTO.destinatario
        )

        val solicitudDestinatario = solicitudesAmistadRepository.findByRemitenteAndDestinatario(
            solicitudAmistadDTO.destinatario,
            solicitudAmistadDTO.remitente
        )

        if (solicitudRemitente.isNotEmpty() || solicitudDestinatario.isNotEmpty()) {
            throw BadRequestException("Ya existe una solicitud de amistad pendiente con este usuario")
        }

        val nuevaSolicitud = SolicitudAmistad(
            _id = UUID.randomUUID().toString(),
            remitente = solicitudAmistadDTO.remitente,
            destinatario = solicitudAmistadDTO.destinatario,
            fechaEnviada = Date(),
            aceptada = false
        )

        solicitudesAmistadRepository.save(nuevaSolicitud)

        return SolicitudAmistadDTO(
            _id = nuevaSolicitud._id,
            remitente = nuevaSolicitud.remitente,
            destinatario = nuevaSolicitud.destinatario,
            fechaEnviada = nuevaSolicitud.fechaEnviada,
            aceptada = nuevaSolicitud.aceptada
        )
    }

    fun bloquearUsuario(bloqueador: String, bloqueado: String): BloqueoDTO {
        val usuarioBloqueador = usuarioRepository.findFirstByUsername(bloqueador).orElseThrow {
            throw NotFoundException("Usuario bloqueador $bloqueador no encontrado")
        }

        val usuarioBloqueado = usuarioRepository.findFirstByUsername(bloqueado).orElseThrow {
            throw NotFoundException("Usuario a bloquear $bloqueado no encontrado")
        }

        if (usuarioBloqueador.roles == "ADMIN") {
            throw BadRequestException("Los administradores no pueden bloquear usuarios")
        }

        if (usuarioBloqueado.roles == "ADMIN") {
            throw BadRequestException("No puedes bloquear a administradores")
        }

        if (bloqueador == bloqueado) {
            throw BadRequestException("No puedes bloquearte a ti mismo")
        }

        if (bloqueoRepository.existsByBloqueadorAndBloqueado(bloqueador, bloqueado)) {
            throw BadRequestException("Ya has bloqueado a este usuario")
        }

        val nuevoBloqueo = Bloqueo(
            _id = UUID.randomUUID().toString(),
            bloqueador = bloqueador,
            bloqueado = bloqueado,
            fechaBloqueo = Date()
        )

        val bloqueoGuardado = bloqueoRepository.save(nuevoBloqueo)

        eliminarSolicitudesAmistad(bloqueador, bloqueado)
        eliminarAmistad(bloqueador, bloqueado)

        return BloqueoDTO(
            _id = bloqueoGuardado._id,
            bloqueador = bloqueoGuardado.bloqueador,
            bloqueado = bloqueoGuardado.bloqueado,
            fechaBloqueo = bloqueoGuardado.fechaBloqueo
        )
    }

    fun verUsuarioPorUsername(username: String, usuarioSolicitante: String? = null): UsuarioDTO {
        val usuario = usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username not found")
        }

        // Si el solicitante es ADMIN, puede ver cualquier usuario
        val solicitante = if (usuarioSolicitante != null) {
            usuarioRepository.findFirstByUsername(usuarioSolicitante).orElse(null)
        } else null

        val esAdmin = solicitante?.roles == "ADMIN"

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
            rol = usuario.roles
        )
    }

    fun verificarCodigoYCrearUsuario(email: String, codigo: String): UsuarioDTO {

        // Verificar el código
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
            rol = usuario.roles
        )
    }


fun verificarCodigoYModificarUsuario(email: String, codigo: String): UsuarioDTO {
        println("=== VERIFICACION CODIGO MODIFICACION ===")
        println("Email: '$email', Código: '$codigo'")

        // Limpiar códigos expirados
        limpiarCodigosExpirados()

        // Verificar que existe un código para este email
        val codigoData = verificacionCodigos[email]
        if (codigoData == null) {
            println("No se encontró código para: $email")
            println("Códigos disponibles: ${verificacionCodigos.keys}")
            throw BadRequestException("No se encontró código de verificación para este email o el código ha expirado")
        }

        val (codigoAlmacenado, timestamp) = codigoData

        // Verificar que el código no haya expirado
        if (System.currentTimeMillis() - timestamp > CODIGO_EXPIRACION_MS) {
            verificacionCodigos.remove(email)
            modificacionesPendientesVerificacion.remove(email)
            throw BadRequestException("El código de verificación ha expirado. Solicita uno nuevo.")
        }

        println("Código almacenado: '$codigoAlmacenado', Recibido: '$codigo'")

        // Verificar el código
        if (codigoAlmacenado.trim() != codigo.trim()) {
            throw BadRequestException("Código de verificación incorrecto")
        }

        // Obtener los datos de modificación pendiente
        val modificacionData = modificacionesPendientesVerificacion[email]
            ?: throw BadRequestException("No se encontraron datos de modificación para este email")

        // Aplicar la modificación
        val resultado = aplicarModificacionUsuarioInterno(modificacionData)

        // LIMPIAR datos temporales
        verificacionCodigos.remove(email)
        modificacionesPendientesVerificacion.remove(email)

        println("Modificación completada exitosamente")

        return resultado
    }

    // FUNCIÓN AUXILIAR NUEVA
    private fun limpiarCodigosExpirados() {
        val ahora = System.currentTimeMillis()
        val codigosExpirados = verificacionCodigos.filterValues { (_, timestamp) ->
            ahora - timestamp > CODIGO_EXPIRACION_MS
        }

        codigosExpirados.keys.forEach { email ->
            verificacionCodigos.remove(email)
            modificacionesPendientesVerificacion.remove(email)
        }
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
            rol = usuario.roles
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

    // MANTENER ENVÍO DE CÓDIGOS COMO ESTÁ PERO MEJORAR ALMACENAMIENTO
    private fun enviarCodigoVerificacion(email: String): Boolean {
        println("Enviando código a: $email")

        // Configuración para el servidor de correo
        val props = Properties()
        props.put("mail.smtp.auth", "true")
        props.put("mail.smtp.starttls.enable", "true")
        props.put("mail.smtp.host", "smtp.gmail.com")
        props.put("mail.smtp.port", "587")

        // Credenciales de la cuenta de correo
        val username = System.getenv("EMAIL_USERNAME") ?: ""
        val password = System.getenv("EMAIL_PASSWORD") ?: ""

        try {
            // Crear sesión con autenticación
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            // Crear el mensaje
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(username))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            message.subject = "Verificación de correo electrónico - SocialMe"

            // Generar un código aleatorio para verificación
            val codigoVerificacion = generarCodigoVerificacion()
            println("Código generado: $codigoVerificacion")

            // Almacenar el código CON TIMESTAMP para verificación posterior
            verificacionCodigos[email] = Pair(codigoVerificacion, System.currentTimeMillis())

            // Crear el contenido del mensaje
            val htmlContent = """
            <html>
                <body>
                    <h2>Verificación de correo electrónico - SocialMe</h2>
                    <p>Gracias por registrarte o actualizar tu información. Para verificar tu dirección de correo electrónico, 
                    por favor utiliza el siguiente código:</p>
                    <h3 style="background-color: #f2f2f2; padding: 10px; text-align: center;">$codigoVerificacion</h3>
                    <p>Si no has solicitado esta verificación, por favor ignora este mensaje.</p>
                    <p>Este código expirará en 15 minutos.</p>
                </body>
            </html>
        """.trimIndent()

            // Establecer el contenido del mensaje como HTML
            message.setContent(htmlContent, "text/html; charset=utf-8")

            // Enviar el mensaje
            Transport.send(message)

            println("Correo de verificación enviado exitosamente a $email")

            return true

        } catch (e: MessagingException) {
            e.printStackTrace()
            println("Error al enviar el correo de verificación: ${e.message}")
            return false
        }
    }

    private fun generarCodigoVerificacion(): String {
        return (100000..999999).random().toString()
    }

    fun verificarCodigo(email: String, codigo: String): Boolean {
        limpiarCodigosExpirados()
        val codigoData = verificacionCodigos[email]
        return codigoData?.first == codigo
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
            rol = usuario.roles
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
            rol = usuario.roles
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
            rol = usuario.radarDistancia,
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
                        rol=usuario.radarDistancia,
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
                        rol = usuario.roles
                    )
                )
            }
        }

        // Devolver la lista de DTOs
        return listaUsuarios
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
                    rol = usuarioBloqueado.roles
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
                    rol = amigo.roles
                )
            )
        }

        // Devolver la lista de DTOs de amigos
        return listaAmigos
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

    fun usuarioEsAdmin(username: String):Boolean{
        if (usuarioRepository.findFirstByUsername(username).orElseThrow{
            throw NotFoundException("Este usuario no existe")
            }.roles=="ADMIN"){
            return true
        }else{
            return false
        }
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
            rol = usuariodto.roles
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
            rol = usuario.roles
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
            rol = usuario.roles
        )
    }

    fun verRadarDistancia(username:String):String{
        return usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Este usuario no existe")
        }.radarDistancia
    }

    fun verPrivacidadActividad(username:String):String{
        return usuarioRepository.findFirstByUsername(username).orElseThrow {
        NotFoundException("Este usuario no existe")
    }.privacidadActividades
    }

    fun verPrivacidadComunidad(username:String):String{
        return usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Este usuario no existe")
        }.privacidadComunidades
    }

    fun cambiarContrasena(cambiarContrasenaDTO: CambiarContrasenaDTO): UsuarioDTO {
        // Buscar el usuario por username
        val usuario = usuarioRepository.findFirstByUsername(cambiarContrasenaDTO.username).orElseThrow {
            NotFoundException("Usuario ${cambiarContrasenaDTO.username} no encontrado")
        }

        // Validar que la nueva contraseña no esté vacía
        if (cambiarContrasenaDTO.passwordNueva.isBlank()) {
            throw BadRequestException("La contraseña no puede estar vacía")
        }

        if (cambiarContrasenaDTO.passwordRepeat.isBlank()) {
            throw BadRequestException("La contraseña no puede estar vacía")
        }

        if (cambiarContrasenaDTO.passwordActual.isBlank()) {
            throw BadRequestException("La contraseña no puede estar vacía")
        }

        if (cambiarContrasenaDTO.passwordNueva!=cambiarContrasenaDTO.passwordRepeat){
            throw BadRequestException("Las contraseñas no coinciden")
        }

        if (passwordEncoder.matches(cambiarContrasenaDTO.passwordActual, usuario.password)) {

            // Validar longitud mínima de contraseña (opcional, ajusta según tus requisitos)
            if (cambiarContrasenaDTO.passwordNueva.length < 6) {
                throw BadRequestException("La contraseña debe tener al menos 6 caracteres")
            }

            // Verificar que la nueva contraseña no sea igual a la anterior
            if (passwordEncoder.matches(cambiarContrasenaDTO.passwordNueva, usuario.password)) {
                throw BadRequestException("La nueva contraseña debe ser diferente a la contraseña actual")
            }

            // Actualizar solo el campo password usando el encoder
            usuario.password = passwordEncoder.encode(cambiarContrasenaDTO.passwordNueva)

            // Guardar el usuario actualizado
            val usuarioActualizado = usuarioRepository.save(usuario)

            // Retornar el DTO del usuario
            return UsuarioDTO(
                username = usuarioActualizado.username,
                email = usuarioActualizado.email,
                intereses = usuarioActualizado.intereses,
                nombre = usuarioActualizado.nombre,
                apellido = usuarioActualizado.apellidos,
                fotoPerfilId = usuarioActualizado.fotoPerfilId ?: "",
                direccion = usuarioActualizado.direccion,
                descripcion = usuarioActualizado.descripcion,
                premium = usuarioActualizado.premium,
                privacidadActividades = usuarioActualizado.privacidadActividades,
                privacidadComunidades = usuarioActualizado.privacidadComunidades,
                radarDistancia = usuarioActualizado.radarDistancia,
                rol = usuarioActualizado.roles
            )
        }else{
            throw BadRequestException ("Esta contraseña no es valida")
        }
    }

    fun updatePremiumStatus(username: String, isPremium: Boolean): Boolean {
        return try {
            // Buscar usuario por username
            val user = usuarioRepository.findFirstByUsername(username).orElseThrow{
                NotFoundException("Este usuario no existe")
            }

            if (user != null) {
                user.premium = isPremium
                usuarioRepository.save(user)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            false
        }
    }

    fun cancelarSolicitudAmistad(id: String): Boolean {
        // Buscar la solicitud por ID
        val solicitud = solicitudesAmistadRepository.findById(id).orElseThrow {
            throw NotFoundException("Solicitud de amistad con ID $id no encontrada")
        }

        // Verificar que la solicitud no haya sido aceptada ya
        if (solicitud.aceptada) {
            throw BadRequestException("No se puede cancelar una solicitud que ya ha sido aceptada")
        }

        // Eliminar la solicitud
        solicitudesAmistadRepository.delete(solicitud)

        return true
    }

    fun rechazarSolicitudAmistad(id: String): Boolean {
        // Buscar la solicitud por ID
        val solicitud = solicitudesAmistadRepository.findById(id).orElseThrow {
            throw NotFoundException("Solicitud de amistad con ID $id no encontrada")
        }

        // Verificar que la solicitud no haya sido aceptada ya
        if (solicitud.aceptada) {
            throw BadRequestException("Esta solicitud ya ha sido aceptada")
        }

        // Eliminar la solicitud (rechazar es equivalente a eliminar)
        solicitudesAmistadRepository.delete(solicitud)

        return true
    }

    // Método auxiliar para verificar si existe una solicitud pendiente entre dos usuarios
    fun verificarSolicitudPendiente(remitente: String, destinatario: String): Boolean {
        val solicitudes = solicitudesAmistadRepository.findByRemitenteAndDestinatario(remitente, destinatario)
        return solicitudes.any { !it.aceptada }
    }


    fun verActividadesPorUsernameFechaSuperior(username: String, usuarioSolicitante: String): List<ActividadDTO> {
        val usuarioObjetivo = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { NotFoundException("Usuario $username no encontrado") }

        usuarioRepository.findFirstByUsername(usuarioSolicitante)
            .orElseThrow { NotFoundException("Usuario solicitante $usuarioSolicitante no encontrado") }

        if (existeBloqueo(username, usuarioSolicitante)) {
            throw BadRequestException("No puedes ver las actividades de este usuario")
        }

        if (username == usuarioSolicitante) {
            val participantes = participantesActividadRepository.findByUsername(username)
            val actividadesIds = participantes.map { it.idActividad }
            val actividadesEncontradas = mutableListOf<Actividad>()
            val fechaActual = Date()

            actividadesIds.forEach { idActividad ->
                val actividad = actividadRepository.findActividadBy_id(idActividad)
                if (actividad.isPresent && actividad.get().fechaInicio.after(fechaActual)) {
                    actividadesEncontradas.add(actividad.get())
                }
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
            }.sortedBy { it.fechaInicio }
        }

        when (usuarioObjetivo.privacidadActividades.uppercase()) {
            "NADIE" -> return emptyList()
            "AMIGOS" -> {
                val sonAmigos = verificarAmistad(username, usuarioSolicitante)
                if (!sonAmigos) {
                    return emptyList()
                }
            }
            "TODOS" -> {
            }
        }

        val participantes = participantesActividadRepository.findByUsername(username)
        val actividadesIds = participantes.map { it.idActividad }
        val actividadesEncontradas = mutableListOf<Actividad>()
        val fechaActual = Date()

        actividadesIds.forEach { idActividad ->
            val actividad = actividadRepository.findActividadBy_id(idActividad)
            if (actividad.isPresent) {
                val act = actividad.get()
                if (act.fechaInicio.after(fechaActual)) {
                    val puedeVerActividad = if (act.privada) {
                        participantesComunidadRepository.findByUsernameAndComunidad(usuarioSolicitante, act.comunidad).isPresent
                    } else {
                        true
                    }

                    if (puedeVerActividad) {
                        actividadesEncontradas.add(act)
                    }
                }
            }
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
        }.sortedBy { it.fechaInicio }
    }

    fun normalizarTexto(texto: String): String {
        return texto
            .replace("á", "a").replace("Á", "A")
            .replace("é", "e").replace("É", "E")
            .replace("í", "i").replace("Í", "I")
            .replace("ó", "o").replace("Ó", "O")
            .replace("ú", "u").replace("Ú", "U")
            .replace("ü", "u").replace("Ü", "U")
            .replace("ñ", "n").replace("Ñ", "N")
            .replace("ç", "c").replace("Ç", "C")
    }
}
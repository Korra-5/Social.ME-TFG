package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Bloqueo
import com.example.socialme.model.Coordenadas
import com.example.socialme.model.SolicitudAmistad
import com.example.socialme.model.Usuario
import com.example.socialme.repository.*
import org.springframework.beans.factory.annotation.Autowired
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
    private lateinit var bloqueoRepository: BloqueoRepository

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

    // Mapa para almacenar temporalmente los códigos de verificación
    private val verificacionCodigos = mutableMapOf<String, String>()

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


        // Primero, verificar el correo electrónico
        if (!verificarGmail(usuarioInsertadoDTO.email)) {
            throw BadRequestException("No se pudo verificar el correo electrónico ${usuarioInsertadoDTO.email}")
        }



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
            fechaUnion = Date.from(Instant.now()),
            coordenadas = null,
            premium =false
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
            fotoPerfilId = fotoPerfilId,
            premium = usuario.premium
        )
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
            premium = usuario.premium
        )

        usuarioRepository.delete(usuario)
        return userDTO
    }

    fun modificarUsuario(usuarioUpdateDTO: UsuarioUpdateDTO): UsuarioDTO {

        // Buscar el usuario existente usando currentUsername
        val usuario = usuarioRepository.findFirstByUsername(usuarioUpdateDTO.currentUsername).orElseThrow {
            throw NotFoundException("Usuario ${usuarioUpdateDTO.currentUsername} no encontrado")
        }

        if (usuarioUpdateDTO.newUsername!=null) {
            usuarioRepository.findFirstByUsername(usuarioUpdateDTO.newUsername).orElseThrow {
                throw NotFoundException("Usuario ${usuarioUpdateDTO.newUsername} ya existe, prueba con otro nombre")
            }
        }

        // Verificar el correo electrónico si se ha cambiado
        if (usuarioUpdateDTO.email != null && usuarioUpdateDTO.email != usuario.email) {
            if (!verificarGmail(usuarioUpdateDTO.email)) {
                throw BadRequestException("No se pudo verificar el nuevo correo electrónico ${usuarioUpdateDTO.email}")
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
            email = usuarioUpdateDTO.email ?: usuario.email // Mantener el email existente si no se proporciona uno nuevo
            nombre = usuarioUpdateDTO.nombre ?: usuario.nombre
            apellidos = usuarioUpdateDTO.apellido ?: usuario.apellidos
            descripcion = usuarioUpdateDTO.descripcion ?: usuario.descripcion
            intereses = usuarioUpdateDTO.intereses ?: usuario.intereses
            fotoPerfilId = nuevaFotoPerfilId
            direccion = usuarioUpdateDTO.direccion ?: usuario.direccion
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
            email = usuario.email,
            intereses = usuario.intereses,
            nombre = usuario.nombre,
            apellido = usuario.apellidos,
            fotoPerfilId = nuevaFotoPerfilId,
            direccion = usuario.direccion,
            descripcion = usuario.descripcion,
            premium = usuario.premium
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
            premium = usuario.premium
        )
    }



    fun verificarGmail(gmail: String): Boolean {
        // Configuración para el servidor de correo (usando Gmail como ejemplo)
        val props = Properties()
        props.put("mail.smtp.auth", "true")
        props.put("mail.smtp.starttls.enable", "true")
        props.put("mail.smtp.host", "smtp.gmail.com")
        props.put("mail.smtp.port", "587")

        // Credenciales de tu cuenta de correo (desde donde enviarás la verificación)
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
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(gmail))
            message.subject = "Verificación de correo electrónico - SocialMe"

            // Generar un código aleatorio para verificación
            val codigoVerificacion = generarCodigoVerificacion()

            // Almacenar el código para verificación posterior
            verificacionCodigos[gmail] = codigoVerificacion

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

            println("Correo de verificación enviado exitosamente a $gmail")

            // Aquí se debería implementar un mecanismo para verificar que el usuario
            // ingrese el código correcto, pero para simplificar, asumimos que la verificación
            // es exitosa si el correo se envía correctamente
            return true

        } catch (e: MessagingException) {
            e.printStackTrace()
            println("Error al enviar el correo de verificación: ${e.message}")
            return false
        }
    }

    // Método para generar un código de verificación aleatorio
    private fun generarCodigoVerificacion(): String {
        return (100000..999999).random().toString()
    }

    // Método para verificar un código ingresado por el usuario
    fun verificarCodigo(email: String, codigo: String): Boolean {
        val codigoAlmacenado = verificacionCodigos[email]
        return codigoAlmacenado == codigo
    }

    // Agregar este método en UsuarioService
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
            premium = usuario.premium
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
                        premium = usuario.premium
                    )
                )
            }
        }

        // Devolver la lista de DTOs
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
                        premium = usuario.premium
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
                    premium = usuario.premium
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

    /**
     * Desbloquea a un usuario
     */
    fun desbloquearUsuario(bloqueador: String, bloqueado: String): Boolean {
        // Buscar el bloqueo existente
        val bloqueo = bloqueoRepository.findByBloqueadorAndBloqueado(bloqueador, bloqueado).orElseThrow {
            throw NotFoundException("No existe un bloqueo de $bloqueador hacia $bloqueado")
        }

        // Eliminar el bloqueo
        bloqueoRepository.delete(bloqueo)

        return true
    }

    /**
     * Verifica si un usuario ha bloqueado a otro
     */
    fun existeBloqueo(usuario1: String, usuario2: String): Boolean {
        // Comprobar si existe un bloqueo en cualquier dirección
        return bloqueoRepository.existsByBloqueadorAndBloqueado(usuario1, usuario2) ||
                bloqueoRepository.existsByBloqueadorAndBloqueado(usuario2, usuario1)
    }

    /**
     * Obtiene la lista de usuarios bloqueados por un usuario
     */
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
                    premium = usuarioBloqueado.premium
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
    /**
     * Función para ver las solicitudes de amistad pendientes de un usuario
     * Corresponde al endpoint: GET /Usuario/verSolicitudesAmistad/{username}
     */
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

    /**
     * Función para ver los amigos de un usuario (solicitudes aceptadas)
     * Corresponde al endpoint: GET /Usuario/verAmigos/{username}
     */
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
                    premium = amigo.premium
                )
            )
        }

        // Devolver la lista de DTOs de amigos
        return listaAmigos
    }

    /**
     * Función para enviar una solicitud de amistad
     * Corresponde al endpoint: POST /Usuario/enviarSolicitudAmistad
     */
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

        // Crear la nueva solicitud de amistad con estado no aceptado (false)
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
        // (Como SolicitudAmistad usa vals inmutables, necesitamos crear una nueva instancia)
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
}

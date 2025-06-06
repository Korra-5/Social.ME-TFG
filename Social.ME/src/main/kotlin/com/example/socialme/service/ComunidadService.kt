package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.ForbiddenException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.*
import com.example.socialme.repository.*
import com.example.socialme.utils.ContentValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class ComunidadService {

    @Autowired
    private lateinit var usuarioService: UsuarioService

    @Autowired
    private lateinit var mensajeRepository: MensajeRepository

    @Autowired
    private lateinit var actividadRepository: ActividadRepository

    @Autowired
    private lateinit var denunciaRepository: DenunciaRepository

    @Autowired
    private lateinit var notificacionRepository: NotificacionRepository

    @Autowired
    private lateinit var actividadesComunidadRepository: ActividadesComunidadRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var participantesActividadRepository: ParticipantesActividadRepository

    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var gridFSService: GridFSService


    private fun validarIntereses(intereses: List<String>) {
        intereses.forEach { interes ->
            val interesLimpio = interes.trim()
            if (interesLimpio.length > 25) {
                throw BadRequestException("Los intereses no pueden exceder los 25 caracteres: '$interesLimpio'")
            }
            if (interesLimpio.contains(" ")) {
                throw BadRequestException("Los intereses no pueden contener espacios: '$interesLimpio'")
            }
            if (interesLimpio.contains(",")) {
                throw BadRequestException("Los intereses no pueden contener comas: '$interesLimpio'")
            }
        }
    }

    fun crearComunidad(comunidadCreateDTO: ComunidadCreateDTO): ComunidadDTO {
        ContentValidator.validarContenidoInapropiado(
            comunidadCreateDTO.nombre,
            comunidadCreateDTO.descripcion,
            comunidadCreateDTO.url
        )

        validarIntereses(comunidadCreateDTO.intereses)

        if (comunidadRepository.findComunidadByUrl(comunidadCreateDTO.url).isPresent) {
            throw BadRequestException("Comunidad existente")
        }

        val formattedUrl = comunidadCreateDTO.url.trim().split(Regex("\\s+")).joinToString("-").toLowerCase()

        if (comunidadCreateDTO.nombre.length > 40) {
            throw BadRequestException("Este nombre es demasiado largo, pruebe con uno inferior a 40 caracteres")
        }
        if (comunidadCreateDTO.descripcion.length > 5000) {
            throw BadRequestException("Lo sentimos, la descripción no puede superar los 5000 caracteres")
        }

        if (!usuarioRepository.existsByUsername(comunidadCreateDTO.creador)) {
            throw NotFoundException("Usuario no encontrado")
        }

        val comunidadesCreadas = comunidadRepository.countByCreador(comunidadCreateDTO.creador)
        if (comunidadesCreadas >= 3) {
            throw ForbiddenException("Has alcanzado el límite máximo de 3 comunidades creadas")
        }

        val fotoPerfilId = if (comunidadCreateDTO.fotoPerfilBase64 != null) {
            gridFSService.storeFileFromBase64(
                comunidadCreateDTO.fotoPerfilBase64,
                "community_profile_${formattedUrl}_${Date().time}",
                "image/jpeg",
                mapOf("type" to "profilePhoto", "community" to formattedUrl)
            )
        } else comunidadCreateDTO.fotoPerfilId ?: throw BadRequestException("Se requiere una foto de perfil")

        val comunidad: Comunidad =
            Comunidad(
                _id = null,
                nombre = comunidadCreateDTO.nombre,
                descripcion = comunidadCreateDTO.descripcion,
                creador = comunidadCreateDTO.creador,
                intereses = comunidadCreateDTO.intereses,
                fotoPerfilId = fotoPerfilId,
                fotoCarruselIds = null,
                administradores = null,
                fechaCreacion = Date.from(Instant.now()),
                url = usuarioService.normalizarTexto(formattedUrl),
                privada = comunidadCreateDTO.privada,
                coordenadas = comunidadCreateDTO.coordenadas,
                codigoUnion = if (comunidadCreateDTO.privada) {
                    generarCodigoUnico()
                }else{
                    null
                }
            )

        val participantesComunidad = ParticipantesComunidad(
            comunidad = comunidad.url,
            username = comunidad.creador,
            fechaUnion = Date.from(Instant.now()),
            _id = null
        )

        comunidadRepository.insert(comunidad)
        participantesComunidadRepository.insert(participantesComunidad)

        return ComunidadDTO(
            url = formattedUrl,
            nombre = comunidadCreateDTO.nombre,
            creador = comunidadCreateDTO.creador,
            intereses = comunidadCreateDTO.intereses,
            fotoCarruselIds = null,
            fotoPerfilId = fotoPerfilId,
            descripcion = comunidadCreateDTO.descripcion,
            fechaCreacion = Date.from(Instant.now()),
            administradores = null,
            privada = comunidadCreateDTO.privada,
            coordenadas = comunidadCreateDTO.coordenadas,
            codigoUnion = comunidadCreateDTO.codigoUnion
        )
    }

    fun modificarComunidad(comunidadUpdateDTO: ComunidadUpdateDTO): ComunidadDTO {
        ContentValidator.validarContenidoInapropiado(
            comunidadUpdateDTO.nombre,
            comunidadUpdateDTO.descripcion,
            comunidadUpdateDTO.newUrl
        )

        validarIntereses(comunidadUpdateDTO.intereses)

        val comunidadExistente = comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.currentURL).orElseThrow {
            throw NotFoundException("Comunidad con URL ${comunidadUpdateDTO.currentURL} no encontrada")
        }

        if (comunidadUpdateDTO.newUrl != comunidadUpdateDTO.currentURL) {
            comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.newUrl).ifPresent {
                throw BadRequestException("Ya existe una comunidad con la URL ${comunidadUpdateDTO.newUrl}, prueba con otra URL")
            }
        }

        comunidadUpdateDTO.administradores?.forEach { admin ->
            if (!usuarioRepository.existsByUsername(admin)) {
                throw NotFoundException("Administrador con username '$admin' no encontrado")
            }
        }

        val urlAntigua = comunidadExistente.url
        val nombreAntiguo = comunidadExistente.nombre
        val nombreNuevo = comunidadUpdateDTO.nombre

        val nuevaFotoPerfilId = if (!comunidadUpdateDTO.fotoPerfilBase64.isNullOrBlank()) {
            try {
                if (!comunidadExistente.fotoPerfilId.isNullOrBlank()) {
                    gridFSService.deleteFile(comunidadExistente.fotoPerfilId)
                }
            } catch (e: Exception) {
                println("Error al eliminar la foto de perfil antigua: ${e.message}")
            }

            val urlParaFoto = comunidadUpdateDTO.newUrl
            gridFSService.storeFileFromBase64(
                comunidadUpdateDTO.fotoPerfilBase64,
                "community_profile_${urlParaFoto}_${Date().time}",
                "image/jpeg",
                mapOf(
                    "type" to "profilePhoto",
                    "community" to urlParaFoto
                )
            ) ?: comunidadExistente.fotoPerfilId
        } else if (comunidadUpdateDTO.fotoPerfilId != null) {
            comunidadUpdateDTO.fotoPerfilId
        } else {
            comunidadExistente.fotoPerfilId
        }

        val nuevasFotosCarruselIds = if (comunidadUpdateDTO.fotoCarruselBase64 != null && comunidadUpdateDTO.fotoCarruselBase64.isNotEmpty()) {
            val fotosExistentes = comunidadUpdateDTO.fotoCarruselIds ?: comunidadExistente.fotoCarruselIds ?: emptyList()

            val urlParaFotos = comunidadUpdateDTO.newUrl
            val nuevasFotosIds = comunidadUpdateDTO.fotoCarruselBase64.mapIndexedNotNull { index, base64 ->
                gridFSService.storeFileFromBase64(
                    base64,
                    "community_carousel_${urlParaFotos}_${System.currentTimeMillis()}_${index}",
                    "image/jpeg",
                    mapOf(
                        "type" to "carouselPhoto",
                        "community" to urlParaFotos,
                        "position" to (fotosExistentes.size + index).toString()
                    )
                )
            }

            fotosExistentes + nuevasFotosIds
        } else {
            comunidadUpdateDTO.fotoCarruselIds ?: comunidadExistente.fotoCarruselIds
        }

        comunidadExistente.apply {
            url = usuarioService.normalizarTexto(comunidadUpdateDTO.newUrl)
            nombre = comunidadUpdateDTO.nombre
            descripcion = comunidadUpdateDTO.descripcion
            intereses = comunidadUpdateDTO.intereses
            administradores = comunidadUpdateDTO.administradores
            fotoPerfilId = nuevaFotoPerfilId
            fotoCarruselIds = nuevasFotosCarruselIds
            privada = comunidadUpdateDTO.privada
            coordenadas = comunidadUpdateDTO.coordenadas
        }

        val comunidadActualizada = comunidadRepository.save(comunidadExistente)

        if (urlAntigua != comunidadActualizada.url) {
            val actividades = actividadesComunidadRepository.findByComunidad(urlAntigua).orElse(emptyList())
            actividades.forEach { actividad ->
                actividad.comunidad = comunidadActualizada.url
                actividadesComunidadRepository.save(actividad)
            }

            val participantes = participantesComunidadRepository.findByComunidad(urlAntigua)
            participantes.forEach { participante ->
                participante.comunidad = comunidadActualizada.url
                participantesComunidadRepository.save(participante)
            }

            val mensajes = mensajeRepository.findByComunidadUrlOrderByFechaEnvioAsc(urlAntigua)
            mensajes.forEach { mensaje ->
                val mensajeActualizado = Mensaje(
                    _id = mensaje._id,
                    comunidadUrl = comunidadActualizada.url,
                    username = mensaje.username,
                    contenido = mensaje.contenido,
                    fechaEnvio = mensaje.fechaEnvio,
                    leido = mensaje.leido
                )
                mensajeRepository.save(mensajeActualizado)
            }

            val actividadesDeComunidad = actividadRepository.findAll().filter { it.comunidad == urlAntigua }
            actividadesDeComunidad.forEach { actividad ->
                actividad.comunidad = comunidadActualizada.url
                actividadRepository.save(actividad)
            }
        }

        if (nombreAntiguo != nombreNuevo) {
            val denunciasComoItem = denunciaRepository.findAll().filter {
                it.tipoItemDenunciado == "comunidad" && it.nombreItemDenunciado == nombreAntiguo
            }
            denunciasComoItem.forEach { denuncia ->
                val denunciaActualizada = Denuncia(
                    _id = denuncia._id,
                    motivo = denuncia.motivo,
                    cuerpo = denuncia.cuerpo,
                    nombreItemDenunciado = nombreNuevo,
                    tipoItemDenunciado = denuncia.tipoItemDenunciado,
                    usuarioDenunciante = denuncia.usuarioDenunciante,
                    fechaCreacion = denuncia.fechaCreacion,
                    solucionado = denuncia.solucionado
                )
                denunciaRepository.save(denunciaActualizada)
            }

            val notificacionesComunidad = notificacionRepository.findAll().filter {
                it.entidadNombre == nombreAntiguo
            }
            notificacionesComunidad.forEach { notificacion ->
                val notificacionActualizada = Notificacion(
                    _id = notificacion._id,
                    tipo = notificacion.tipo,
                    titulo = notificacion.titulo,
                    mensaje = notificacion.mensaje,
                    usuarioDestino = notificacion.usuarioDestino,
                    entidadId = notificacion.entidadId,
                    entidadNombre = nombreNuevo,
                    fechaCreacion = notificacion.fechaCreacion,
                    leida = notificacion.leida
                )
                notificacionRepository.save(notificacionActualizada)
            }
        }

        return ComunidadDTO(
            url = comunidadActualizada.url,
            nombre = comunidadActualizada.nombre,
            creador = comunidadActualizada.creador,
            intereses = comunidadActualizada.intereses,
            fotoCarruselIds = comunidadActualizada.fotoCarruselIds,
            fotoPerfilId = comunidadActualizada.fotoPerfilId,
            descripcion = comunidadActualizada.descripcion,
            fechaCreacion = comunidadActualizada.fechaCreacion,
            administradores = comunidadActualizada.administradores,
            privada = comunidadActualizada.privada,
            coordenadas = comunidadActualizada.coordenadas,
            codigoUnion = comunidadActualizada.codigoUnion
        )
    }

    fun unirseComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("La comunidad no existe") }

        val usuario = usuarioRepository.findFirstByUsername(participantesComunidadDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        // Los ADMIN no pueden unirse a comunidades
        if (usuario.roles == "ADMIN") {
            throw BadRequestException("Los administradores no pueden unirse a comunidades")
        }

        if (participantesComunidadRepository.findByUsernameAndComunidad(
                participantesComunidadDTO.username,
                participantesComunidadDTO.comunidad
            ).isPresent
        ) {
            throw BadRequestException("El usuario ya está unido a esta comunidad")
        }

        val union = ParticipantesComunidad(
            _id = null,
            comunidad = participantesComunidadDTO.comunidad,
            username = participantesComunidadDTO.username,
            fechaUnion = Date.from(Instant.now())
        )

        participantesComunidadRepository.insert(union)

        return participantesComunidadDTO
    }

    fun unirseComunidadPorCodigo(participantesComunidadDTO: ParticipantesComunidadDTO, codigo: String): ParticipantesComunidadDTO {
        val comunidad = comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("La comunidad no existe") }

        if (comunidad.codigoUnion == null) {
            throw BadRequestException("La comunidad ${comunidad.url} es publica")
        }

        val usuario = usuarioRepository.findFirstByUsername(participantesComunidadDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        // Los ADMIN no pueden unirse a comunidades
        if (usuario.roles == "ADMIN") {
            throw BadRequestException("Los administradores no pueden unirse a comunidades")
        }

        if (participantesComunidadRepository.findByUsernameAndComunidad(
                participantesComunidadDTO.username,
                participantesComunidadDTO.comunidad
            ).isPresent
        ) {
            throw BadRequestException("El usuario ya está unido a esta comunidad")
        }

        if (codigo == comunidad.codigoUnion) {
            val union = ParticipantesComunidad(
                _id = null,
                comunidad = participantesComunidadDTO.comunidad,
                username = participantesComunidadDTO.username,
                fechaUnion = Date.from(Instant.now())
            )

            participantesComunidadRepository.insert(union)

            return participantesComunidadDTO
        } else {
            throw BadRequestException("El codigo de union no es correcto")
        }
    }

    fun verComunidadPorUrl(url: String) : ComunidadDTO {
        val comunidad=comunidadRepository.findComunidadByUrl(url).orElseThrow {
            throw NotFoundException("Comunidad not found: $url")
        }
        return  ComunidadDTO(
            nombre = comunidad.nombre,
            descripcion = comunidad.descripcion,
            creador = comunidad.creador,
            intereses = comunidad.intereses,
            fotoPerfilId = comunidad.fotoPerfilId,
            fotoCarruselIds = comunidad.fotoCarruselIds,
            administradores = comunidad.administradores,
            fechaCreacion = comunidad.fechaCreacion,
            privada = comunidad.privada,
            url =comunidad.url,
            coordenadas = comunidad.coordenadas,
            codigoUnion = comunidad.codigoUnion
        )
    }

    fun verComunidadesPublicasEnZona(
        username: String
    ): List<ComunidadDTO> {
        val distancia=usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario no existe")
        }.radarDistancia.toFloat()

        // Obtenemos todas las comunidades
        val todasLasComunidades = comunidadRepository.findAll()

        // Obtenemos el usuario para acceder a sus coordenadas e intereses
        val usuario = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { throw NotFoundException("Usuario no existe") }

        val coordenadasUser = usuario.coordenadas
        val interesesUser = usuario.intereses

        // Obtenemos las comunidades a las que el usuario ya está unido
        val comunidadesDelUsuario = participantesComunidadRepository.findByUsername(username)
            .map { it.comunidad }
            .toSet()

        return todasLasComunidades
            .filter { !it.privada }
            .filter { comunidad ->
                // Filtrar aquellas a las que el usuario no esté unido ya
                !comunidadesDelUsuario.contains(comunidad.url)
            }
            .filter { comunidad ->
                // Verificar la distancia
                verificarDistancia(comunidad.coordenadas, coordenadasUser, distancia)
            }
            .map { comunidad ->
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
            }
            .sortedWith(compareByDescending<ComunidadDTO> { comunidadDTO ->
                // Primera ordenación: por número de intereses coincidentes
                comunidadDTO.intereses.count { interes -> interesesUser.contains(interes) }
            }.thenByDescending {
                // Segunda ordenación: por fecha de creación (las más recientes primero)
                it.fechaCreacion
            })
    }

    // Verifica si una comunidad está dentro del radio de distancia especificado
    private fun verificarDistancia(coordenadasComunidad: Coordenadas?, coordenadasUser: Coordenadas?, distanciaKm: Float?): Boolean {
        // Si falta algún parámetro necesario para el cálculo de distancia, devuelve true para incluir la comunidad
        if (coordenadasComunidad == null || coordenadasUser == null || distanciaKm == null) {
            return true
        }

        // Cálculo de la distancia
        val distanciaCalculada = GeoUtils.calcularDistancia(coordenadasUser, coordenadasComunidad)

        // Verificamos si la distancia calculada es menor o igual que la distancia especificada
        return distanciaCalculada <= distanciaKm
    }

    fun eliminarComunidad(url: String): ComunidadDTO {
        val comunidad = comunidadRepository.findComunidadByUrl(url).orElseThrow { BadRequestException("Esta comunidad no existe") }

        val comunidadDto = ComunidadDTO(
            url = comunidad.url,
            nombre = comunidad.nombre,
            creador = comunidad.creador,
            intereses = comunidad.intereses,
            fotoCarruselIds = comunidad.fotoCarruselIds,
            fotoPerfilId = comunidad.fotoPerfilId,
            descripcion = comunidad.descripcion,
            fechaCreacion = comunidad.fechaCreacion,
            administradores = comunidad.administradores,
            privada = comunidad.privada,
            coordenadas = comunidad.coordenadas,
            codigoUnion = comunidad.codigoUnion
        )

        // NUEVO: Obtener todas las actividades de la comunidad y eliminar participaciones
        val actividadesComunidad = actividadesComunidadRepository.findByComunidad(url).orElse(emptyList())
        actividadesComunidad.forEach { actividadComunidad ->
            // Eliminar todas las participaciones en cada actividad de la comunidad
            participantesActividadRepository.deleteByIdActividad(actividadComunidad.idActividad ?: "")
        }

        // Elimina imagenes de GridFS
        try {
            gridFSService.deleteFile(comunidad.fotoPerfilId)
            comunidad.fotoCarruselIds?.forEach { fileId ->
                gridFSService.deleteFile(fileId)
            }
        } catch (e: Exception) {
            // Log para mis pruebas
            println("Error deleting GridFS files: ${e.message}")
        }

        // Eliminar primero todos los participantes de la comunidad
        participantesComunidadRepository.deleteByComunidad(comunidad.url)

        // Luego eliminar las actividades asociadas a la comunidad
        actividadesComunidadRepository.deleteByComunidad(comunidad.url)

        // Finalmente eliminar la comunidad
        comunidadRepository.delete(comunidad)

        chatService.eliminarMensajesComunidad(url)

        return comunidadDto
    }

    fun salirComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        val union = participantesComunidadRepository.findByUsernameAndComunidad(username = participantesComunidadDTO.username, comunidad = participantesComunidadDTO.comunidad).orElseThrow {
            throw BadRequestException("No estás en esta comunidad")
        }

        val comunidad=comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad).orElseThrow {
            throw NotFoundException("No existe esta comunidad")
        }

        if (comunidad.creador==participantesComunidadDTO.username) {
            throw BadRequestException("El creador no puede abandonar la comunidad")
        }

        // NUEVO: Eliminar participaciones en actividades privadas de esta comunidad
        val actividadesComunidad = actividadesComunidadRepository.findByComunidad(participantesComunidadDTO.comunidad).orElse(emptyList())
        actividadesComunidad.forEach { actividadComunidad ->
            val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad).orElse(null)
            // Si la actividad es privada, eliminar la participación del usuario
            if (actividad != null && actividad.privada) {
                val participacionActividad = participantesActividadRepository.findByUsernameAndIdActividad(
                    participantesComunidadDTO.username,
                    actividadComunidad.idActividad ?: ""
                )
                if (participacionActividad.isPresent) {
                    participantesActividadRepository.delete(participacionActividad.get())
                }
            }
        }

        participantesComunidadRepository.delete(union)

        return ParticipantesComunidadDTO(
            comunidad = union.comunidad,
            username = union.username
        )
    }

    fun validateAndReplaceSpaces(inputList: List<String>): List<String> {
        return inputList.map {
            val trimmed = it.trim()
            if (trimmed.length > 25) throw BadRequestException("Los intereses no pueden exceder los 25 caracteres")
            if (trimmed.contains(" ")) throw BadRequestException("Con el fin de facilitar su uso, los intereses no pueden contener espacios")
            trimmed
        }
    }

    fun booleanUsuarioApuntadoComunidad(participantesComunidadDTO: ParticipantesComunidadDTO):Boolean{
        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("Esta comunidad no existe") }

        // Verificar que el usuario existe
        if (usuarioRepository.findFirstByUsername(participantesComunidadDTO.username).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        return participantesComunidadRepository.findByUsernameAndComunidad(participantesComunidadDTO.username,participantesComunidadDTO.comunidad).isPresent

    }

    fun contarUsuariosEnUnaComunidad(comunidad:String):Int{
        if (comunidadRepository.findComunidadByUrl(comunidad).isEmpty) {
            throw BadRequestException("Comunidad no existe")
        }
        val participaciones=participantesComunidadRepository.findByComunidad(comunidad)
        var usuarios:Int=0
        participaciones.forEach {
            usuarios++
        }
        return usuarios
    }

    fun verificarCreadorAdministradorComunidad(comunidadUrl: String, username: String):Boolean{
        val comunidad=comunidadRepository.findComunidadByUrl(comunidadUrl).orElseThrow {
            NotFoundException("Comunidad no existe")
        }
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario no encontrado")
        }

        return comunidad.creador == username || comunidad.administradores!!.contains(username)
    }

    fun verTodasComunidadesPublicas():List<ComunidadDTO>{
        val todasLasComunidades = comunidadRepository.findAll()

        return todasLasComunidades
            .filter { !it.privada }
            .map { comunidad ->
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
            }
    }


    private fun generarCodigoUnico(): String {
        val caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val longitud = 10
        val random = Random()

        var codigoGenerado: String
        var codigoExistente: Boolean

        do {
            // Generar un nuevo código
            codigoGenerado = (1..longitud)
                .map { caracteres[random.nextInt(caracteres.length)] }
                .joinToString("")

            // Verificar si el código ya existe en alguna comunidad
            codigoExistente = comunidadRepository.findComunidadByCodigoUnion(codigoGenerado).isPresent

        } while (codigoExistente) // Repetir si el código ya existe

        return codigoGenerado
    }

    fun verComunidadesPorUsuarioCreador(username: String): List<ComunidadDTO> {
        // Verificar que el usuario existe
        if (!usuarioRepository.existsByUsername(username)) {
            throw NotFoundException("Usuario no encontrado")
        }

        // Buscar todas las comunidades donde el usuario es creador
        val comunidades = comunidadRepository.findByCreador(username)

        // Si no es creador de ninguna comunidad, devolver lista vacía
        if (comunidades.isEmpty()) {
            return emptyList()
        }

        return comunidades.map { comunidad ->
            ComunidadDTO(
                url = comunidad.url,
                nombre = comunidad.nombre,
                creador = comunidad.creador,
                intereses = comunidad.intereses,
                fotoCarruselIds = comunidad.fotoCarruselIds,
                fotoPerfilId = comunidad.fotoPerfilId,
                descripcion = comunidad.descripcion,
                fechaCreacion = comunidad.fechaCreacion,
                administradores = comunidad.administradores,
                privada = comunidad.privada,
                coordenadas = comunidad.coordenadas,
                codigoUnion = comunidad.codigoUnion
            )
        }
    }

    fun eliminarUsuarioDeComunidad(
        participantesComunidadDTO: ParticipantesComunidadDTO,
        usuarioSolicitante: String
    ): ParticipantesComunidadDTO {
        val usuarioAEliminar = participantesComunidadDTO.username
        val comunidadUrl = participantesComunidadDTO.comunidad

        // Verificar que la comunidad existe
        val comunidad = comunidadRepository.findComunidadByUrl(comunidadUrl)
            .orElseThrow { NotFoundException("Comunidad no encontrada") }

        // Verificar que el usuario a eliminar existe
        usuarioRepository.findFirstByUsername(usuarioAEliminar)
            .orElseThrow { NotFoundException("Usuario a eliminar no encontrado") }

        // Verificar que el usuario solicitante existe
        usuarioRepository.findFirstByUsername(usuarioSolicitante)
            .orElseThrow { NotFoundException("Usuario solicitante no encontrado") }

        // Verificar si el usuario a eliminar pertenece a la comunidad
        val participacion = participantesComunidadRepository.findByUsernameAndComunidad(
            usuarioAEliminar, comunidadUrl
        ).orElseThrow { BadRequestException("El usuario a eliminar no pertenece a esta comunidad") }

        // Verificar si el usuario solicitante es creador o administrador
        val esCreador = comunidad.creador == usuarioSolicitante
        val esAdmin = comunidad.administradores?.contains(usuarioSolicitante) ?: false

        if (!esCreador && !esAdmin) {
            throw ForbiddenException("No tienes permisos para eliminar usuarios de esta comunidad")
        }

        // Verificar permisos según roles
        if (!esCreador && esAdmin) {
            val usuarioAEliminarEsCreador = comunidad.creador == usuarioAEliminar
            val usuarioAEliminarEsAdmin = comunidad.administradores?.contains(usuarioAEliminar) ?: false

            if (usuarioAEliminarEsCreador || usuarioAEliminarEsAdmin) {
                throw ForbiddenException("Los administradores no pueden eliminar al creador ni a otros administradores")
            }
        }

        // No permitir que el creador se elimine a sí mismo
        if (esCreador && usuarioAEliminar == usuarioSolicitante) {
            throw BadRequestException("El creador no puede abandonar la comunidad")
        }

        // NUEVO: Eliminar participaciones en actividades privadas de esta comunidad
        val actividadesComunidad = actividadesComunidadRepository.findByComunidad(comunidadUrl).orElse(emptyList())
        actividadesComunidad.forEach { actividadComunidad ->
            val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad).orElse(null)
            // Si la actividad es privada, eliminar la participación del usuario
            if (actividad != null && actividad.privada) {
                val participacionActividad = participantesActividadRepository.findByUsernameAndIdActividad(
                    usuarioAEliminar,
                    actividadComunidad.idActividad ?: ""
                )
                if (participacionActividad.isPresent) {
                    participantesActividadRepository.delete(participacionActividad.get())
                }
            }
        }

        // Eliminar al usuario de la comunidad
        participantesComunidadRepository.delete(participacion)

        return participantesComunidadDTO
    }

    fun cambiarCreadorComunidad(comunidadUrl: String, creadorActual: String, nuevoCreador: String): ComunidadDTO {
        // Verificar que la comunidad existe
        val comunidad = comunidadRepository.findComunidadByUrl(comunidadUrl).orElseThrow {
            throw NotFoundException("Comunidad con URL $comunidadUrl no encontrada")
        }

        // Verificar que el usuario actual es el creador
        if (comunidad.creador != creadorActual) {
            throw ForbiddenException("Solo el creador actual puede transferir la propiedad de la comunidad")
        }

        // Verificar que el nuevo creador existe
        if (!usuarioRepository.existsByUsername(nuevoCreador)) {
            throw NotFoundException("El usuario $nuevoCreador no existe")
        }

        // Verificar que el nuevo creador es miembro de la comunidad
        if (!participantesComunidadRepository.findByUsernameAndComunidad(nuevoCreador, comunidadUrl).isPresent) {
            throw BadRequestException("El usuario $nuevoCreador debe ser miembro de la comunidad para convertirse en creador")
        }

        // Verificar que no es el mismo usuario
        if (creadorActual == nuevoCreador) {
            throw BadRequestException("No puedes transferir la propiedad a ti mismo")
        }

        // Actualizar el creador
        comunidad.creador = nuevoCreador

        // Si el nuevo creador estaba en la lista de administradores, eliminarlo de ahí
        val nuevosAdministradores = comunidad.administradores?.toMutableList() ?: mutableListOf()
        nuevosAdministradores.remove(nuevoCreador)

        // Añadir al creador anterior como administrador si no está ya
        if (!nuevosAdministradores.contains(creadorActual)) {
            nuevosAdministradores.add(creadorActual)
        }

        comunidad.administradores = nuevosAdministradores

        // Guardar los cambios
        val comunidadActualizada = comunidadRepository.save(comunidad)

        // Retornar el DTO actualizado
        return ComunidadDTO(
            url = comunidadActualizada.url,
            nombre = comunidadActualizada.nombre,
            creador = comunidadActualizada.creador,
            intereses = comunidadActualizada.intereses,
            fotoCarruselIds = comunidadActualizada.fotoCarruselIds,
            fotoPerfilId = comunidadActualizada.fotoPerfilId,
            descripcion = comunidadActualizada.descripcion,
            fechaCreacion = comunidadActualizada.fechaCreacion,
            administradores = comunidadActualizada.administradores,
            privada = comunidadActualizada.privada,
            coordenadas = comunidadActualizada.coordenadas,
            codigoUnion = comunidadActualizada.codigoUnion
        )
    }

}
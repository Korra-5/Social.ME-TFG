package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.*
import com.example.socialme.repository.*
import com.example.socialme.utils.ContentValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
@Service
class ActividadService {

    @Autowired
    private lateinit var denunciaRepository: DenunciaRepository

    @Autowired
    private lateinit var notificacionRepository: NotificacionRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var actividadesComunidadRepository: ActividadesComunidadRepository

    @Autowired
    private lateinit var participantesActividadRepository: ParticipantesActividadRepository

    @Autowired
    private lateinit var actividadRepository: ActividadRepository

    @Autowired
    private lateinit var gridFSService: GridFSService


    fun unirseActividad(participantesActividadDTO: ParticipantesActividadDTO): ParticipantesActividadDTO {
        val actividad = actividadRepository.findActividadBy_id(participantesActividadDTO.actividadId)
            .orElseThrow { BadRequestException("Esta actividad no existe") }

        val usuario = usuarioRepository.findFirstByUsername(participantesActividadDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        if (usuario.roles == "ADMIN") {
            throw BadRequestException("Los administradores no pueden unirse a actividades")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(actividad.comunidad)
            .orElseThrow { BadRequestException("La comunidad de la actividad no existe") }

        if (comunidad.expulsadosUsername.contains(participantesActividadDTO.username)) {
            throw BadRequestException("Has sido expulsado de la comunidad de esta actividad y no puedes unirte")
        }

        if (actividad.fechaFinalizacion.before(Date())) {
            throw BadRequestException("No puedes unirte a una actividad que ya ha finalizado")
        }

        if (actividad.privada) {
            val participaEnComunidad = participantesComunidadRepository.findByUsernameAndComunidad(
                participantesActividadDTO.username,
                actividad.comunidad
            ).isPresent

            if (!participaEnComunidad) {
                throw BadRequestException("No puedes unirte a esta actividad privada sin pertenecer a la comunidad")
            }
        }

        if (participantesActividadRepository.findByUsernameAndIdActividad(participantesActividadDTO.username, participantesActividadDTO.actividadId).isPresent) {
            throw BadRequestException("Ya estás participando en esta actividad")
        }

        val participante = ParticipantesActividad(
            _id = null,
            username = participantesActividadDTO.username,
            idActividad = participantesActividadDTO.actividadId,
            fechaUnion = Date.from(Instant.now()),
            nombreActividad = participantesActividadDTO.nombreActividad
        )

        participantesActividadRepository.insert(participante)
        return participantesActividadDTO
    }

    fun crearActividad(actividadCreateDTO: ActividadCreateDTO): ActividadDTO {

        ContentValidator.validarContenidoInapropiado(
            actividadCreateDTO.nombre,
            actividadCreateDTO.descripcion
        )

        if (actividadCreateDTO.nombre.length > 40) {
            throw BadRequestException("El nombre de la actividad no puede superar los 40 caracteres")
        }
        if (actividadCreateDTO.descripcion.length > 600) {
            throw BadRequestException("La descrpicion no puede superar los 600 caracteres")
        }

        if (actividadCreateDTO.lugar.length > 40) {
            throw BadRequestException("El lugar no puede superar los 40 caracteres")
        }

        if (actividadCreateDTO.fechaInicio.after(actividadCreateDTO.fechaFinalizacion)) {
            throw BadRequestException("La fecha de inicio debe ser anterior a la fecha de finalización")
        }

        usuarioRepository.findFirstByUsername(actividadCreateDTO.creador).orElseThrow {
            throw NotFoundException("Este usuario no existe")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(actividadCreateDTO.comunidad).orElseThrow {
            NotFoundException("Esta comunidad no existe")
        }

        if (comunidad.creador != actividadCreateDTO.creador && !comunidad.administradores!!.contains(actividadCreateDTO.creador)) {
            throw BadRequestException("No tienes permisos para crear esta actividad")
        }

        val fotosCarruselIds =
            if (actividadCreateDTO.fotosCarruselBase64 != null && actividadCreateDTO.fotosCarruselBase64.isNotEmpty()) {
                actividadCreateDTO.fotosCarruselBase64.mapIndexed { index, base64 ->
                    gridFSService.storeFileFromBase64(
                        base64,
                        "activity_carousel_${actividadCreateDTO.nombre}_${index}_${Date().time}",
                        "image/jpeg",
                        mapOf(
                            "type" to "activityCarousel",
                            "activity" to actividadCreateDTO.nombre,
                            "position" to index.toString()
                        )
                    )
                }
            } else actividadCreateDTO.fotosCarruselIds ?: emptyList()

        val actividad = Actividad(
            _id = null,
            nombre = actividadCreateDTO.nombre,
            descripcion = actividadCreateDTO.descripcion,
            fotosCarruselIds = fotosCarruselIds,
            fechaInicio = actividadCreateDTO.fechaInicio,
            fechaFinalizacion = actividadCreateDTO.fechaFinalizacion,
            fechaCreacion = Date.from(Instant.now()),
            creador = actividadCreateDTO.creador,
            privada = actividadCreateDTO.privada,
            comunidad = actividadCreateDTO.comunidad,
            coordenadas = actividadCreateDTO.coordenadas,
            lugar = actividadCreateDTO.lugar,
        )

        val actividadInsertada = actividadRepository.insert(actividad)

        val actividadComunidad = ActividadesComunidad(
            _id = null,
            comunidad = actividadInsertada.comunidad,
            idActividad = actividadInsertada._id,
            nombreActividad = actividadInsertada.nombre
        )

        actividadesComunidadRepository.insert(actividadComunidad)

        return ActividadDTO(
            nombre = actividadInsertada.nombre,
            privada = actividadInsertada.privada,
            creador = actividadInsertada.creador,
            descripcion = actividadInsertada.descripcion,
            fotosCarruselIds = actividadInsertada.fotosCarruselIds,
            fechaFinalizacion = actividadInsertada.fechaFinalizacion,
            fechaInicio = actividadInsertada.fechaInicio,
            coordenadas = actividadInsertada.coordenadas,
            _id = actividadInsertada._id,
            lugar = actividadInsertada.lugar,
        )
    }

    fun modificarActividad(actividadUpdateDTO: ActividadUpdateDTO): ActividadDTO {

        ContentValidator.validarContenidoInapropiado(
            actividadUpdateDTO.nombre,
            actividadUpdateDTO.descripcion
        )

        val actividad = actividadRepository.findActividadBy_id(actividadUpdateDTO._id)
            .orElseThrow { NotFoundException("Esta actividad no existe") }

        if (actividadUpdateDTO.fechaInicio.after(actividadUpdateDTO.fechaFinalizacion)) {
            throw BadRequestException("La fecha de inicio debe ser anterior a la fecha de finalización")
        }


        if (actividadUpdateDTO.nombre.length > 40) {
            throw BadRequestException("El nombre de la actividad no puede superar los 40 caracteres")
        }
        if (actividadUpdateDTO.descripcion.length > 600) {
            throw BadRequestException("La descrpicion no puede superar los 600 caracteres")
        }

        if (actividadUpdateDTO.lugar.length > 40) {
            throw BadRequestException("El lugar no puede superar los 40 caracteres")
        }

        val nombreAntiguo = actividad.nombre
        val nombreNuevo = actividadUpdateDTO.nombre

        val nuevasFotosCarruselIds = if (actividadUpdateDTO.fotosCarruselBase64 != null && actividadUpdateDTO.fotosCarruselBase64.isNotEmpty()) {
            val fotosExistentes = actividadUpdateDTO.fotosCarruselIds ?: actividad.fotosCarruselIds

            val nuevasFotosIds = actividadUpdateDTO.fotosCarruselBase64.mapIndexedNotNull { index, base64 ->
                try {
                    gridFSService.storeFileFromBase64(
                        base64,
                        "activity_carousel_${nombreNuevo}_${System.currentTimeMillis()}_${index}",
                        "image/jpeg",
                        mapOf(
                            "type" to "activityCarousel",
                            "activity" to nombreNuevo,
                            "position" to (fotosExistentes.size + index).toString()
                        )
                    )
                } catch (e: Exception) {
                    println("Error storing activity carousel image: ${e.message}")
                    null
                }
            }

            fotosExistentes + nuevasFotosIds
        } else {
            actividadUpdateDTO.fotosCarruselIds ?: actividad.fotosCarruselIds
        }

        actividad.apply {
            nombre = nombreNuevo
            descripcion = actividadUpdateDTO.descripcion
            fotosCarruselIds = nuevasFotosCarruselIds
            fechaInicio = actividadUpdateDTO.fechaInicio
            fechaFinalizacion = actividadUpdateDTO.fechaFinalizacion
            coordenadas = actividadUpdateDTO.coordenadas ?: this.coordenadas
            lugar = actividadUpdateDTO.lugar ?: this.lugar
        }

        val actividadActualizada = actividadRepository.save(actividad)

        if (nombreAntiguo != nombreNuevo) {
            val participantes = participantesActividadRepository.findByidActividad(actividadUpdateDTO._id)
            participantes.forEach { participante ->
                participante.nombreActividad = nombreNuevo
                participantesActividadRepository.save(participante)
            }

            val actividadComunidad = actividadesComunidadRepository.findActividadesComunidadByIdActividad(actividadUpdateDTO._id).orElseThrow{
                throw NotFoundException("La actividad no existe")
            }

            actividadComunidad.nombreActividad = nombreNuevo
            actividadesComunidadRepository.save(actividadComunidad)

            val denunciasComoItem = denunciaRepository.findAll().filter {
                it.tipoItemDenunciado == "actividad" && it.nombreItemDenunciado == nombreAntiguo
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

            val notificacionesActividad = notificacionRepository.findAll().filter {
                it.entidadNombre == nombreAntiguo
            }
            notificacionesActividad.forEach { notificacion ->
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

        return ActividadDTO(
            nombre = nombreNuevo,
            descripcion = actividadUpdateDTO.descripcion,
            privada = actividad.privada,
            creador = actividad.creador,
            fechaFinalizacion = actividad.fechaFinalizacion,
            fechaInicio = actividad.fechaInicio,
            fotosCarruselIds = actividad.fotosCarruselIds,
            _id = actividad._id,
            coordenadas = actividad.coordenadas,
            lugar = actividad.lugar
        )
    }

    fun eliminarActividad(id: String): ActividadDTO {
        val actividad = actividadRepository.findActividadBy_id(id).orElseThrow {
            throw NotFoundException("Esta actividad no existe")
        }

        try {
            actividad.fotosCarruselIds.forEach { fileId ->
                gridFSService.deleteFile(fileId)
            }
        } catch (e: Exception) {
            println("Error deleting GridFS files: ${e.message}")
        }

        val actividadDTO = ActividadDTO(
            nombre = actividad.nombre,
            privada = actividad.privada,
            creador = actividad.creador,
            descripcion = actividad.descripcion,
            fotosCarruselIds = actividad.fotosCarruselIds,
            fechaFinalizacion = actividad.fechaFinalizacion,
            fechaInicio = actividad.fechaInicio,
            coordenadas = actividad.coordenadas,
            _id = actividad._id,
            lugar = actividad.lugar,
        )

        participantesActividadRepository.deleteByIdActividad(id)

        actividadesComunidadRepository.deleteByIdActividad(id)

        actividadRepository.delete(actividad)

        return actividadDTO
    }

    fun verActividadNoParticipaUsuarioFechaSuperior(username: String): List<ActividadDTO> {
        val participaciones = participantesComunidadRepository.findComunidadByUsername(username).orElseThrow {
            throw BadRequestException("Usuario no existe")
        }

        val actividadesNoInscritas = mutableListOf<ActividadDTO>()
        val fechaActual = Date()

        participaciones.forEach { comunidad ->
            val actividadesComunidad = actividadesComunidadRepository.findByComunidad(comunidad.comunidad)
                .orElse(null)

            actividadesComunidad.forEach { actividadComunidad ->
                val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad)
                    .orElse(null)

                val estaInscrito = participantesActividadRepository
                    .findByUsernameAndIdActividad(username, actividadComunidad.idActividad ?:"")
                    .isPresent

                val puedeVerActividad = if (actividad != null && actividad.privada) {
                    participantesComunidadRepository.findByUsernameAndComunidad(username, actividad.comunidad).isPresent
                } else {
                    true
                }

                if (!estaInscrito && actividad != null && puedeVerActividad && actividad.fechaInicio.after(fechaActual)) {
                    actividadesNoInscritas.add(
                        ActividadDTO(
                            nombre = actividad.nombre,
                            descripcion = actividad.descripcion,
                            privada = actividad.privada,
                            creador = actividad.creador,
                            fotosCarruselIds = actividad.fotosCarruselIds,
                            fechaFinalizacion = actividad.fechaFinalizacion,
                            fechaInicio = actividad.fechaInicio,
                            coordenadas= actividad.coordenadas,
                            lugar=actividad.lugar,
                            _id = actividad._id
                        )
                    )
                }
            }
        }

        return actividadesNoInscritas
    }

    fun verActividadNoParticipaUsuarioCualquierFecha(username: String): List<ActividadDTO> {
        val participaciones = participantesComunidadRepository.findComunidadByUsername(username).orElseThrow {
            throw BadRequestException("Usuario no existe")
        }

        val actividadesNoInscritas = mutableListOf<ActividadDTO>()

        participaciones.forEach { comunidad ->
            val actividadesComunidad = actividadesComunidadRepository.findByComunidad(comunidad.comunidad)
                .orElse(null)

            actividadesComunidad.forEach { actividadComunidad ->
                val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad)
                    .orElse(null)

                val estaInscrito = participantesActividadRepository
                    .findByUsernameAndIdActividad(username, actividadComunidad.idActividad ?:"")
                    .isPresent

                val puedeVerActividad = if (actividad != null && actividad.privada) {
                    participantesComunidadRepository.findByUsernameAndComunidad(username, actividad.comunidad).isPresent
                } else {
                    true
                }

                if (!estaInscrito && actividad != null && puedeVerActividad) {
                    actividadesNoInscritas.add(
                        ActividadDTO(
                            nombre = actividad.nombre,
                            descripcion = actividad.descripcion,
                            privada = actividad.privada,
                            creador = actividad.creador,
                            fotosCarruselIds = actividad.fotosCarruselIds,
                            fechaFinalizacion = actividad.fechaFinalizacion,
                            fechaInicio = actividad.fechaInicio,
                            coordenadas= actividad.coordenadas,
                            lugar=actividad.lugar,
                            _id = actividad._id
                        )
                    )
                }
            }
        }

        return actividadesNoInscritas
    }

    fun verActividadPorId(id:String): ActividadDTO {
        val actividad=actividadRepository.findActividadBy_id(id).orElseThrow{
            throw NotFoundException("Esta actividad no existe")
        }
        val actividadDTO=ActividadDTO(
            nombre = actividad.nombre,
            descripcion = actividad.descripcion,
            privada = actividad.privada,
            creador = actividad.creador,
            fechaFinalizacion = actividad.fechaFinalizacion,
            fechaInicio = actividad.fechaInicio,
            coordenadas= actividad.coordenadas,
            lugar=actividad.lugar,
            fotosCarruselIds = actividad.fotosCarruselIds,
            _id = actividad._id
        )
        return actividadDTO
    }


    private fun verificarDistancia(coordenadasActividad: Coordenadas?, coordenadasUser: Coordenadas?, distanciaKm: Float?): Boolean {
        if (coordenadasActividad == null || coordenadasUser == null || distanciaKm == null) {
            return true
        }

        val distanciaCalculada = GeoUtils.calcularDistancia(coordenadasUser, coordenadasActividad)

        return distanciaCalculada <= distanciaKm
    }

    fun salirActividad(participantesActividadDTO: ParticipantesActividadDTO):ParticipantesActividadDTO{
        val union = participantesActividadRepository.findByUsernameAndIdActividad(username = participantesActividadDTO.username, actividadId = participantesActividadDTO.actividadId).orElseThrow {
            throw NotFoundException("No te has unido a esta actividad")
        }
        participantesActividadRepository.delete(union)

        return participantesActividadDTO
    }

    fun booleanUsuarioApuntadoActividad(participantesActividadDTO: ParticipantesActividadDTO):Boolean{
        actividadRepository.findActividadBy_id(participantesActividadDTO.actividadId)
            .orElseThrow { BadRequestException("Esta actividad no existe") }

        if (usuarioRepository.findFirstByUsername(participantesActividadDTO.username).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        return participantesActividadRepository.findByUsernameAndIdActividad(participantesActividadDTO.username, participantesActividadDTO.actividadId).isPresent
    }
    fun verActividadesPublicasEnZonaFechaSuperior(
        username: String
    ): List<ActividadDTO> {
        val distancia=usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario no existe")
        }.radarDistancia.toFloat()

        val todasLasActividades = actividadRepository.findAll()

        val usuario = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { throw NotFoundException("Usuario no existe") }

        val coordenadasUser = usuario.coordenadas
        val interesesUser = usuario.intereses
        val fechaActual = Date()

        val actividadesDelUsuario = participantesActividadRepository.findByUsername(username)
            .map { it.idActividad }
            .toSet()

        val comunidadesDelUsuario = participantesComunidadRepository.findByUsername(username)
            .map { it.comunidad }
            .toSet()

        val actividadesComunidades = todasLasActividades.associateWith { actividad ->
            comunidadRepository.findById(actividad.comunidad).orElse(null)
        }

        return todasLasActividades
            .filter { !it.privada }
            .filter { actividad ->
                !actividadesDelUsuario.contains(actividad._id)
            }
            .filter { actividad ->
                if (actividad.privada) {
                    comunidadesDelUsuario.contains(actividad.comunidad)
                } else {
                    true
                }
            }
            .filter { actividad ->
                val comunidad = actividadesComunidades[actividad]
                comunidad?.expulsadosUsername?.contains(username) != true
            }
            .filter { actividad ->
                actividad.fechaInicio.after(fechaActual)
            }
            .filter { actividad ->
                verificarDistancia(actividad.coordenadas, coordenadasUser, distancia)
            }
            .map { actividad ->
                ActividadDTO(
                    _id = actividad._id,
                    nombre = actividad.nombre,
                    descripcion = actividad.descripcion,
                    privada = actividad.privada,
                    creador = actividad.creador,
                    fotosCarruselIds = actividad.fotosCarruselIds,
                    fechaFinalizacion = actividad.fechaFinalizacion,
                    fechaInicio = actividad.fechaInicio,
                    coordenadas = actividad.coordenadas,
                    lugar = actividad.lugar
                )
            }
            .sortedWith(compareByDescending<ActividadDTO> { actividadDTO ->
                val comunidadIntereses = actividadesComunidades[todasLasActividades.find { it._id == actividadDTO._id }]?.intereses ?: emptyList()
                comunidadIntereses.count { interes -> interesesUser.contains(interes) }
            }.thenByDescending {
                it.fechaInicio
            })
    }

    fun verActividadesPublicasEnZonaCualquierFecha(
        username: String
    ): List<ActividadDTO> {
        val distancia=usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario no existe")
        }.radarDistancia.toFloat()

        val todasLasActividades = actividadRepository.findAll()

        val usuario = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { throw NotFoundException("Usuario no existe") }

        val coordenadasUser = usuario.coordenadas
        val interesesUser = usuario.intereses

        val actividadesDelUsuario = participantesActividadRepository.findByUsername(username)
            .map { it.idActividad }
            .toSet()

        val comunidadesDelUsuario = participantesComunidadRepository.findByUsername(username)
            .map { it.comunidad }
            .toSet()

        val actividadesComunidades = todasLasActividades.associateWith { actividad ->
            comunidadRepository.findById(actividad.comunidad).orElse(null)
        }

        return todasLasActividades
            .filter { !it.privada }
            .filter { actividad ->
                !actividadesDelUsuario.contains(actividad._id)
            }
            .filter { actividad ->
                if (actividad.privada) {
                    comunidadesDelUsuario.contains(actividad.comunidad)
                } else {
                    true
                }
            }
            .filter { actividad ->
                val comunidad = actividadesComunidades[actividad]
                comunidad?.expulsadosUsername?.contains(username) != true
            }
            .filter { actividad ->
                verificarDistancia(actividad.coordenadas, coordenadasUser, distancia)
            }
            .map { actividad ->
                ActividadDTO(
                    _id = actividad._id,
                    nombre = actividad.nombre,
                    descripcion = actividad.descripcion,
                    privada = actividad.privada,
                    creador = actividad.creador,
                    fotosCarruselIds = actividad.fotosCarruselIds,
                    fechaFinalizacion = actividad.fechaFinalizacion,
                    fechaInicio = actividad.fechaInicio,
                    coordenadas = actividad.coordenadas,
                    lugar = actividad.lugar
                )
            }
            .sortedWith(compareByDescending<ActividadDTO> { actividadDTO ->
                val comunidadIntereses = actividadesComunidades[todasLasActividades.find { it._id == actividadDTO._id }]?.intereses ?: emptyList()
                comunidadIntereses.count { interes -> interesesUser.contains(interes) }
            }.thenByDescending {
                it.fechaInicio
            })
    }

    fun verActividadesPorComunidadFechaSuperior(comunidad: String, username: String): List<ActividadDTO> {
        val comunidadObj = comunidadRepository.findComunidadByUrl(comunidad).orElseThrow {
            throw NotFoundException("Esta comunidad no existe")
        }

        if (comunidadObj.expulsadosUsername.contains(username)) {
            return emptyList()
        }

        val actividadesComunidad = actividadesComunidadRepository.findByComunidad(comunidad)
            .orElse(emptyList())

        val actividadesDTO = mutableListOf<ActividadDTO>()
        val fechaActual = Date()

        actividadesComunidad.forEach { actividadComunidad ->
            val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad)
                .orElse(null)

            if (actividad != null && actividad.fechaInicio.after(fechaActual)) {
                actividadesDTO.add(
                    ActividadDTO(
                        nombre = actividad.nombre,
                        descripcion = actividad.descripcion,
                        privada = actividad.privada,
                        creador = actividad.creador,
                        fotosCarruselIds = actividad.fotosCarruselIds,
                        fechaFinalizacion = actividad.fechaFinalizacion,
                        fechaInicio = actividad.fechaInicio,
                        _id = actividad._id,
                        coordenadas= actividad.coordenadas,
                        lugar=actividad.lugar
                    )
                )
            }
        }

        return actividadesDTO
    }

    fun verActividadesPorComunidadCualquierFecha(comunidad: String, username: String): List<ActividadDTO> {
        val comunidadObj = comunidadRepository.findComunidadByUrl(comunidad).orElseThrow {
            throw NotFoundException("Esta comunidad no existe")
        }

        if (comunidadObj.expulsadosUsername.contains(username)) {
            return emptyList()
        }

        val actividadesComunidad = actividadesComunidadRepository.findByComunidad(comunidad)
            .orElse(emptyList())

        val actividadesDTO = mutableListOf<ActividadDTO>()

        actividadesComunidad.forEach { actividadComunidad ->
            val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad)
                .orElse(null)

            if (actividad != null) {
                actividadesDTO.add(
                    ActividadDTO(
                        nombre = actividad.nombre,
                        descripcion = actividad.descripcion,
                        privada = actividad.privada,
                        creador = actividad.creador,
                        fotosCarruselIds = actividad.fotosCarruselIds,
                        fechaFinalizacion = actividad.fechaFinalizacion,
                        fechaInicio = actividad.fechaInicio,
                        _id = actividad._id,
                        coordenadas= actividad.coordenadas,
                        lugar=actividad.lugar
                    )
                )
            }
        }

        return actividadesDTO
    }

    fun contarUsuariosEnUnaActividad(actividadId:String):Int{
        if (actividadRepository.findActividadBy_id(actividadId).isEmpty) {
            throw BadRequestException("Actividad no existe")
        }
        val participaciones=participantesActividadRepository.findByidActividad(actividadId)
        var usuarios:Int=0
        participaciones.forEach {
            usuarios++
        }
        return usuarios
    }

    fun verificarCreadorAdministradorActividad(username: String, id:String):Boolean{
        val actividadComunidad=actividadesComunidadRepository.findActividadesComunidadByIdActividad(id).orElseThrow {
            NotFoundException("Actividad no existe")
        }
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario no encontrado")
        }
        val comunidad=comunidadRepository.findComunidadByUrl(actividadComunidad.comunidad).orElseThrow {
            throw NotFoundException("Comunidad no encontrado")
        }

        return comunidad.creador == username || comunidad.administradores!!.contains(username)
    }
    fun verTodasActividadesPublicasFechaSuperior(username: String): List<ActividadDTO> {
        val todasLasActividades = actividadRepository.findAll()
        val fechaActual = Date()
        val todasLasComunidades = comunidadRepository.findAll()

        return todasLasActividades
            .filter { !it.privada }
            .filter { actividad ->
                actividad.fechaInicio.after(fechaActual)
            }
            .filter { actividad ->
                val comunidadCreadora = todasLasComunidades.find { it.creador == actividad.creador }
                comunidadCreadora?.expulsadosUsername?.contains(username) != true
            }
            .map { actividad ->
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

    fun verTodasActividadesPublicasCualquierFecha(username: String): List<ActividadDTO> {
        val todasLasActividades = actividadRepository.findAll()
        val todasLasComunidades = comunidadRepository.findAll()

        return todasLasActividades
            .filter { !it.privada }
            .filter { actividad ->
                val comunidadCreadora = todasLasComunidades.find { it.creador == actividad.creador }
                comunidadCreadora?.expulsadosUsername?.contains(username) != true
            }
            .map { actividad ->
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

    fun verComunidadPorActividad(IdActividad:String):ComunidadDTO{
        val comunidad=comunidadRepository.findComunidadByUrl(
            actividadesComunidadRepository.findActividadesComunidadByIdActividad(IdActividad).orElseThrow{
                throw NotFoundException("Esta actividad no existe")
            }.comunidad
        ).orElseThrow {
            NotFoundException("Comunidad no encontrado")
        }

        return ComunidadDTO(
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
            codigoUnion = comunidad.codigoUnion,
            expulsadosUsername = comunidad.expulsadosUsername

        )
    }
}
package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Actividad
import com.example.socialme.model.ActividadesComunidad
import com.example.socialme.model.Coordenadas
import com.example.socialme.model.ParticipantesActividad
import com.example.socialme.repository.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
@Service
class ActividadService {

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

    fun crearActividad(actividadCreateDTO: ActividadCreateDTO): ActividadDTO {

        if (actividadCreateDTO.nombre.length > 25) {
            throw BadRequestException("El nombre de la actividad no puede superar los 25 caracteres")
        }
        if (actividadCreateDTO.descripcion.length > 2000) {
            throw BadRequestException("La descrpicion no puede superar los 2000 caracteres")
        }

        // Verificar que la fecha de inicio sea anterior a la fecha de finalización
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

    // Add GridFS handling to eliminarActividad
    fun eliminarActividad(id: String): ActividadDTO {
        val actividad = actividadRepository.findActividadBy_id(id).orElseThrow {
            throw NotFoundException("Esta actividad no existe")
        }

        // Delete activity images from GridFS
        try {
            actividad.fotosCarruselIds.forEach { fileId ->
                gridFSService.deleteFile(fileId)
            }
        } catch (e: Exception) {
            // Log error but continue with deletion
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

        // Eliminar primero a todos los participantes de la actividad
        participantesActividadRepository.deleteByIdActividad(id)

        // Después eliminar la referencia en la comunidad
        actividadesComunidadRepository.deleteByIdActividad(id)

        // Finalmente eliminar la actividad
        actividadRepository.delete(actividad)

        return actividadDTO
    }

    // Update the modificarActividad method to handle GridFS
    fun modificarActividad(actividadUpdateDTO: ActividadUpdateDTO): ActividadDTO {
        // Buscar la actividad existente
        val actividad = actividadRepository.findActividadBy_id(actividadUpdateDTO._id)
            .orElseThrow { NotFoundException("Esta actividad no existe") }

        // Verificar que la fecha de inicio sea anterior a la fecha de finalización
        if (actividadUpdateDTO.fechaInicio.after(actividadUpdateDTO.fechaFinalizacion)) {
            throw BadRequestException("La fecha de inicio debe ser anterior a la fecha de finalización")
        }

        // Guardar el nombre antiguo para comparación
        val nombreAntiguo = actividad.nombre
        val nombreNuevo = actividadUpdateDTO.nombre

        // Process new carousel photos if they exist in base64 format
        val nuevasFotos =
            if (actividadUpdateDTO.fotosCarruselBase64 != null && actividadUpdateDTO.fotosCarruselBase64.isNotEmpty()) {
                actividadUpdateDTO.fotosCarruselBase64.mapIndexed { index, base64 ->
                    gridFSService.storeFileFromBase64(
                        base64,
                        "activity_carousel_${nombreNuevo}_${index}_${Date().time}",
                        "image/jpeg",
                        mapOf(
                            "type" to "activityCarousel",
                            "activity" to nombreNuevo,
                            "position" to index.toString()
                        )
                    )
                }
            } else actividadUpdateDTO.fotosCarruselIds ?: emptyList()

        // Delete old photos that are not in the new list
        val viejasFotos = actividad.fotosCarruselIds
        val fotosParaEliminar = viejasFotos.filter { !nuevasFotos.contains(it) }

        try {
            fotosParaEliminar.forEach { fileId ->
                gridFSService.deleteFile(fileId)
            }
        } catch (e: Exception) {
            println("Error deleting old GridFS files: ${e.message}")
        }

        // Actualizar los datos de la actividad
        actividad.apply {
            nombre = nombreNuevo
            descripcion = actividadUpdateDTO.descripcion
            fotosCarruselIds = nuevasFotos
            fechaInicio = actividadUpdateDTO.fechaInicio
            fechaFinalizacion = actividadUpdateDTO.fechaFinalizacion
            coordenadas= actividad.coordenadas
            lugar=actividad.lugar
        }

        // Guardar la actividad actualizada
        val actividadActualizada = actividadRepository.save(actividad)

        // Si el nombre ha cambiado, actualizar en otras colecciones
        if (nombreAntiguo != nombreNuevo) {
            // Actualizar en ParticipantesActividad
            val participantes = participantesActividadRepository.findByidActividad(actividadUpdateDTO._id)
            participantes.forEach { participante ->
                participante.nombreActividad = nombreNuevo
                participantesActividadRepository.save(participante)
            }

            // Actualizar en ActividadesComunidad
            val actividadComunidad = actividadesComunidadRepository.findActividadesComunidadByIdActividad(actividadUpdateDTO._id).orElseThrow{
                throw NotFoundException("La actividad no existe")
            }

            actividadComunidad.nombreActividad = nombreNuevo
            actividadesComunidadRepository.save(actividadComunidad)
        }



        // Devolver DTO de la actividad actualizada
        return ActividadDTO(
            nombre = nombreNuevo,
            descripcion = actividadUpdateDTO.descripcion,
            privada = actividad.privada,
            creador = actividad.creador,
            fechaFinalizacion = actividad.fechaFinalizacion,
            fechaInicio = actividad.fechaInicio,
            fotosCarruselIds = actividad.fotosCarruselIds,
            _id = actividad._id,
            coordenadas= actividad.coordenadas,
            lugar=actividad.lugar
        )
    }

    // Also update verActividadNoParticipaUsuario to use the new fotosCarruselIds field
    fun verActividadNoParticipaUsuario(username: String): List<ActividadDTO> {
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

                // Verificar si el usuario NO está inscrito en esta actividad
                val estaInscrito = participantesActividadRepository
                    .findByUsernameAndIdActividad(username, actividadComunidad.idActividad ?:"")
                    .isPresent

                // Solo añadir la actividad si el usuario NO está inscrito
                if (!estaInscrito && actividad != null) {
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
    /**
     * Devuelve todas las actividades públicas que están dentro del radio de distancia especificado
     * Si no se especifica distancia o coordenadas del usuario, devuelve todas las actividades públicas
     * Filtra las actividades en las que el usuario ya está inscrito
     */
    fun verActividadesPublicasEnZona(
        distancia: Float? = null,
        username: String
    ): List<ActividadDTO> {
        // Obtenemos todas las actividades
        val todasLasActividades = actividadRepository.findAll()

        // Obtenemos el usuario para acceder a sus coordenadas e intereses
        val usuario = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { throw NotFoundException("Usuario no existe") }

        val coordenadasUser = usuario.coordenadas
        val interesesUser = usuario.intereses

        // Obtenemos las actividades a las que el usuario ya está inscrito
        val actividadesDelUsuario = participantesActividadRepository.findByUsername(username)
            .map { it.idActividad }
            .toSet()

        // Crear un mapa que asocie cada actividad con su comunidad correspondiente
        // para no tener que buscar la comunidad múltiples veces
        val actividadesComunidades = todasLasActividades.associateWith { actividad ->
            comunidadRepository.findById(actividad.comunidad).orElse(null)
        }

        return todasLasActividades
            .filter { !it.privada } // Solo actividades públicas
            .filter { actividad ->
                // Filtrar aquellas a las que el usuario no esté unido ya
                !actividadesDelUsuario.contains(actividad._id)
            }
            .filter { actividad ->
                // Verificar la distancia
                verificarDistancia(actividad.coordenadas, coordenadasUser, distancia)
            }
            // Ya no filtramos por intereses para mostrar todas
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
                // Primera ordenación: por número de intereses coincidentes
                // Buscamos la comunidad asociada a esta actividad
                val comunidadIntereses = actividadesComunidades[todasLasActividades.find { it._id == actividadDTO._id }]?.intereses ?: emptyList()
                comunidadIntereses.count { interes -> interesesUser.contains(interes) }
            }.thenByDescending {
                // Segunda ordenación: por fecha de inicio (las más próximas primero)
                it.fechaInicio
            })
    }

    private fun verificarDistancia(coordenadasActividad: Coordenadas?, coordenadasUser: Coordenadas?, distanciaKm: Float?): Boolean {
        // Si falta algún parámetro necesario para el cálculo de distancia, devolvemos true para incluir la actividad
        if (coordenadasActividad == null || coordenadasUser == null || distanciaKm == null) {
            return true
        }

        // Calculamos la distancia entre las coordenadas del usuario y las de la actividad
        val distanciaCalculada = GeoUtils.calcularDistancia(coordenadasUser, coordenadasActividad)

        // Verificamos si la distancia calculada es menor o igual que la distancia especificada
        return distanciaCalculada <= distanciaKm
    }


    fun verActividadesPorUsername(username: String): List<ActividadDTO> {
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
                coordenadas= actividad.coordenadas,
                lugar=actividad.lugar
            )
        }
    }

    fun unirseActividad(participantesActividadDTO: ParticipantesActividadDTO) : ParticipantesActividadDTO {
        val actividad = actividadRepository.findActividadBy_id(participantesActividadDTO.actividadId)
            .orElseThrow { BadRequestException("Esta actividad no existe") }

        // Verificar que el usuario existe
        if (usuarioRepository.findFirstByUsername(participantesActividadDTO.username).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        // Verificar si el usuario ya está participando en esta actividad
        if (participantesActividadRepository.findByUsernameAndIdActividad(participantesActividadDTO.username, participantesActividadDTO.actividadId).isPresent) {
            throw BadRequestException("Ya estás participando en esta actividad")
        }

        // Si no existe la participación, se crea
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

        // Verificar que el usuario existe
        if (usuarioRepository.findFirstByUsername(participantesActividadDTO.username).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        return participantesActividadRepository.findByUsernameAndIdActividad(participantesActividadDTO.username, participantesActividadDTO.actividadId).isPresent
    }

    fun verActividadesPorComunidad(comunidad: String): List<ActividadDTO> {
        comunidadRepository.findComunidadByUrl(comunidad).orElseThrow {
            throw NotFoundException("Esta comunidad no existe")
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

    fun verTodasActividadesPublicas():List<ActividadDTO>{
        val todasLasActividades = actividadRepository.findAll()
        return todasLasActividades
            .filter { !it.privada }
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
}
package com.example.socialme.service

import com.example.socialme.dto.ActividadCreateDTO
import com.example.socialme.dto.ActividadDTO
import com.example.socialme.dto.ActividadUpdateDTO
import com.example.socialme.dto.ParticipantesActividadDTO
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Actividad
import com.example.socialme.model.ActividadesComunidad
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

        usuarioRepository.findFirstByUsername(actividadCreateDTO.creador).orElseThrow {
            throw NotFoundException("Este usuario no existe")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(actividadCreateDTO.comunidad).orElseThrow {
            NotFoundException("Esta comunidad no existe")
        }

        if (comunidad.creador != actividadCreateDTO.creador && !comunidad.administradores!!.contains(actividadCreateDTO.creador)) {
            throw BadRequestException("No tienes permisos para crear esta actividad")
        }

        val fotosCarruselIds = if (actividadCreateDTO.fotosCarruselBase64 != null && actividadCreateDTO.fotosCarruselBase64.isNotEmpty()) {
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
            lugar = actividadInsertada.lugar,
            _id = actividadInsertada._id
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
            lugar = actividad.lugar,
            _id = actividad._id
        )

        // Remove related documents
        participantesActividadRepository.deleteByIdActividad(id)
        actividadesComunidadRepository.deleteByIdActividad(id)
        actividadRepository.delete(actividad)

        return actividadDTO
    }

    // Update the modificarActividad method to handle GridFS
    fun modificarActividad(actividadUpdateDTO: ActividadUpdateDTO): ActividadDTO {
        // Buscar la actividad existente
        val actividad = actividadRepository.findActividadBy_id(actividadUpdateDTO._id)
            .orElseThrow { NotFoundException("Esta actividad no existe") }

        // Guardar el nombre antiguo para comparación
        val nombreAntiguo = actividad.nombre
        val nombreNuevo = actividadUpdateDTO.nombre

        // Process new carousel photos if they exist in base64 format
        val nuevasFotos = if (actividadUpdateDTO.fotosCarruselBase64 != null && actividadUpdateDTO.fotosCarruselBase64.isNotEmpty()) {
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
            lugar = actividadUpdateDTO.lugar
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
            val actividadesComunidad = actividadesComunidadRepository.findByIdActividad(actividadUpdateDTO._id)
            actividadesComunidad.forEach { actividadComunidad ->
                actividadComunidad.nombreActividad = nombreNuevo
                actividadesComunidadRepository.save(actividadComunidad)
            }
        }

        // Devolver DTO de la actividad actualizada
        return ActividadDTO(
            nombre = nombreNuevo,
            descripcion = actividadUpdateDTO.descripcion,
            privada = actividad.privada,
            creador = actividad.creador,
            fechaFinalizacion = actividad.fechaFinalizacion,
            fechaInicio = actividad.fechaInicio,
            lugar = actividad.lugar,
            fotosCarruselIds = actividad.fotosCarruselIds,
            _id = actividad._id
        )
    }

    // Also update verActividadPorComunidad to use the new fotosCarruselIds field
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
                            lugar = actividad.lugar,
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
            lugar = actividad.lugar,
            fotosCarruselIds = actividad.fotosCarruselIds,
            _id = actividad._id
        )
        return actividadDTO
    }

    fun verActividadesPublicas(): List<ActividadDTO> {
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
                    lugar = actividad.lugar,
                    _id = actividad._id
                )
            }
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
                lugar = actividad.lugar,
                _id = actividad._id
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
    fun salirActividad(id:String):ParticipantesActividadDTO{
        val union = participantesActividadRepository.findBy_id(id).orElseThrow {
            throw NotFoundException("No te has unido a esta actividad")
        }
        participantesActividadRepository.delete(union)

        val participantesActividadDTO= ParticipantesActividadDTO(
            actividadId =union.idActividad,
            username = union.username,
            nombreActividad = union.nombreActividad,

            )

        return participantesActividadDTO
    }

}
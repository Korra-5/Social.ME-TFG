package com.example.socialme.service

import com.es.aplicacion.error.exception.BadRequestException
import com.es.aplicacion.error.exception.NotFoundException
import com.example.socialme.dto.ActividadCreateDTO
import com.example.socialme.dto.ActividadDTO
import com.example.socialme.dto.ActividadUpdateDTO
import com.example.socialme.dto.ParticipantesActividadDTO
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
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var actividadesComunidadRepository: ActividadesComunidadRepository

    @Autowired
    private lateinit var participantesActividadRepository: ParticipantesActividadRepository

    @Autowired
    private lateinit var actividadRepository: ActividadRepository

    fun crearActividad(actividadCreateDTO: ActividadCreateDTO) : ActividadDTO {

        if (actividadCreateDTO.nombre.length>25){
            throw BadRequestException("El nombre de la actividad no puede superar los 25 caracteres")
        }
        if (actividadCreateDTO.descripcion.length>2000){
            throw BadRequestException("La descrpicion no puede superar los 2000 caracteres")
        }

        val actividad= Actividad(
            _id=null,
            nombre = actividadCreateDTO.nombre,
            descripcion = actividadCreateDTO.descripcion,
            fotosCarrusel = actividadCreateDTO.fotosCarrusel,
            fechaInicio = actividadCreateDTO.fechaInicio,
            fechaFinalizacion = actividadCreateDTO.fechaFinalizacion,
            fechaCreacion = Date.from(Instant.now()),
            creador = actividadCreateDTO.creador,
            privada = actividadCreateDTO.privada,
            comunidad=actividadCreateDTO.comunidad
        )
        if (usuarioRepository.findByUsername(actividadCreateDTO.creador).isPresent){
            val comunidad=comunidadRepository.findComunidadByUrl(actividadCreateDTO.comunidad).orElseThrow { NotFoundException("Esta comunidad no existe") }
            if (comunidad.creador==actividadCreateDTO.creador|| comunidad.administradores!!.contains(actividadCreateDTO.creador)){
                actividadRepository.insert(actividad)
            }else{
                throw BadRequestException("No tienes permisos para crear esta actividad")
            }
        }else{
            throw BadRequestException("Este usuario no existe")
        }
        val actividadDTO = ActividadDTO(
            nombre=actividadCreateDTO.nombre,
            privada = actividadCreateDTO.privada,
            creador = actividadCreateDTO.creador,
            descripcion = actividadCreateDTO.descripcion,
        )

        return actividadDTO
    }

    fun unirseActividad(participantesActividadDTO: ParticipantesActividadDTO) : ParticipantesActividadDTO {
            val actividad = actividadRepository.findActividadBy_id(participantesActividadDTO.actividadId)
                .orElseThrow { BadRequestException("Esta actividad no existe") }

            // Verificar que el usuario existe
            if (usuarioRepository.findByUsername(participantesActividadDTO.username).isEmpty) {
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

    fun eliminarActividad(id:String):ActividadDTO{
        val actividad = actividadRepository.findActividadBy_id(id).orElseThrow{
            throw NotFoundException("Esta actividad no existe")
        }
        val actividadDTO = ActividadDTO(
            nombre = actividad.nombre,
            privada = actividad.privada,
            creador = actividad.creador,
            descripcion = actividad.descripcion,
        )
        actividadRepository.delete(actividad)
        return actividadDTO
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

    fun verActividadPorComunidad(comunidad: String): List<ActividadDTO> {
        val actividadesComunidad: List<ActividadesComunidad> = actividadesComunidadRepository.findByComunidad(comunidad)
            .orElseThrow {
                throw BadRequestException("No existen actividades para esta comunidad")
            }

        return actividadesComunidad.mapNotNull { actividadComunidad ->
            val actividad = actividadRepository.findActividadBy_id(actividadComunidad.idActividad)
                .orElse(null)

            actividad?.let {
                ActividadDTO(
                    nombre = it.nombre,
                    descripcion = it.descripcion,
                    privada = it.privada,
                    creador = it.creador
                )
            }
        }
}
    fun verActividadesPublicas():List<ActividadDTO>{
            val todasLasActividades = actividadRepository.findAll()

            return todasLasActividades
                .filter { it.privada }
                .map { actividad ->
                    ActividadDTO(
                        nombre = actividad.nombre,
                        descripcion = actividad.descripcion,
                        privada = actividad.privada,
                        creador = actividad.creador
                    )
                }

    }
    fun modificarActividad(actividadUpdateDTO: ActividadUpdateDTO): ActividadDTO {
        // Buscar la actividad existente
        val actividad = actividadRepository.findActividadBy_id(actividadUpdateDTO._id)
            .orElseThrow { NotFoundException("Esta actividad no existe") }

        // Guardar el nombre antiguo para comparación
        val nombreAntiguo = actividad.nombre
        val nombreNuevo = actividadUpdateDTO.nombre

        // Actualizar los datos de la actividad
        actividad.apply {
            nombre = nombreNuevo
            descripcion = actividadUpdateDTO.descripcion
            fotosCarrusel = actividadUpdateDTO.fotosCarrusel
            fechaInicio = actividadUpdateDTO.fechaInicio
            fechaFinalizacion = actividadUpdateDTO.fechaFinalizacion
        }

        // Guardar la actividad actualizada
        val actividadActualizada = actividadRepository.save(actividad)

        // Si el nombre ha cambiado, actualizar en otras colecciones
        if (nombreAntiguo != nombreNuevo) {
            // Actualizar en ParticipantesActividad
            val participantes:List<ParticipantesActividad> = participantesActividadRepository.findByidActividad(actividadUpdateDTO._id)
            participantes.forEach { participante ->
                participante.nombreActividad = nombreNuevo
                participantesActividadRepository.save(participante)
            }

            // Actualizar en ActividadesComunidad
            val actividadesComunidad:List<ActividadesComunidad> = actividadesComunidadRepository.findByIdActividad(actividadUpdateDTO._id)
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
            creador = actividad.creador
        )
    }
    }

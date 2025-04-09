package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Comunidad
import com.example.socialme.model.ParticipantesComunidad
import com.example.socialme.repository.ActividadesComunidadRepository
import com.example.socialme.repository.ComunidadRepository
import com.example.socialme.repository.ParticipantesComunidadRepository
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class ComunidadService {

    @Autowired
    private lateinit var actividadesComunidadRepository: ActividadesComunidadRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var gridFSService: GridFSService

    fun crearComunidad(comunidadCreateDTO: ComunidadCreateDTO): ComunidadDTO {
        if (comunidadRepository.findComunidadByUrl(comunidadCreateDTO.url).isPresent) {
            throw BadRequestException("Comunidad existente")
        }

        //Sustituye por guiones los espacios para que las url sean más accesibles
        val formattedUrl = comunidadCreateDTO.url.trim().split(Regex("\\s+")).joinToString("-")

        if (comunidadCreateDTO.nombre.length > 40) {
            throw BadRequestException("Este nombre es demasiado largo, pruebe con uno inferior a 40 caracteres")
        }
        if (comunidadCreateDTO.descripcion.length > 5000) {
            throw BadRequestException("Lo sentimos, la descripción no puede superar los 5000 caracteres")
        }

        //Verifica que los intereses no tengan espacios ni superen los 25 caracteres
        validateAndReplaceSpaces(listOf(formattedUrl))

        if (!usuarioRepository.existsByUsername(comunidadCreateDTO.creador)) {
            throw NotFoundException("Usuario no encontrado")
        }

        // Handle profile photo upload to GridFS
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
                url = formattedUrl,
                comunidadGlobal = comunidadCreateDTO.comunidadGlobal,
                privada = comunidadCreateDTO.privada
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
            comunidadGlobal = comunidadCreateDTO.comunidadGlobal,
            creador = comunidadCreateDTO.creador,
            intereses = comunidadCreateDTO.intereses,
            fotoCarruselIds = null,
            fotoPerfilId = fotoPerfilId,
            descripcion = comunidadCreateDTO.descripcion,
            fechaCreacion = Date.from(Instant.now()),
            administradores = null,
            privada = comunidadCreateDTO.privada
        )
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
            comunidadGlobal = comunidad.comunidadGlobal,
            privada = comunidad.privada,
            url=comunidad.url
        )
    }

    fun unirseComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("La comunidad no existe") }

        usuarioRepository.findFirstByUsername(participantesComunidadDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

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

    fun eliminarComunidad(id: String): ComunidadDTO {
        val comunidad = comunidadRepository.findComunidadBy_id(id).orElseThrow { BadRequestException("Esta comunidad no existe") }

        val comunidadDto = ComunidadDTO(
            url = comunidad.url,
            nombre = comunidad.nombre,
            comunidadGlobal = comunidad.comunidadGlobal,
            creador = comunidad.creador,
            intereses = comunidad.intereses,
            fotoCarruselIds = comunidad.fotoCarruselIds,
            fotoPerfilId = comunidad.fotoPerfilId,
            descripcion = comunidad.descripcion,
            fechaCreacion = comunidad.fechaCreacion,
            administradores = comunidad.administradores,
            privada = comunidad.privada
        )

        // Delete images from GridFS
        try {
            gridFSService.deleteFile(comunidad.fotoPerfilId)
            comunidad.fotoCarruselIds?.forEach { fileId ->
                gridFSService.deleteFile(fileId)
            }
        } catch (e: Exception) {
            // Log error but continue with deletion
            println("Error deleting GridFS files: ${e.message}")
        }

        comunidadRepository.delete(comunidad)
        participantesComunidadRepository.deleteByComunidad(comunidad.url)
        actividadesComunidadRepository.deleteByComunidad(comunidad.url)

        return comunidadDto
    }

    fun getComunidadPorUsername(username: String): List<Comunidad> {
        val participaciones = participantesComunidadRepository.findByUsername(username)

        if (participaciones.isEmpty()) {
            throw BadRequestException("No existe el usuario o no pertenece a ninguna comunidad")
        }

        return participaciones.mapNotNull { participante ->
            val url = participante.comunidad
            comunidadRepository.findComunidadByUrl(url)
                .orElseThrow { BadRequestException("La comunidad con URL '$url' no existe") }
        }
    }

    fun verTodasComunidades(): MutableList<Comunidad> {
        return comunidadRepository.findAll()
    }

    fun modificarComunidad(comunidad: ComunidadUpdateDTO): ComunidadDTO {
        val comunidadExistente = comunidadRepository.findComunidadByUrl(comunidad.url)
            .orElseThrow { BadRequestException("Comunidad no existente") }

        val urlAntigua = comunidadExistente.url
        val urlNueva = comunidad.url

        // Handle profile photo update
        var fotoPerfilId = if (comunidad.fotoPerfilBase64 != null) {
            // If a new photo was provided in base64, store it and get new ID
            val newPhotoId = gridFSService.storeFileFromBase64(
                comunidad.fotoPerfilBase64,
                "community_profile_${urlNueva}_${Date().time}",
                "image/jpeg",
                mapOf("type" to "profilePhoto", "community" to urlNueva)
            )

            // Delete old photo if exists
            try {
                gridFSService.deleteFile(comunidadExistente.fotoPerfilId)
            } catch (e: Exception) {
                // Log error but continue
                println("Error deleting old profile photo: ${e.message}")
            }

            newPhotoId
        } else comunidad.fotoPerfilId ?: comunidadExistente.fotoPerfilId

        // Handle carousel photos update
        var fotoCarruselIds = if (comunidad.fotoCarruselBase64 != null && comunidad.fotoCarruselBase64.isNotEmpty()) {
            // If new carousel photos were provided in base64, store them
            val newCarouselIds = comunidad.fotoCarruselBase64.mapIndexed { index, base64 ->
                gridFSService.storeFileFromBase64(
                    base64,
                    "community_carousel_${urlNueva}_${index}_${Date().time}",
                    "image/jpeg",
                    mapOf("type" to "carouselPhoto", "community" to urlNueva, "position" to index.toString())
                )
            }

            // Delete old carousel photos if exist
            comunidadExistente.fotoCarruselIds?.forEach { oldId ->
                try {
                    gridFSService.deleteFile(oldId)
                } catch (e: Exception) {
                    // Log error but continue
                    println("Error deleting old carousel photo: ${e.message}")
                }
            }

            newCarouselIds
        } else comunidad.fotoCarruselIds ?: comunidadExistente.fotoCarruselIds

        comunidadExistente.apply {
            url = comunidad.url
            nombre = comunidad.nombre
            descripcion = comunidad.descripcion
            intereses = comunidad.intereses
            administradores = comunidad.administradores
            fotoPerfilId = fotoPerfilId
            fotoCarruselIds = fotoCarruselIds
        }

        val comunidadActualizada = comunidadRepository.save(comunidadExistente)

        if (urlAntigua != urlNueva) {
            val participantes = participantesComunidadRepository.findParticipantesByComunidad(urlAntigua)

            participantes.forEach { participante ->
                participante.comunidad = urlNueva
                participantesComunidadRepository.save(participante)
            }
        }

        return ComunidadDTO(
            url = comunidadActualizada.url,
            nombre = comunidadActualizada.nombre,
            comunidadGlobal = comunidadActualizada.comunidadGlobal,
            creador = comunidadActualizada.creador,
            intereses = comunidadActualizada.intereses,
            fotoCarruselIds = comunidadActualizada.fotoCarruselIds,
            fotoPerfilId = comunidadActualizada.fotoPerfilId,
            descripcion = comunidadActualizada.descripcion,
            fechaCreacion = comunidadActualizada.fechaCreacion,
            administradores = comunidadActualizada.administradores,
            privada = comunidadActualizada.privada
        )
    }

    fun salirComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        val union = participantesComunidadRepository.findByUsernameAndComunidad(username = participantesComunidadDTO.username, comunidad = participantesComunidadDTO.comunidad).orElseThrow {
            throw BadRequestException("No estás en esta comunidad")
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
}
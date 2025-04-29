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
                privada = comunidadCreateDTO.privada,
                coordenadas = comunidadCreateDTO.coordenadas
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
            privada = comunidadCreateDTO.privada,
            coordenadas = comunidadCreateDTO.coordenadas
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
            url =comunidad.url,
            coordenadas = comunidad.coordenadas
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

    fun eliminarComunidad(url: String): ComunidadDTO {
        val comunidad = comunidadRepository.findComunidadByUrl(url).orElseThrow { BadRequestException("Esta comunidad no existe") }

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
            privada = comunidad.privada,
            coordenadas = comunidad.coordenadas
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

    fun verTodasComunidades(): List<ComunidadDTO> {
        val todasLasComunidades = comunidadRepository.findAll()

        return todasLasComunidades
            .filter { !it.privada }
            .map {  comunidad ->
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
                comunidadGlobal = comunidad.comunidadGlobal,
                privada = comunidad.privada,
                coordenadas = comunidad.coordenadas
            )
        }
    }

    fun modificarComunidad(comunidadUpdateDTO: ComunidadUpdateDTO): ComunidadDTO {
        // Buscar la comunidad existente usando currentURL
        val comunidadExistente = comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.currentURL).orElseThrow {
            throw NotFoundException("Comunidad con URL ${comunidadUpdateDTO.currentURL} no encontrada")
        }

        // Si se está cambiando la URL, validar que la nueva no exista ya
        if (comunidadUpdateDTO.newUrl != comunidadUpdateDTO.currentURL) {
            comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.newUrl).ifPresent {
                throw BadRequestException("Ya existe una comunidad con la URL ${comunidadUpdateDTO.newUrl}, prueba con otra URL")
            }
        }

        // Guardar la antigua URL para actualizar referencias
        val urlAntigua = comunidadExistente.url

        // Procesar la foto de perfil si se proporciona en Base64
        val nuevaFotoPerfilId = if (!comunidadUpdateDTO.fotoPerfilBase64.isNullOrBlank()) {
            // Intentar eliminar la foto de perfil antigua, si existe
            try {
                if (!comunidadExistente.fotoPerfilId.isNullOrBlank()) {
                    gridFSService.deleteFile(comunidadExistente.fotoPerfilId)
                }
            } catch (e: Exception) {
                println("Error al eliminar la foto de perfil antigua: ${e.message}")
            }

            // Guardar la nueva foto de perfil
            val urlParaFoto = comunidadUpdateDTO.newUrl
            gridFSService.storeFileFromBase64(
                comunidadUpdateDTO.fotoPerfilBase64,
                "community_profile_${urlParaFoto}_${Date().time}",
                "image/jpeg",
                mapOf(
                    "type" to "profilePhoto",
                    "community" to urlParaFoto
                )
            ) ?: ""
        } else if (comunidadUpdateDTO.fotoPerfilId != null) {
            // Si se proporciona explícitamente un ID de foto
            comunidadUpdateDTO.fotoPerfilId
        } else {
            // Mantener la foto de perfil existente
            comunidadExistente.fotoPerfilId
        }

        // Procesar las fotos del carrusel si se proporcionan en Base64
        val nuevasFotosCarruselIds = if (comunidadUpdateDTO.fotoCarruselBase64 != null && comunidadUpdateDTO.fotoCarruselBase64.isNotEmpty()) {
            // Intentar eliminar las fotos de carrusel antiguas, si existen
            try {
                comunidadExistente.fotoCarruselIds?.forEach { fotoId ->
                    if (!fotoId.isNullOrBlank()) {
                        gridFSService.deleteFile(fotoId)
                    }
                }
            } catch (e: Exception) {
                println("Error al eliminar fotos de carrusel antiguas: ${e.message}")
            }

            // Guardar las nuevas fotos de carrusel
            val urlParaFotos = comunidadUpdateDTO.newUrl
            comunidadUpdateDTO.fotoCarruselBase64.mapIndexed { index, base64 ->
                gridFSService.storeFileFromBase64(
                    base64,
                    "community_carousel_${urlParaFotos}_${index}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "carouselPhoto",
                        "community" to urlParaFotos,
                        "position" to index.toString()
                    )
                ) ?: ""
            }
        } else {
            // Mantener las fotos de carrusel existentes
            comunidadUpdateDTO.fotoCarruselIds ?: comunidadExistente.fotoCarruselIds
        }

        // Actualizar la información de la comunidad
        comunidadExistente.apply {
            url = comunidadUpdateDTO.newUrl
            nombre = comunidadUpdateDTO.nombre
            descripcion = comunidadUpdateDTO.descripcion
            intereses = comunidadUpdateDTO.intereses
            administradores = comunidadUpdateDTO.administradores
            fotoPerfilId = nuevaFotoPerfilId
            fotoCarruselIds = nuevasFotosCarruselIds
        }

        val comunidadActualizada = comunidadRepository.save(comunidadExistente)

        // Si se ha cambiado la URL, actualizar referencias en otras colecciones
        if (urlAntigua != comunidadActualizada.url) {
            // Actualizar referencias en ActividadesComunidad
            val actividades = actividadesComunidadRepository.findByComunidad(urlAntigua).orElseThrow {
                NotFoundException("Actividades no encontradas")
            }
            actividades.forEach { actividad ->
                actividad.comunidad = comunidadActualizada.url
                actividadesComunidadRepository.save(actividad)
            }

            // Actualizar referencias en ParticipantesComunidad
            val participantes = participantesComunidadRepository.findByComunidad(urlAntigua)
            participantes.forEach { participante ->
                participante.comunidad = comunidadActualizada.url
                participantesComunidadRepository.save(participante)
            }
        }

        // Retornar el DTO actualizado
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
            privada = comunidadActualizada.privada,
            coordenadas = comunidadActualizada.coordenadas,
        )
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
}
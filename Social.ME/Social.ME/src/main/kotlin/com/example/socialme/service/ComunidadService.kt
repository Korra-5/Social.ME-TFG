package com.example.socialme.service

import com.example.socialme.dto.ComunidadCreateDTO
import com.example.socialme.dto.ComunidadDTO
import com.example.socialme.dto.ComunidadUpdateDTO
import com.example.socialme.dto.ParticipantesComunidadDTO
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Comunidad
import com.example.socialme.model.ParticipantesComunidad
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
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    fun crearComunidad(comunidadCreateDTO: ComunidadCreateDTO):ComunidadDTO {
        if (comunidadRepository.findComunidadByUrl(comunidadCreateDTO.url).isPresent) {
            throw BadRequestException("Comunidad existente")
        }

        //Sustituye por guiones los espacios para que las url sean mas accesibles
        comunidadCreateDTO.url.trim().split(Regex("\\s+")).joinToString("-")

        if (comunidadCreateDTO.nombre.length>40){
            throw BadRequestException("Este nombre es demasiado largo, pruebe con uno inferior a 40 caracteres")
        }
        if (comunidadCreateDTO.descripcion.length>5000){
            throw BadRequestException("Lo sentimos, la descripción no puede superar los 5000 caracteres")
        }

        //Verifica que los intereses no tengan espacios ni superen los 25 caracteres
        validateAndReplaceSpaces(listOf(comunidadCreateDTO.url))

        if (!usuarioRepository.existsByUsername(comunidadCreateDTO.creador)) {
            throw NotFoundException("Usuario no encontrado")
        }

        val comunidad: Comunidad =
            Comunidad(
                _id=null,
                nombre = comunidadCreateDTO.nombre,
                descripcion = comunidadCreateDTO.descripcion,
                creador = comunidadCreateDTO.creador,
                intereses = comunidadCreateDTO.intereses,
                fotoPerfil = comunidadCreateDTO.fotoPerfil,
                fotoCarrusel = null,
                administradores = null,
                fechaCreacion = Date.from(Instant.now()),
                url=comunidadCreateDTO.url,
                comunidadGlobal = comunidadCreateDTO.comunidadGlobal
            )

        val participantesComunidad=ParticipantesComunidad(
            comunidad=comunidad.url,
            username = comunidad.creador,
            fechaUnion = Date.from(Instant.now()),
            _id = null
        )

        comunidadRepository.insert(comunidad)
        participantesComunidadRepository.insert(participantesComunidad)

        return ComunidadDTO(
            url=comunidadCreateDTO.url,
            nombre=comunidadCreateDTO.nombre,
            comunidadGlobal=comunidadCreateDTO.comunidadGlobal,
            creador=comunidadCreateDTO.creador,
            intereses=comunidadCreateDTO.intereses,
            fotoCarrusel = null,
            fotoPerfil = comunidadCreateDTO.fotoPerfil,
            descripcion=comunidadCreateDTO.descripcion,
            fechaCreacion=Date.from(Instant.now()),
            administradores=null
        )
    }

    fun unirseComunidad(participantesComunidadDTO: ParticipantesComunidadDTO):ParticipantesComunidadDTO{
        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
                .orElseThrow { BadRequestException("La comunidad no existe") }

        usuarioRepository.findByUsername(participantesComunidadDTO.username)
                .orElseThrow { NotFoundException("Usuario no encontrado") }

            if (participantesComunidadRepository.findByUsernameAndComunidad(
                    participantesComunidadDTO.username,
                    participantesComunidadDTO.comunidad).isPresent) {
                throw BadRequestException("El usuario ya está unido a esta comunidad")
            }

            val union=ParticipantesComunidad(
                _id=null,
                comunidad=participantesComunidadDTO.comunidad,
                username = participantesComunidadDTO.username,
                fechaUnion = Date.from(Instant.now())
            )

            participantesComunidadRepository.insert(union)

        return participantesComunidadDTO
        }

    fun eliminarComunidad(id:String):ComunidadDTO{

        val comunidad=comunidadRepository.findComunidadBy_id(id).orElseThrow{BadRequestException("Esta comunidad no existe")}

        val comunidadDto=ComunidadDTO(
            url=comunidad.url,
            nombre=comunidad.nombre,
            comunidadGlobal=comunidad.comunidadGlobal,
            creador=comunidad.creador,
            intereses=comunidad.intereses,
            fotoCarrusel = comunidad.fotoCarrusel,
            fotoPerfil = comunidad.fotoPerfil,
            descripcion=comunidad.descripcion,
            fechaCreacion=Date.from(Instant.now()),
            administradores=comunidad.administradores,
        )
        comunidadRepository.delete(comunidad)


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
        val comunidades=comunidadRepository.findAll()
        return comunidades
    }

    fun modificarComunidad(comunidad: ComunidadUpdateDTO): ComunidadDTO {
        val comunidadExistente = comunidadRepository.findComunidadByUrl(comunidad.url)
            .orElseThrow { BadRequestException("Comunidad no existente") }

        val urlAntigua = comunidadExistente.url
        val urlNueva = comunidad.url

        comunidadExistente.apply {
            url = comunidad.url
            nombre = comunidad.nombre
            descripcion = comunidad.descripcion
            intereses = comunidad.intereses
            administradores = comunidad.administradores
            fotoPerfil = comunidad.fotoPerfil
            fotoCarrusel = comunidad.fotoCarrusel
        }

        val comunidadActualizada = comunidadRepository.save(comunidadExistente)

        if (urlAntigua != urlNueva) {
            val participantes = participantesComunidadRepository.findParticipantesByComunidad(urlAntigua)

            participantes.forEach { participante ->
                participante.comunidad = urlNueva
                participantesComunidadRepository.save(participante)
            }
        }

        val comunidadDTO=ComunidadDTO(
            url=comunidadActualizada.url,
            nombre=comunidadActualizada.nombre,
            comunidadGlobal=comunidadActualizada.comunidadGlobal,
            creador=comunidadActualizada.creador,
            intereses=comunidadActualizada.intereses,
            fotoCarrusel = comunidadActualizada.fotoCarrusel,
            fotoPerfil = comunidadActualizada.fotoPerfil,
            descripcion=comunidadActualizada.descripcion,
            fechaCreacion=comunidadActualizada.fechaCreacion,
            administradores=comunidadActualizada.administradores,
        )

        return comunidadDTO
    }

    fun salirComunidad(id:String): ParticipantesComunidadDTO {
        val union=participantesComunidadRepository.findBy_id(id).orElseThrow {
            throw BadRequestException("No estas en esta comunidad")
        }
        participantesComunidadRepository.delete(union)

        val participantesComunidadDTO=ParticipantesComunidadDTO(
            comunidad=union.comunidad,
            username = union.username
        )

        return participantesComunidadDTO
    }

    fun validateAndReplaceSpaces(inputList: List<String>): List<String> {
        return inputList.map {
            val trimmed = it.trim()
            if (trimmed.length > 25) throw BadRequestException("Los intereses no pueden exceder los 25 caracteres")
            if (trimmed.contains(" ")) throw BadRequestException("Con el fin de facilitar su uso, los interes ")
            trimmed
        }
    }
}


package com.example.socialme.service

import com.es.aplicacion.error.exception.BadRequestException
import com.es.aplicacion.error.exception.NotFoundException
import com.example.socialme.dto.ComunidadCreateDTO
import com.example.socialme.dto.ComunidadDTO
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

        if (usuarioRepository.findByUsername(comunidadCreateDTO.creador).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        val comunidad: Comunidad =
            Comunidad(
                _id=null,
                nombre = comunidadCreateDTO.nombre,
                descripcion = comunidadCreateDTO.descripcion,
                creador = comunidadCreateDTO.creador,
                intereses = comunidadCreateDTO.intereses,
                actividades = null,
                fotoPerfil = comunidadCreateDTO.fotoPerfil,
                fotoCarrusel = null,
                administradores = null,
                fechaCreacion = Date.from(Instant.now()),
                url=comunidadCreateDTO.url
            )

        comunidadRepository.insert(comunidad)

        return ComunidadDTO(
            comunidadCreateDTO.url,
            comunidadCreateDTO.nombre,
        )
    }

    fun eliminarComunidad(id:String):ComunidadDTO{

        val comunidad=comunidadRepository.findComunidadBy_id(id).orElseThrow{BadRequestException("Esta comunidad no existe")}

        val comunidadDto=ComunidadDTO(
            comunidad.url,
            comunidad.nombre
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

    fun eliminarParticipacionComunidad(id: String): ParticipantesComunidad {
        val participacion = participantesComunidadRepository.findBy_id(id).orElseThrow {
            throw BadRequestException("ID no encontrada")
        }
        participantesComunidadRepository.delete(participacion)

        return participacion
    }

    fun verTodasComunidades(): MutableList<Comunidad> {
        val comunidades=comunidadRepository.findAll()
        return comunidades
    }

    fun modificarComunidad(id: String): Comunidad {
        
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


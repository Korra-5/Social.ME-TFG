package com.example.socialme.controller

import com.example.socialme.dto.ComunidadCreateDTO
import com.example.socialme.dto.ComunidadDTO
import com.example.socialme.model.Comunidad
import com.example.socialme.model.ParticipantesComunidad
import com.example.socialme.service.ComunidadService
import com.example.socialme.service.UsuarioService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Comunidad")
class ComunidadController {

    private lateinit var comunidadService: ComunidadService

    @PostMapping("/crearComunidad")
    fun crearComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadCreateDTO: ComunidadCreateDTO
    ) : ResponseEntity<ComunidadDTO> {
        val comunidad=comunidadService.crearComunidad(comunidadCreateDTO)
        return ResponseEntity(comunidad, HttpStatus.CREATED)
    }

    @PostMapping("/crearComunidad")
    fun unirseComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadCreateDTO: ComunidadCreateDTO
    ) : ResponseEntity<ComunidadDTO> {
        val comunidad=comunidadService.crearComunidad(comunidadCreateDTO)
        return ResponseEntity(comunidad, HttpStatus.CREATED)
    }

    @DeleteMapping("/eliminarComunidad/{id}")
    fun eliminarComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ComunidadDTO> {
        val comunidad=comunidadService.eliminarComunidad(id)
        return ResponseEntity(comunidad, HttpStatus.OK)
    }

    @GetMapping("/verComunidadPorUsuario/{username}")
    fun verComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<List<Comunidad>> {
        val comunidades=comunidadService.getComunidadPorUsername(username)
        return ResponseEntity(comunidades, HttpStatus.OK)
    }

    @DeleteMapping("/eliminarParticipacionComunidad/{id}")
    fun eliminarParticipacionComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ParticipantesComunidad> {
        val participantesComunidad=comunidadService.eliminarParticipacionComunidad(id)
        return ResponseEntity(participantesComunidad, HttpStatus.OK)
    }

    @GetMapping("/verTodasComunidades")
    fun verTodasComunidades(
        httpRequest: HttpServletRequest,
        ): ResponseEntity<MutableList<Comunidad>> {
        return  ResponseEntity(comunidadService.verTodasComunidades(),HttpStatus.OK)
    }

    @PutMapping("/modificarComunidad")
    fun modificarComunidad(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<MutableList<Comunidad>> {
        return  ResponseEntity(comunidadService.modificarComunidad(),HttpStatus.OK)
    }

}
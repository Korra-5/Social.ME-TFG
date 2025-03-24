package com.example.socialme.controller

import com.example.socialme.dto.*
import com.example.socialme.model.Actividad
import com.example.socialme.model.Comunidad
import com.example.socialme.model.ParticipantesComunidad
import com.example.socialme.service.ActividadService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Actividad")
class ActividadController {

    @Autowired
    private lateinit var actividadService: ActividadService

    @PostMapping("/crearActividad")
    fun crearActividad(
        httpRequest: HttpServletRequest,
        @RequestBody actividadCreateDTO: ActividadCreateDTO
    ) : ResponseEntity<ActividadDTO> {
        return ResponseEntity(actividadService.crearActividad(actividadCreateDTO), HttpStatus.CREATED)
    }

    @PostMapping("/unirseActividad")
    fun unirseActividad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesActividadDTO: ParticipantesActividadDTO
    ) : ResponseEntity<ParticipantesActividadDTO> {
        return ResponseEntity(actividadService.unirseActividad(participantesActividadDTO), HttpStatus.CREATED)
    }

    @DeleteMapping("/eliminarActividad/{id}")
    fun eliminarActividad(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ActividadDTO> {

        return ResponseEntity(actividadService.eliminarActividad(id), HttpStatus.OK)
    }

    @DeleteMapping("/salirActividad/{id}")
    fun salirActividad(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ActividadDTO> {

        return ResponseEntity(actividadService.salirActividad(id), HttpStatus.OK)
    }

    @GetMapping("/verActividadPorComunidad/{comunidad}")
    fun verActividadPorComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String
    ) : ResponseEntity<List<Actividad>> {

        return ResponseEntity(actividadService.verActividadPorComunidad(comunidad), HttpStatus.OK)
    }


    @GetMapping("/verActividadesPublicas")
    fun verActividadesDisponibles(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<MutableList<Comunidad>> {
        return  ResponseEntity(actividadService.verActividadesPublicas(), HttpStatus.OK)
    }

    @PutMapping("/modificarComunidad")
    fun modificarActividad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadUpdateDTO: ComunidadUpdateDTO
    ): ResponseEntity<Comunidad> {
        return  ResponseEntity(actividadService.modificarActividad(ActividadUpdateDTO), HttpStatus.OK)
    }

}
}
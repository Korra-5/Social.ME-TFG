package com.example.socialme.controller

import com.example.socialme.dto.*
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
    ) : ResponseEntity<ParticipantesActividadDTO> {

        return ResponseEntity(actividadService.salirActividad(id), HttpStatus.OK)
    }

    @GetMapping("/verActividadNoParticipaUsuario/{username}")
    fun verActividadNoParticipaUsuario(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(actividadService.verActividadNoParticipaUsuario(username), HttpStatus.OK)
    }

    @GetMapping("/verActividadPorId/{id}")
    fun verActividadPorId(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ActividadDTO> {
        return ResponseEntity(actividadService.verActividadPorId(id), HttpStatus.OK)
    }

    @GetMapping("/verActividadPorUsername/{username}")
    fun verActividadPorUsername(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(actividadService.verActividadesPorUsername(username), HttpStatus.OK)
    }


    @GetMapping("/verActividadesPublicasEnZona")
    fun verActividadesPublicasEnZona(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<List<ActividadDTO>>{
        return  ResponseEntity(actividadService.verActividadesPublicas(), HttpStatus.OK)
    }

    @PutMapping("/modificarActividad")
    fun modificarActividad(
        httpRequest: HttpServletRequest,
        @RequestBody actividadUpdateDTO: ActividadUpdateDTO
    ): ResponseEntity<ActividadDTO> {
        return  ResponseEntity(actividadService.modificarActividad(actividadUpdateDTO), HttpStatus.OK)
    }

    @GetMapping("/boooleanUsuarioApuntadoActividad")
    fun boooleanUsuarioApuntadoActividad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesActividadDTO: ParticipantesActividadDTO
    ): ResponseEntity<Boolean> {
        return ResponseEntity(actividadService.booleanUsuarioApuntadoActividad(participantesActividadDTO), HttpStatus.OK)
    }

}

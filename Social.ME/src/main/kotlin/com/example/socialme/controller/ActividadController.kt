package com.example.socialme.controller

import com.example.socialme.dto.*
import com.example.socialme.service.ActividadService
import com.example.socialme.service.UsuarioService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Actividad")
class ActividadController {

    @Autowired
    private lateinit var usuarioService: UsuarioService

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

    @DeleteMapping("/salirActividad")
    fun salirActividad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesActividadDTO: ParticipantesActividadDTO
    ) : ResponseEntity<ParticipantesActividadDTO> {
        return ResponseEntity(actividadService.salirActividad(participantesActividadDTO), HttpStatus.OK)
    }

    @GetMapping("/verActividadNoParticipaUsuarioFechaSuperior/{username}")
    fun verActividadNoParticipaUsuarioFechaSuperior(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(actividadService.verActividadNoParticipaUsuarioFechaSuperior(username), HttpStatus.OK)
    }

    @GetMapping("/verActividadNoParticipaUsuarioCualquierFecha/{username}")
    fun verActividadNoParticipaUsuarioCualquierFecha(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(actividadService.verActividadNoParticipaUsuarioCualquierFecha(username), HttpStatus.OK)
    }

    @GetMapping("/verActividadPorId/{id}")
    fun verActividadPorId(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ActividadDTO> {
        return ResponseEntity(actividadService.verActividadPorId(id), HttpStatus.OK)
    }

    @GetMapping("/verActividadPorUsername/{username}/{usuarioSolicitante}")
    fun verActividadPorUsername(
        httpRequest: HttpServletRequest,
        @PathVariable username: String,
        @PathVariable usuarioSolicitante: String
    ) : ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(usuarioService.verActividadesPorUsername(username, usuarioSolicitante), HttpStatus.OK)
    }

    @GetMapping("/verActividadesPublicasEnZonaFechaSuperior/{username}")
    fun verActividadesPublicasEnZonaFechaSuperior(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<ActividadDTO>>{
        return  ResponseEntity(actividadService.verActividadesPublicasEnZonaFechaSuperior(username), HttpStatus.OK)
    }

    @GetMapping("/verActividadesPublicasEnZonaCualquierFecha/{username}")
    fun verActividadesPublicasEnZonaCualquierFecha(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<ActividadDTO>>{
        return  ResponseEntity(actividadService.verActividadesPublicasEnZonaCualquierFecha(username), HttpStatus.OK)
    }

    @GetMapping("/verTodasActividadesPublicasFechaSuperior")
    fun verTodasActividadesPublicasFechaSuperior(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<List<ActividadDTO>>{
        return  ResponseEntity(actividadService.verTodasActividadesPublicasFechaSuperior(), HttpStatus.OK)
    }

    @GetMapping("/verTodasActividadesPublicasCualquierFecha")
    fun verTodasActividadesPublicasCualquierFecha(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<List<ActividadDTO>>{
        return  ResponseEntity(actividadService.verTodasActividadesPublicasCualquierFecha(), HttpStatus.OK)
    }

    @PutMapping("/modificarActividad")
    fun modificarActividad(
        httpRequest: HttpServletRequest,
        @RequestBody actividadUpdateDTO: ActividadUpdateDTO
    ): ResponseEntity<ActividadDTO> {
        return  ResponseEntity(actividadService.modificarActividad(actividadUpdateDTO), HttpStatus.OK)
    }

    @PostMapping("/booleanUsuarioApuntadoActividad")
    fun booleanUsuarioApuntadoActividad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesActividadDTO: ParticipantesActividadDTO
    ): ResponseEntity<Boolean> {
        return ResponseEntity(actividadService.booleanUsuarioApuntadoActividad(participantesActividadDTO), HttpStatus.OK)
    }

    @GetMapping("/verActividadesPorComunidadFechaSuperior/{comunidad}")
    fun verActividadesPorComunidadFechaSuperior(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String
    ):ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(actividadService.verActividadesPorComunidadFechaSuperior(comunidad), HttpStatus.OK)
    }

    @GetMapping("/verActividadesPorComunidadCualquierFecha/{comunidad}")
    fun verActividadesPorComunidadCualquierFecha(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String
    ):ResponseEntity<List<ActividadDTO>> {
        return ResponseEntity(actividadService.verActividadesPorComunidadCualquierFecha(comunidad), HttpStatus.OK)
    }

    @GetMapping("/contarUsuariosEnUnaActividad/{actividadId}")
    fun contarUsuariosEnUnaActividad(
        httpRequest: HttpServletRequest,
        @PathVariable actividadId: String
    ):ResponseEntity<Int> {
        return ResponseEntity(actividadService.contarUsuariosEnUnaActividad(actividadId), HttpStatus.OK)
    }

    @GetMapping("/verificarCreadorAdministradorActividad/{username}/{idActividad}")
    fun verificarCreadorAdministradorActividad(
        @PathVariable("username") username: String,
        @PathVariable("idActividad") idActividad: String
    ): ResponseEntity<Boolean>{
        return ResponseEntity(actividadService.verificarCreadorAdministradorActividad(username, idActividad), HttpStatus.OK)
    }

    @GetMapping("/verComunidadPorActividad/{idActividad}")
    fun verComunidadPorActividad(
        @PathVariable("idActividad") idActividad: String
    ): ResponseEntity<ComunidadDTO>{
        return ResponseEntity(actividadService.verComunidadPorActividad(idActividad), HttpStatus.OK)
    }
}
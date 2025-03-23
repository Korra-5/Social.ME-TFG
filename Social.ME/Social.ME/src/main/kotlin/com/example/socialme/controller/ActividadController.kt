package com.example.socialme.controller

import com.example.socialme.dto.ComunidadCreateDTO
import com.example.socialme.dto.ComunidadDTO
import com.example.socialme.dto.ComunidadUpdateDTO
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
        @RequestBody comunidadCreateDTO: ComunidadCreateDTO
    ) : ResponseEntity<ComunidadDTO> {

        return ResponseEntity(, HttpStatus.CREATED)
    }

    @PostMapping("/unirseActividad")
    fun unirseActividad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadCreateDTO: ComunidadCreateDTO
    ) : ResponseEntity<ComunidadDTO> {

        return ResponseEntity(, HttpStatus.CREATED)
    }

    @DeleteMapping("/eliminarActividad/{id}")
    fun eliminarActividad(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ComunidadDTO> {

        return ResponseEntity(, HttpStatus.OK)
    }

    @DeleteMapping("/salirActividad/{id}")
    fun salirActividad(
        httpRequest: HttpServletRequest,
        @PathVariable id: String
    ) : ResponseEntity<ComunidadDTO> {

        return ResponseEntity(, HttpStatus.OK)
    }

    @GetMapping("/verActividadPorComunidad/{comunidad}")
    fun verComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String
    ) : ResponseEntity<List<Actividad>> {

        return ResponseEntity(, HttpStatus.OK)
    }


    @GetMapping("/verActividadesPublicas")
    fun verActividadesDisponibles(
        httpRequest: HttpServletRequest,
    ): ResponseEntity<MutableList<Comunidad>> {
        return  ResponseEntity(, HttpStatus.OK)
    }

    @PutMapping("/modificarComunidad")
    fun modificarActividad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadUpdateDTO: ComunidadUpdateDTO
    ): ResponseEntity<Comunidad> {
        return  ResponseEntity(, HttpStatus.OK)
    }

}
}
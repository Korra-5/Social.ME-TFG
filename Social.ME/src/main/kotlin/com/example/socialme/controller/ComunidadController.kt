package com.example.socialme.controller

import com.example.socialme.dto.ComunidadCreateDTO
import com.example.socialme.dto.ComunidadDTO
import com.example.socialme.dto.ComunidadUpdateDTO
import com.example.socialme.dto.ParticipantesComunidadDTO
import com.example.socialme.model.Comunidad
import com.example.socialme.model.ParticipantesComunidad
import com.example.socialme.service.ComunidadService
import com.example.socialme.service.UsuarioService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Comunidad")
class ComunidadController {

    @Autowired
    private lateinit var comunidadService: ComunidadService

    @PostMapping("/crearComunidad")
    fun crearComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadCreateDTO: ComunidadCreateDTO
    ) : ResponseEntity<ComunidadDTO> {
        val comunidad=comunidadService.crearComunidad(comunidadCreateDTO)
        return ResponseEntity(comunidad, HttpStatus.CREATED)
    }

    @PostMapping("/unirseComunidad")
    fun unirseComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesComunidadDTO: ParticipantesComunidadDTO
    ) : ResponseEntity<ParticipantesComunidadDTO> {
        val union=comunidadService.unirseComunidad(participantesComunidadDTO)
        return ResponseEntity(union, HttpStatus.CREATED)
    }

    @PostMapping("/unirseComunidadPorCodigo/{codigo}")
    fun unirseComunidadPorCodigo(
        httpRequest: HttpServletRequest,
        @RequestBody participantesComunidadDTO: ParticipantesComunidadDTO,
        @PathVariable("codigo") codigo: String
    ) : ResponseEntity<ParticipantesComunidadDTO> {
        val union=comunidadService.unirseComunidadPorCodigo(participantesComunidadDTO, codigo)
        return ResponseEntity(union, HttpStatus.CREATED)
    }


    @DeleteMapping("/eliminarComunidad/{url}")
    fun eliminarComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable url: String
    ) : ResponseEntity<ComunidadDTO> {
        val comunidad=comunidadService.eliminarComunidad(url)
        return ResponseEntity(comunidad, HttpStatus.OK)
    }

    @DeleteMapping("/salirComunidad")
    fun salirComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesComunidadDTO: ParticipantesComunidadDTO
        ) : ResponseEntity<ParticipantesComunidadDTO> {
        return ResponseEntity(comunidadService.salirComunidad(participantesComunidadDTO), HttpStatus.OK)
    }

    @GetMapping("/verComunidadPorUsuario/{username}")
    fun verComunidadPorUsuario(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ) : ResponseEntity<List<Comunidad>> {
        val comunidades=comunidadService.getComunidadPorUsername(username)
        return ResponseEntity(comunidades, HttpStatus.OK)
    }


    @GetMapping("/verComunidadesPorUsuarioCreador/{username}")
    fun verComunidadesPorUsuarioCreador(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<ComunidadDTO>> {
        return  ResponseEntity(comunidadService.verComunidadesPorUsuarioCreador(username),HttpStatus.OK)
    }

    @GetMapping("/verTodasComunidadesPublicas")
    fun verTodasComunidadesPublicas(
        httpRequest: HttpServletRequest,
        ): ResponseEntity<List<ComunidadDTO>> {
        return  ResponseEntity(comunidadService.verTodasComunidadesPublicas(),HttpStatus.OK)
    }

    @GetMapping("/verComunidadesPublicasEnZona/{username}")
    fun verComunidadesPublicasEnZona(
        httpRequest: HttpServletRequest,
        @PathVariable username: String
    ): ResponseEntity<List<ComunidadDTO>> {
        return  ResponseEntity(comunidadService.verComunidadesPublicasEnZona(username),HttpStatus.OK)
    }

    @PutMapping("/modificarComunidad")
    fun modificarComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody comunidadUpdateDTO: ComunidadUpdateDTO
    ): ResponseEntity<ComunidadDTO> {
        return  ResponseEntity(comunidadService.modificarComunidad(comunidadUpdateDTO),HttpStatus.OK)
    }

    @GetMapping("/verComunidadPorUrl/{url}")
    fun verComunidadPorUrl(
        httpRequest: HttpServletRequest,
        @PathVariable url: String
    ):ResponseEntity<ComunidadDTO> {
        return ResponseEntity(comunidadService.verComunidadPorUrl(url),HttpStatus.OK)
    }

    @PostMapping("/booleanUsuarioApuntadoComunidad")
    fun booleanUsuarioApuntadoComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesComunidadDTO: ParticipantesComunidadDTO
    ):ResponseEntity<Boolean> {
        return ResponseEntity(comunidadService.booleanUsuarioApuntadoComunidad(participantesComunidadDTO),HttpStatus.OK)
    }

    @GetMapping("/contarUsuariosEnUnaComunidad/{comunidad}")
    fun contarUsuariosEnUnaComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable comunidad: String
    ):ResponseEntity<Int> {
        return ResponseEntity(comunidadService.contarUsuariosEnUnaComunidad(comunidad), HttpStatus.OK)
    }

    @GetMapping("/verificarCreadorAdministradorComunidad/{username}/{comunidadUrl}")
    fun verificarCreadorAdministradorComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable username: String,
        @PathVariable comunidadUrl: String
    ):ResponseEntity<Boolean> {
        return ResponseEntity(comunidadService.verificarCreadorAdministradorComunidad(comunidadUrl, username), HttpStatus.OK)
    }

    @DeleteMapping("/eliminarUsuarioDeComunidad/{usuarioSolicitante}")
    fun eliminarUsuarioDeComunidad(
        httpRequest: HttpServletRequest,
        @RequestBody participantesComunidadDTO: ParticipantesComunidadDTO,
        @PathVariable usuarioSolicitante: String
    ) : ResponseEntity<ParticipantesComunidadDTO> {
        return ResponseEntity(comunidadService.eliminarUsuarioDeComunidad(participantesComunidadDTO, usuarioSolicitante), HttpStatus.OK)
    }
    @PutMapping("/cambiarCreadorComunidad/{comunidadUrl}/{creadorActual}/{nuevoCreador}")
    fun cambiarCreadorComunidad(
        httpRequest: HttpServletRequest,
        @PathVariable comunidadUrl: String,
        @PathVariable creadorActual: String,
        @PathVariable nuevoCreador: String
    ): ResponseEntity<ComunidadDTO> {
        return ResponseEntity(
            comunidadService.cambiarCreadorComunidad(comunidadUrl, creadorActual, nuevoCreador),
            HttpStatus.OK
        )
    }
}


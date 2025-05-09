package com.example.socialme.controller

import com.example.socialme.dto.ComunidadDTO
import com.example.socialme.dto.DenunciaCreateDTO
import com.example.socialme.dto.DenunciaDTO
import com.example.socialme.repository.ActividadesComunidadRepository
import com.example.socialme.service.DenunciaService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/Denuncia")
class DenunciaController {

    @Autowired
    private lateinit var denunciaService: DenunciaService

    @GetMapping("/verDenunciasPuestas/{username}")
    fun verDenunciasPuestas(
        httpRequest: HttpServletRequest,
        @PathVariable("username") username: String
    ): ResponseEntity<List<DenunciaDTO>> {
        return  ResponseEntity(denunciaService.verDenunciasPuestas(username), HttpStatus.OK)
    }

    @GetMapping("/crearDenuncia")
    fun crearDenuncia(
        httpRequest: HttpServletRequest,
        @RequestBody denunciaCreateDTO: DenunciaCreateDTO
    ): ResponseEntity<DenunciaDTO> {
        return  ResponseEntity(denunciaService.crearDenuncia(denunciaCreateDTO),HttpStatus.OK)
    }


}
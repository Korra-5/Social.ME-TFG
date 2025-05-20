package com.example.socialme.service

import com.example.socialme.dto.DenunciaCreateDTO
import com.example.socialme.dto.DenunciaDTO
import com.example.socialme.error.exception.ForbiddenException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Denuncia
import com.example.socialme.repository.DenunciaRepository
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class DenunciaService {

    @Autowired
    private lateinit var denunciaRepository: DenunciaRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    fun crearDenuncia(denunciaCreate: DenunciaCreateDTO): DenunciaDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins también pueden crear denuncias
        if (userActual.roles != "ADMIN" && auth.name != denunciaCreate.usuarioDenunciante) {
            throw ForbiddenException("No tienes permisos para crear una denuncia en nombre de otro usuario")
        }

        val denuncia = Denuncia(
            _id = null,
            motivo = denunciaCreate.motivo,
            cuerpo = denunciaCreate.cuerpo,
            usuarioDenunciante = denunciaCreate.usuarioDenunciante,
            nombreItemDenunciado = denunciaCreate.nombreItemDenunciado,
            tipoItemDenunciado = denunciaCreate.tipoItemDenunciado,
            fechaCreacion = Date.from(Instant.now()),
            solucionado = false
        )
        denunciaRepository.insert(denuncia)
        return DenunciaDTO(
            motivo = denuncia.motivo,
            cuerpo = denuncia.cuerpo,
            nombreItemDenunciado = denuncia.nombreItemDenunciado,
            tipoItemDenunciado = denuncia.tipoItemDenunciado,
            fechaCreacion = denuncia.fechaCreacion,
            solucionado = denuncia.solucionado
        )
    }

    fun verDenunciasPuestas(username: String): List<DenunciaDTO> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden ver las denuncias de cualquier usuario
        if (userActual.roles != "ADMIN" && auth.name != username) {
            throw ForbiddenException("No tienes permisos para ver las denuncias de este usuario")
        }

        // Verificar que el usuario existe
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        // Obtener todas las denuncias
        val todasLasDenuncias = denunciaRepository.findAll()

        // Filtrar las denuncias del usuario específico
        val denunciasDelUsuario = todasLasDenuncias.filter {
            it.usuarioDenunciante == username
        }

        // Mapear las denuncias a DTOs
        return denunciasDelUsuario.map { denuncia ->
            DenunciaDTO(
                motivo = denuncia.motivo,
                cuerpo = denuncia.cuerpo,
                nombreItemDenunciado = denuncia.nombreItemDenunciado,
                tipoItemDenunciado = denuncia.tipoItemDenunciado,
                fechaCreacion = denuncia.fechaCreacion,
                solucionado = denuncia.solucionado
            )
        }
    }

    // Método para ver todas las denuncias (solo accesible para admins)
    fun verTodasLasDenuncias(): List<DenunciaDTO> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Solo los admins pueden ver todas las denuncias
        if (userActual.roles != "ADMIN") {
            throw ForbiddenException("Solo los administradores pueden ver todas las denuncias")
        }

        // Obtener todas las denuncias
        val todasLasDenuncias = denunciaRepository.findAll()

        // Mapear las denuncias a DTOs
        return todasLasDenuncias.map { denuncia ->
            DenunciaDTO(
                motivo = denuncia.motivo,
                cuerpo = denuncia.cuerpo,
                nombreItemDenunciado = denuncia.nombreItemDenunciado,
                tipoItemDenunciado = denuncia.tipoItemDenunciado,
                fechaCreacion = denuncia.fechaCreacion,
                solucionado = denuncia.solucionado
            )
        }
    }

    // Método para ver denuncias no completadas (solo accesible para admins)
    fun verDenunciasNoCompletadas(): List<DenunciaDTO> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Solo los admins pueden ver las denuncias no completadas
        if (userActual.roles != "ADMIN") {
            throw ForbiddenException("Solo los administradores pueden ver las denuncias no completadas")
        }

        // Obtener todas las denuncias
        val todasLasDenuncias = denunciaRepository.findAll()

        // Filtrar las denuncias no completadas (solucionado = false)
        val denunciasNoCompletadas = todasLasDenuncias.filter {
            !it.solucionado
        }

        // Mapear las denuncias a DTOs
        return denunciasNoCompletadas.map { denuncia ->
            DenunciaDTO(
                motivo = denuncia.motivo,
                cuerpo = denuncia.cuerpo,
                nombreItemDenunciado = denuncia.nombreItemDenunciado,
                tipoItemDenunciado = denuncia.tipoItemDenunciado,
                fechaCreacion = denuncia.fechaCreacion,
                solucionado = denuncia.solucionado
            )
        }
    }

    // Método para marcar una denuncia como completada (solo accesible para admins)
    fun completarDenuncia(denunciaId: String, completado: Boolean): DenunciaDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Solo los admins pueden completar denuncias
        if (userActual.roles != "ADMIN") {
            throw ForbiddenException("Solo los administradores pueden marcar denuncias como completadas")
        }

        // Buscar la denuncia por ID
        val denuncia = denunciaRepository.findById(denunciaId).orElseThrow {
            throw NotFoundException("Denuncia con ID $denunciaId no encontrada")
        }

        // Actualizar el estado de la denuncia
        denuncia.solucionado = completado

        // Guardar la denuncia actualizada
        val denunciaActualizada = denunciaRepository.save(denuncia)

        // Mapear la denuncia a DTO
        return DenunciaDTO(
            motivo = denunciaActualizada.motivo,
            cuerpo = denunciaActualizada.cuerpo,
            nombreItemDenunciado = denunciaActualizada.nombreItemDenunciado,
            tipoItemDenunciado = denunciaActualizada.tipoItemDenunciado,
            fechaCreacion = denunciaActualizada.fechaCreacion,
            solucionado = denunciaActualizada.solucionado
        )
    }
}
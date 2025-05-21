package com.example.socialme.service

import com.example.socialme.dto.DenunciaCreateDTO
import com.example.socialme.dto.DenunciaDTO
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Denuncia
import com.example.socialme.repository.DenunciaRepository
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
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

    fun verTodasLasDenuncias(): List<DenunciaDTO> {
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

    fun verDenunciasNoCompletadas(): List<DenunciaDTO> {
        // Obtener todas las denuncias
        val todasLasDenuncias = denunciaRepository.findAll()

        // Filtrar solo las denuncias no completadas (solucionado = false)
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

    fun completarDenuncia(denunciaId: String, completado: Boolean): DenunciaDTO {
        // Buscar la denuncia por ID
        val denuncia = denunciaRepository.findById(denunciaId).orElseThrow {
            throw NotFoundException("Denuncia con ID $denunciaId no encontrada")
        }

        // Actualizar el estado de la denuncia
        denuncia.solucionado = completado

        // Guardar los cambios
        val denunciaActualizada = denunciaRepository.save(denuncia)

        // Retornar el DTO actualizado
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
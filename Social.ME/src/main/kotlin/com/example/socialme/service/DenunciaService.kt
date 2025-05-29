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
            _id = denuncia._id,
            motivo = denuncia.motivo,
            cuerpo = denuncia.cuerpo,
            nombreItemDenunciado = denuncia.nombreItemDenunciado,
            tipoItemDenunciado = denuncia.tipoItemDenunciado,
            fechaCreacion = denuncia.fechaCreacion,
            solucionado = denuncia.solucionado,
            usuarioDenunciante = denuncia.usuarioDenunciante
        )
    }

    fun verDenunciasPuestas(username: String): List<DenunciaDTO> {
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario $username no encontrado")
        }

        val todasLasDenuncias = denunciaRepository.findAll()
        val denunciasDelUsuario = todasLasDenuncias.filter {
            it.usuarioDenunciante == username
        }

        return denunciasDelUsuario.map { denuncia ->
            DenunciaDTO(
                _id = denuncia._id,
                motivo = denuncia.motivo,
                cuerpo = denuncia.cuerpo,
                nombreItemDenunciado = denuncia.nombreItemDenunciado,
                tipoItemDenunciado = denuncia.tipoItemDenunciado,
                fechaCreacion = denuncia.fechaCreacion,
                solucionado = denuncia.solucionado,
                usuarioDenunciante = denuncia.usuarioDenunciante
            )
        }
    }

    fun verTodasLasDenuncias(): List<DenunciaDTO> {
        val todasLasDenuncias = denunciaRepository.findAll()

        return todasLasDenuncias.map { denuncia ->
            DenunciaDTO(
                _id = denuncia._id,
                motivo = denuncia.motivo,
                cuerpo = denuncia.cuerpo,
                nombreItemDenunciado = denuncia.nombreItemDenunciado,
                tipoItemDenunciado = denuncia.tipoItemDenunciado,
                fechaCreacion = denuncia.fechaCreacion,
                solucionado = denuncia.solucionado,
                usuarioDenunciante = denuncia.usuarioDenunciante
            )
        }
    }

    fun verDenunciasNoCompletadas(): List<DenunciaDTO> {
        val todasLasDenuncias = denunciaRepository.findAll()
        val denunciasNoCompletadas = todasLasDenuncias.filter {
            !it.solucionado
        }

        return denunciasNoCompletadas.map { denuncia ->
            DenunciaDTO(
                _id = denuncia._id,
                motivo = denuncia.motivo,
                cuerpo = denuncia.cuerpo,
                nombreItemDenunciado = denuncia.nombreItemDenunciado,
                tipoItemDenunciado = denuncia.tipoItemDenunciado,
                fechaCreacion = denuncia.fechaCreacion,
                solucionado = denuncia.solucionado,
                usuarioDenunciante = denuncia.usuarioDenunciante
            )
        }
    }

    fun completarDenuncia(denunciaId: String, completado: Boolean): DenunciaDTO {
        val denuncia = denunciaRepository.findById(denunciaId).orElseThrow {
            throw NotFoundException("Denuncia con ID $denunciaId no encontrada")
        }

        denuncia.solucionado = completado
        val denunciaActualizada = denunciaRepository.save(denuncia)

        return DenunciaDTO(
            _id = denunciaActualizada._id,
            motivo = denunciaActualizada.motivo,
            cuerpo = denunciaActualizada.cuerpo,
            nombreItemDenunciado = denunciaActualizada.nombreItemDenunciado,
            tipoItemDenunciado = denunciaActualizada.tipoItemDenunciado,
            fechaCreacion = denunciaActualizada.fechaCreacion,
            solucionado = denunciaActualizada.solucionado,
            usuarioDenunciante = denunciaActualizada.usuarioDenunciante
        )
    }
}